package my.id.rakyzumusic.core.data.auth

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.exception.AuthWeakPasswordException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.HttpRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal class SupabaseAuthRepository(
    private val auth: Auth,
    applicationScope: CoroutineScope,
) : AuthRepository {
    override val sessionState: StateFlow<AuthSessionState> = auth.sessionStatus
        .map(::toDomainSessionState)
        .stateIn(
            scope = applicationScope,
            started = SharingStarted.Eagerly,
            initialValue = AuthSessionState.Initializing,
        )

    override suspend fun signIn(email: String, password: String): AuthActionResult = authRequest {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        AuthActionResult.Success
    }

    override suspend fun signUp(email: String, password: String): AuthActionResult = authRequest {
        auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        if (auth.currentSessionOrNull() == null) {
            AuthActionResult.ConfirmationRequired(email)
        } else {
            AuthActionResult.Success
        }
    }

    override suspend fun signOut(): AuthActionResult = authRequest {
        auth.signOut(SignOutScope.LOCAL)
        AuthActionResult.Success
    }

    private suspend fun authRequest(block: suspend () -> AuthActionResult): AuthActionResult = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        AuthActionResult.Failure(error.toAuthFailure())
    }
}

private fun toDomainSessionState(status: SessionStatus): AuthSessionState = when (status) {
    SessionStatus.Initializing -> AuthSessionState.Initializing
    is SessionStatus.NotAuthenticated -> AuthSessionState.SignedOut
    is SessionStatus.Authenticated -> AuthSessionState.SignedIn(
        userId = status.session.user?.id.orEmpty(),
        email = status.session.user?.email,
    )
    is SessionStatus.RefreshFailure -> AuthSessionState.RecoveryRequired(AuthFailure.SessionExpired)
}

internal fun Throwable.toAuthFailure(): AuthFailure = when (this) {
    is AuthWeakPasswordException -> AuthFailure.WeakPassword
    is HttpRequestTimeoutException,
    is HttpRequestException,
    -> AuthFailure.NetworkUnavailable
    is AuthRestException -> when (errorCode) {
        AuthErrorCode.InvalidCredentials,
        AuthErrorCode.UserNotFound,
        -> AuthFailure.InvalidCredentials
        AuthErrorCode.EmailNotConfirmed -> AuthFailure.EmailNotConfirmed
        AuthErrorCode.EmailExists,
        AuthErrorCode.UserAlreadyExists,
        -> AuthFailure.EmailAlreadyRegistered
        AuthErrorCode.WeakPassword -> AuthFailure.WeakPassword
        AuthErrorCode.OverRequestRateLimit,
        AuthErrorCode.OverEmailSendRateLimit,
        -> AuthFailure.RateLimited
        AuthErrorCode.SessionExpired,
        AuthErrorCode.SessionNotFound,
        AuthErrorCode.RefreshTokenNotFound,
        AuthErrorCode.RefreshTokenAlreadyUsed,
        -> AuthFailure.SessionExpired
        AuthErrorCode.UnexpectedFailure,
        AuthErrorCode.HookTimeout,
        AuthErrorCode.HookTimeoutAfterRetry,
        AuthErrorCode.RequestTimeout,
        -> AuthFailure.ServiceUnavailable
        else -> AuthFailure.Unexpected
    }
    else -> AuthFailure.Unexpected
}
