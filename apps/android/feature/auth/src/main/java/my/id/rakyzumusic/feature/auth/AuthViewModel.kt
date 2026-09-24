package my.id.rakyzumusic.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import my.id.rakyzumusic.core.data.auth.AuthActionResult
import my.id.rakyzumusic.core.data.auth.AuthCredentials
import my.id.rakyzumusic.core.data.auth.AuthFailure
import my.id.rakyzumusic.core.data.auth.AuthEmail
import my.id.rakyzumusic.core.data.auth.AuthPassword
import my.id.rakyzumusic.core.data.auth.AuthRepository
import my.id.rakyzumusic.core.data.auth.CredentialValidation
import my.id.rakyzumusic.core.data.auth.EmailValidation
import my.id.rakyzumusic.core.data.auth.PasswordValidation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode {
    SignIn,
    SignUp,
    ForgotPassword,
    ResetPassword,
}

data class AuthUiState(
    val mode: AuthMode = AuthMode.SignIn,
    val email: String = "",
    val password: String = "",
    val passwordConfirmation: String = "",
    val isPasswordVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
)

class AuthViewModel(
    private val repository: AuthRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = mutableUiState.asStateFlow()

    fun updateEmail(email: String) {
        mutableUiState.update { it.copy(email = email.take(MAXIMUM_EMAIL_LENGTH), message = null) }
    }

    fun updatePassword(password: String) {
        mutableUiState.update { it.copy(password = password.take(MAXIMUM_PASSWORD_LENGTH), message = null) }
    }

    fun updatePasswordConfirmation(password: String) {
        mutableUiState.update {
            it.copy(passwordConfirmation = password.take(MAXIMUM_PASSWORD_LENGTH), message = null)
        }
    }

    fun togglePasswordVisibility() {
        mutableUiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun switchMode() {
        mutableUiState.update {
            it.copy(
                mode = if (it.mode == AuthMode.SignIn) AuthMode.SignUp else AuthMode.SignIn,
                password = "",
                passwordConfirmation = "",
                isPasswordVisible = false,
                message = null,
                messageIsError = false,
            )
        }
    }

    fun showForgotPassword() {
        mutableUiState.update {
            it.copy(
                mode = AuthMode.ForgotPassword,
                password = "",
                passwordConfirmation = "",
                isPasswordVisible = false,
                message = null,
                messageIsError = false,
            )
        }
    }

    fun showPasswordReset() {
        mutableUiState.update {
            if (it.mode == AuthMode.ResetPassword) it else it.copy(
                mode = AuthMode.ResetPassword,
                email = "",
                password = "",
                passwordConfirmation = "",
                isPasswordVisible = false,
                message = null,
                messageIsError = false,
            )
        }
    }

    fun cancelRecovery() {
        mutableUiState.update {
            it.copy(
                mode = AuthMode.SignIn,
                password = "",
                passwordConfirmation = "",
                isPasswordVisible = false,
                message = null,
                messageIsError = false,
            )
        }
    }

    fun submit() {
        val state = mutableUiState.value
        if (state.isSubmitting) return

        when (state.mode) {
            AuthMode.ForgotPassword -> requestPasswordReset(state.email)
            AuthMode.ResetPassword -> updatePassword(
                password = state.password,
                confirmation = state.passwordConfirmation,
            )
            AuthMode.SignIn,
            AuthMode.SignUp,
            -> submitCredentials(state)
        }
    }

    private fun submitCredentials(state: AuthUiState) {

        val validation = AuthCredentials(state.email, state.password).validate()
        if (validation !is CredentialValidation.Valid) {
            showValidationError(validation)
            return
        }
        if (state.mode == AuthMode.SignUp && state.password != state.passwordConfirmation) {
            showError("Passwords do not match.")
            return
        }

        mutableUiState.update { it.copy(isSubmitting = true, message = null) }
        viewModelScope.launch {
            val credentials = validation.credentials
            val result = if (state.mode == AuthMode.SignIn) {
                repository.signIn(credentials.email, credentials.password)
            } else {
                repository.signUp(credentials.email, credentials.password)
            }
            handleResult(result)
        }
    }

    private fun requestPasswordReset(email: String) {
        val validation = AuthEmail(email).validate()
        if (validation !is EmailValidation.Valid) {
            showError("Enter a valid email address.")
            return
        }

        mutableUiState.update { it.copy(isSubmitting = true, message = null) }
        viewModelScope.launch {
            handleResult(repository.requestPasswordReset(validation.email))
        }
    }

    private fun updatePassword(password: String, confirmation: String) {
        val validation = AuthPassword(password).validate()
        if (validation !is PasswordValidation.Valid) {
            showError(
                "Use at least ${AuthCredentials.MINIMUM_PASSWORD_LENGTH} characters with " +
                    "uppercase, lowercase, and a number.",
            )
            return
        }
        if (password != confirmation) {
            showError("Passwords do not match.")
            return
        }

        mutableUiState.update { it.copy(isSubmitting = true, message = null) }
        viewModelScope.launch {
            handleResult(repository.updatePassword(validation.password))
        }
    }

    private fun showValidationError(validation: CredentialValidation) {
        val message = when (validation) {
            CredentialValidation.InvalidEmail -> "Enter a valid email address."
            CredentialValidation.WeakPassword -> "Use at least " +
                AuthCredentials.MINIMUM_PASSWORD_LENGTH +
                " characters with uppercase, lowercase, and a number."
            is CredentialValidation.Valid -> return
        }
        showError(message)
    }

    private fun handleResult(result: AuthActionResult) {
        when (result) {
            AuthActionResult.Success -> mutableUiState.update {
                it.copy(
                    password = "",
                    passwordConfirmation = "",
                    isSubmitting = false,
                    message = null,
                )
            }
            is AuthActionResult.ConfirmationRequired -> mutableUiState.update {
                it.copy(
                    mode = AuthMode.SignIn,
                    email = result.email,
                    password = "",
                    passwordConfirmation = "",
                    isSubmitting = false,
                    message = "If this is a new account, check your email to confirm it, then sign in.",
                    messageIsError = false,
                )
            }
            is AuthActionResult.RecoveryEmailSent -> mutableUiState.update {
                it.copy(
                    mode = AuthMode.SignIn,
                    email = result.email,
                    password = "",
                    passwordConfirmation = "",
                    isSubmitting = false,
                    message = "If an account exists for this email, a password reset link is on its way.",
                    messageIsError = false,
                )
            }
            is AuthActionResult.Failure -> showError(result.reason.toSafeMessage())
        }
    }

    private fun showError(message: String) {
        mutableUiState.update {
            it.copy(
                isSubmitting = false,
                message = message,
                messageIsError = true,
            )
        }
    }

    companion object {
        private const val MAXIMUM_EMAIL_LENGTH = 254
        private const val MAXIMUM_PASSWORD_LENGTH = 128

        fun factory(repository: AuthRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(AuthViewModel::class.java))
                    return AuthViewModel(repository) as T
                }
            }
    }
}

private fun AuthFailure.toSafeMessage(): String = when (this) {
    AuthFailure.InvalidConfiguration -> "Rakyzu Music sign-in is not configured for this build."
    AuthFailure.InvalidCredentials -> "The email or password is incorrect."
    AuthFailure.EmailNotConfirmed -> "Confirm your email before signing in."
    AuthFailure.EmailAlreadyRegistered ->
        "This email cannot be registered. Sign in or reset its password instead."
    AuthFailure.WeakPassword -> "Choose a stronger password and try again."
    AuthFailure.RateLimited -> "Too many attempts. Wait a moment and try again."
    AuthFailure.NetworkUnavailable -> "No connection. Check your network and try again."
    AuthFailure.ServiceUnavailable -> "Rakyzu Music sign-in is temporarily unavailable."
    AuthFailure.SessionExpired -> "Your session expired. Sign in again to continue."
    AuthFailure.Unexpected -> "Something went wrong. Try again."
}
