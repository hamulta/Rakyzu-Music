package my.id.rakyzumusic.core.data.auth

import android.content.Context
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import java.net.URI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import my.id.rakyzumusic.core.data.profile.ProfileFailure
import my.id.rakyzumusic.core.data.profile.ProfileRepository
import my.id.rakyzumusic.core.data.profile.ProfileResult
import my.id.rakyzumusic.core.data.profile.SupabaseProfileRepository

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
        return createRepositories(context, configuration, applicationScope).authRepository
    }

    fun createRepositories(
        context: Context,
        configuration: SupabasePublicConfiguration,
        applicationScope: CoroutineScope,
    ): RakyzuRepositories {
        if (!configuration.isValid()) return RakyzuRepositories(
            authRepository = UnavailableAuthRepository,
            profileRepository = UnavailableProfileRepository,
        )

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
            install(Postgrest) {
                requireValidSession = true
            }
        }
        return RakyzuRepositories(
            authRepository = SupabaseAuthRepository(
                auth = client.auth,
                recoveryState = PasswordRecoveryState(encryptedStore),
                applicationScope = applicationScope,
            ),
            profileRepository = SupabaseProfileRepository(client.auth, client.postgrest),
        )
    }
}

data class RakyzuRepositories(
    val authRepository: AuthRepository,
    val profileRepository: ProfileRepository,
)

private data object UnavailableAuthRepository : AuthRepository {
    override val sessionState: StateFlow<AuthSessionState> = MutableStateFlow(
        AuthSessionState.RecoveryRequired(AuthFailure.InvalidConfiguration),
    )

    override suspend fun signIn(email: String, password: String): AuthActionResult = unavailable()

    override suspend fun signUp(email: String, password: String): AuthActionResult = unavailable()

    override suspend fun requestPasswordReset(email: String): AuthActionResult = unavailable()

    override suspend fun updatePassword(password: String): AuthActionResult = unavailable()

    override suspend fun signOut(): AuthActionResult = unavailable()

    override fun markPasswordRecoveryCallback() = Unit

    private fun unavailable() = AuthActionResult.Failure(AuthFailure.InvalidConfiguration)
}

private data object UnavailableProfileRepository : ProfileRepository {
    override suspend fun getProfile(): ProfileResult = unavailable()

    override suspend fun updateProfile(
        displayName: String,
        completeOnboarding: Boolean,
    ): ProfileResult = unavailable()

    private fun unavailable() = ProfileResult.Failure(ProfileFailure.ServiceUnavailable)
}
