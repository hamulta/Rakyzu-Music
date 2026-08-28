package my.id.rakyzumusic.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import my.id.rakyzumusic.core.data.auth.AuthActionResult
import my.id.rakyzumusic.core.data.auth.AuthCredentials
import my.id.rakyzumusic.core.data.auth.AuthFailure
import my.id.rakyzumusic.core.data.auth.AuthRepository
import my.id.rakyzumusic.core.data.auth.CredentialValidation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode {
    SignIn,
    SignUp,
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

    fun submit() {
        val state = mutableUiState.value
        if (state.isSubmitting) return

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
                    message = "Check your email to confirm your Rakyzu Music account, then sign in.",
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
    AuthFailure.EmailAlreadyRegistered -> "Unable to create this account. Try signing in instead."
    AuthFailure.WeakPassword -> "Choose a stronger password and try again."
    AuthFailure.RateLimited -> "Too many attempts. Wait a moment and try again."
    AuthFailure.NetworkUnavailable -> "No connection. Check your network and try again."
    AuthFailure.ServiceUnavailable -> "Rakyzu Music sign-in is temporarily unavailable."
    AuthFailure.SessionExpired -> "Your session expired. Sign in again to continue."
    AuthFailure.Unexpected -> "Something went wrong. Try again."
}
