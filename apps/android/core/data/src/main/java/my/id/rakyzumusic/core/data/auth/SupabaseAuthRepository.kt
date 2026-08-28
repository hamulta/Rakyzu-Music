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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

internal class SupabaseAuthRepository(
    private val auth: Auth,
    private val recoveryState: PasswordRecoveryState,
    applicationScope: CoroutineScope,
) : AuthRepository {
    override val sessionState: StateFlow<AuthSessionState> = combine(
        auth.sessionStatus,
        recoveryState.isRecoveryRequired,
        ::toDomainSessionState,
    )
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

    override suspend fun requestPasswordReset(email: String): AuthActionResult = authRequest {
        auth.resetPasswordForEmail(
            email = email,
            redirectUrl = PASSWORD_RECOVERY_REDIRECT,
        )
        AuthActionResult.RecoveryEmailSent(email)
    }

    override suspend fun updatePassword(password: String): AuthActionResult = authRequest {
        auth.updateUser {
            this.password = password
        }
        recoveryState.complete()
        AuthActionResult.Success
    }

    override suspend fun signOut(): AuthActionResult = authRequest {
        auth.signOut(SignOutScope.LOCAL)
        recoveryState.complete()
        AuthActionResult.Success
    }

    override fun markPasswordRecoveryCallback() {
        recoveryState.requireRecovery()
    }

    private suspend fun authRequest(block: suspend () -> AuthActionResult): AuthActionResult = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        AuthActionResult.Failure(error.toAuthFailure())
    }

    private companion object {
        const val PASSWORD_RECOVERY_REDIRECT = "my.id.rakyzumusic://auth/recovery"
    }
}

private fun toDomainSessionState(
    status: SessionStatus,
    isPasswordRecoveryRequired: Boolean,
): AuthSessionState = when (status) {
    SessionStatus.Initializing -> AuthSessionState.Initializing
    is SessionStatus.NotAuthenticated -> AuthSessionState.SignedOut
    is SessionStatus.Authenticated -> if (isPasswordRecoveryRequired) {
        AuthSessionState.PasswordRecovery(
            userId = status.session.user?.id.orEmpty(),
            email = status.session.user?.email,
        )
    } else {
        AuthSessionState.SignedIn(
            userId = status.session.user?.id.orEmpty(),
            email = status.session.user?.email,
        )
    }
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
