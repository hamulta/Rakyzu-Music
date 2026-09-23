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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class SupabaseAuthRepository(
    private val auth: Auth,
    private val recoveryState: PasswordRecoveryState,
    private val applicationScope: CoroutineScope,
) : AuthRepository {
    private val callbackState = MutableStateFlow<AuthCallbackState>(AuthCallbackState.Idle)
    override val sessionState: StateFlow<AuthSessionState> = combine(
        auth.sessionStatus,
        recoveryState.isRecoveryRequired,
        callbackState,
        ::toDomainSessionState,
    )
        .stateIn(
            scope = applicationScope,
            started = SharingStarted.Eagerly,
            initialValue = AuthSessionState.Initializing,
        )

    override suspend fun signIn(email: String, password: String): AuthActionResult = authRequest {
        callbackState.value = AuthCallbackState.Idle
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        AuthActionResult.Success
    }

    override suspend fun signUp(email: String, password: String): AuthActionResult = authRequest {
        callbackState.value = AuthCallbackState.Idle
        val user = auth.signUpWith(Email, redirectUrl = EMAIL_CONFIRMATION_REDIRECT) {
            this.email = email
            this.password = password
        }
        if (auth.currentSessionOrNull() == null) {
            if (user != null && user.identities.isNullOrEmpty()) {
                AuthActionResult.Failure(AuthFailure.EmailAlreadyRegistered)
            } else {
                AuthActionResult.ConfirmationRequired(email)
            }
        } else {
            AuthActionResult.Success
        }
    }

    override suspend fun requestPasswordReset(email: String): AuthActionResult = authRequest {
        callbackState.value = AuthCallbackState.Idle
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

    override fun handleAuthCallback(callback: AuthCallback) {
        if (callbackState.value == AuthCallbackState.Processing) return
        callbackState.value = AuthCallbackState.Processing
        applicationScope.launch {
            try {
                when (callback) {
                    is AuthCallback.AuthorizationCode ->
                        auth.exchangeCodeForSession(callback.code)
                    is AuthCallback.ImplicitSession -> auth.importAuthToken(
                        callback.accessToken,
                        callback.refreshToken,
                        true,
                        true,
                    )
                }
                if (callback.purpose == AuthCallbackPurpose.PasswordRecovery) {
                    recoveryState.requireRecovery()
                }
                callbackState.value = AuthCallbackState.Idle
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                callbackState.value = AuthCallbackState.Failed(
                    error.toAuthFailure().asCallbackFailure(),
                )
            }
        }
    }

    private suspend fun authRequest(block: suspend () -> AuthActionResult): AuthActionResult = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        AuthActionResult.Failure(error.toAuthFailure())
    }

    private companion object {
        const val EMAIL_CONFIRMATION_REDIRECT = "my.id.rakyzumusic://auth"
        const val PASSWORD_RECOVERY_REDIRECT = "my.id.rakyzumusic://auth/recovery"
    }
}

private fun toDomainSessionState(
    status: SessionStatus,
    isPasswordRecoveryRequired: Boolean,
    callbackState: AuthCallbackState,
): AuthSessionState = when (callbackState) {
    AuthCallbackState.Processing -> AuthSessionState.Initializing
    is AuthCallbackState.Failed -> AuthSessionState.RecoveryRequired(callbackState.failure)
    AuthCallbackState.Idle -> status.toDomainSessionState(isPasswordRecoveryRequired)
}

private fun SessionStatus.toDomainSessionState(
    isPasswordRecoveryRequired: Boolean,
): AuthSessionState = when (this) {
    SessionStatus.Initializing -> AuthSessionState.Initializing
    is SessionStatus.NotAuthenticated -> AuthSessionState.SignedOut
    is SessionStatus.Authenticated -> if (isPasswordRecoveryRequired) {
        AuthSessionState.PasswordRecovery(
            userId = session.user?.id.orEmpty(),
            email = session.user?.email,
        )
    } else {
        AuthSessionState.SignedIn(
            userId = session.user?.id.orEmpty(),
            email = session.user?.email,
        )
    }
    is SessionStatus.RefreshFailure -> AuthSessionState.RecoveryRequired(AuthFailure.SessionExpired)
}

private sealed interface AuthCallbackState {
    data object Idle : AuthCallbackState
    data object Processing : AuthCallbackState
    data class Failed(val failure: AuthFailure) : AuthCallbackState
}

private fun AuthFailure.asCallbackFailure(): AuthFailure = when (this) {
    AuthFailure.NetworkUnavailable,
    AuthFailure.ServiceUnavailable,
    AuthFailure.RateLimited,
    -> this
    else -> AuthFailure.SessionExpired
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
