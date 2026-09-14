package my.id.rakyzumusic

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import my.id.rakyzumusic.core.playback.PlaybackPreferences
import my.id.rakyzumusic.core.playback.PlaybackQuality

class AndroidPlaybackPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
    private val mutableState = MutableStateFlow(readPreferences())

    val state: StateFlow<PlaybackPreferences> = mutableState.asStateFlow()

    fun effectiveQuality(): PlaybackQuality = state.value.effectiveQuality(
        isMetered = connectivityManager.isActiveNetworkMetered,
    )

    fun setWifiQuality(quality: PlaybackQuality) = update(wifiQuality = quality)

    fun setMobileQuality(quality: PlaybackQuality) = update(mobileQuality = quality)

    fun setDataSaverEnabled(enabled: Boolean) = update(dataSaverEnabled = enabled)

    private fun update(
        wifiQuality: PlaybackQuality = state.value.wifiQuality,
        mobileQuality: PlaybackQuality = state.value.mobileQuality,
        dataSaverEnabled: Boolean = state.value.dataSaverEnabled,
    ) {
        val updated = PlaybackPreferences(wifiQuality, mobileQuality, dataSaverEnabled)
        preferences.edit()
            .putString(WIFI_QUALITY, updated.wifiQuality.name)
            .putString(MOBILE_QUALITY, updated.mobileQuality.name)
            .putBoolean(DATA_SAVER, updated.dataSaverEnabled)
            .apply()
        mutableState.value = updated
    }

    private fun readPreferences(): PlaybackPreferences = PlaybackPreferences(
        wifiQuality = preferences.quality(WIFI_QUALITY),
        mobileQuality = preferences.quality(MOBILE_QUALITY),
        dataSaverEnabled = preferences.getBoolean(DATA_SAVER, false),
    )

    private fun android.content.SharedPreferences.quality(key: String): PlaybackQuality =
        getString(key, null)
            ?.let { stored -> PlaybackQuality.entries.firstOrNull { it.name == stored } }
            ?: PlaybackQuality.Standard

    private companion object {
        const val STORE_NAME = "rakyzu_playback_preferences"
        const val WIFI_QUALITY = "wifi_quality"
        const val MOBILE_QUALITY = "mobile_quality"
        const val DATA_SAVER = "data_saver"
    }
}
