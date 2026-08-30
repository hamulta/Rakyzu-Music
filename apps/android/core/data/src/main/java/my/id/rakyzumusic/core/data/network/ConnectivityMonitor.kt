package my.id.rakyzumusic.core.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

interface ConnectivityMonitor {
    val isOnline: Flow<Boolean>

    fun isCurrentlyOnline(): Boolean
}

fun createConnectivityMonitor(context: Context): ConnectivityMonitor =
    AndroidConnectivityMonitor(
        connectivityManager = context.applicationContext.getSystemService(
            ConnectivityManager::class.java,
        ),
    )

private class AndroidConnectivityMonitor(
    private val connectivityManager: ConnectivityManager,
) : ConnectivityMonitor {
    override val isOnline: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(isCurrentlyOnline())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                trySend(networkCapabilities.isValidatedInternet())
            }

            override fun onLost(network: Network) {
                trySend(isCurrentlyOnline())
            }

            override fun onUnavailable() {
                trySend(false)
            }
        }

        trySend(isCurrentlyOnline())
        val callbackRegistered = runCatching {
            connectivityManager.registerDefaultNetworkCallback(callback)
        }.isSuccess
        awaitClose {
            if (callbackRegistered) {
                runCatching { connectivityManager.unregisterNetworkCallback(callback) }
            }
        }
    }.distinctUntilChanged().conflate()

    override fun isCurrentlyOnline(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        return connectivityManager.getNetworkCapabilities(activeNetwork)
            ?.isValidatedInternet() == true
    }
}

private fun NetworkCapabilities.isValidatedInternet(): Boolean =
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
