package my.id.rakyzumusic.core.data.auth

import android.content.Context
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import java.net.URI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json

data class SupabasePublicConfiguration(
    val url: String,
    val publishableKey: String,
) {
    fun isValid(): Boolean {
        val uri = runCatching { URI(url) }.getOrNull()
        return uri?.scheme == "https" &&
            !uri.host.isNullOrBlank() &&
            publishableKey.isNotBlank()
    }
}

object RakyzuAuthFactory {
    fun create(
        context: Context,
        configuration: SupabasePublicConfiguration,
        applicationScope: CoroutineScope,
    ): AuthRepository {
        if (!configuration.isValid()) return UnavailableAuthRepository

        val encryptedStore = EncryptedAuthStore(context)
        val sessionJson = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
        val client = createSupabaseClient(
            supabaseUrl = configuration.url,
            supabaseKey = configuration.publishableKey,
        ) {
            install(Auth) {
                flowType = FlowType.PKCE
                scheme = "my.id.rakyzumusic"
                host = "auth"
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
                autoSaveToStorage = true
                sessionManager = EncryptedSessionManager(encryptedStore, sessionJson)
                codeVerifierCache = EncryptedCodeVerifierCache(encryptedStore)
            }
        }
        return SupabaseAuthRepository(client.auth, applicationScope)
    }
}

private data object UnavailableAuthRepository : AuthRepository {
    override val sessionState: StateFlow<AuthSessionState> = MutableStateFlow(
        AuthSessionState.RecoveryRequired(AuthFailure.InvalidConfiguration),
    )

    override suspend fun signIn(email: String, password: String): AuthActionResult = unavailable()

    override suspend fun signUp(email: String, password: String): AuthActionResult = unavailable()

    override suspend fun signOut(): AuthActionResult = unavailable()

    private fun unavailable() = AuthActionResult.Failure(AuthFailure.InvalidConfiguration)
}
