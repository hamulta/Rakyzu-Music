package my.id.rakyzumusic.core.data.auth

private val emailPattern = Regex(
    pattern = "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
    option = RegexOption.IGNORE_CASE,
)

data class AuthCredentials(
    val email: String,
    val password: String,
) {
    fun normalized(): AuthCredentials = copy(email = email.trim().lowercase())

    fun validate(): CredentialValidation {
        val normalized = normalized()
        return when {
            !emailPattern.matches(normalized.email) -> CredentialValidation.InvalidEmail
            normalized.password.length < MINIMUM_PASSWORD_LENGTH -> CredentialValidation.WeakPassword
            else -> CredentialValidation.Valid(normalized)
        }
    }

    companion object {
        const val MINIMUM_PASSWORD_LENGTH = 8
    }
}

sealed interface CredentialValidation {
    data class Valid(val credentials: AuthCredentials) : CredentialValidation

    data object InvalidEmail : CredentialValidation

    data object WeakPassword : CredentialValidation
}
