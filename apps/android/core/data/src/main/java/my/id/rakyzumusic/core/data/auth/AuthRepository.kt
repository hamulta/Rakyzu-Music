package my.id.rakyzumusic.core.data.auth

import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val sessionState: StateFlow<AuthSessionState>

    suspend fun signIn(email: String, password: String): AuthActionResult

    suspend fun signUp(email: String, password: String): AuthActionResult

    suspend fun signOut(): AuthActionResult
}

sealed interface AuthSessionState {
    data object Initializing : AuthSessionState

    data object SignedOut : AuthSessionState

    data class SignedIn(
        val userId: String,
        val email: String?,
    ) : AuthSessionState

    data class RecoveryRequired(val failure: AuthFailure) : AuthSessionState
}

sealed interface AuthActionResult {
    data object Success : AuthActionResult

    data class ConfirmationRequired(val email: String) : AuthActionResult

    data class Failure(val reason: AuthFailure) : AuthActionResult
}

enum class AuthFailure {
    InvalidConfiguration,
    InvalidCredentials,
    EmailNotConfirmed,
    EmailAlreadyRegistered,
    WeakPassword,
    RateLimited,
    NetworkUnavailable,
    ServiceUnavailable,
    SessionExpired,
    Unexpected,
}
