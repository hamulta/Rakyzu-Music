package my.id.rakyzumusic.core.data.auth

private val emailPattern = Regex(
    pattern = "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
    option = RegexOption.IGNORE_CASE,
)

data class AuthEmail(val value: String) {
    fun normalized(): AuthEmail = copy(value = value.trim().lowercase())

    fun validate(): EmailValidation {
        val normalized = normalized()
        return if (emailPattern.matches(normalized.value)) {
            EmailValidation.Valid(normalized.value)
        } else {
            EmailValidation.Invalid
        }
    }
}

sealed interface EmailValidation {
    data class Valid(val email: String) : EmailValidation

    data object Invalid : EmailValidation
}

data class AuthPassword(val value: String) {
    fun validate(): PasswordValidation = if (
        value.length >= AuthCredentials.MINIMUM_PASSWORD_LENGTH &&
        value.hasRequiredCharacterGroups()
    ) {
        PasswordValidation.Valid(value)
    } else {
        PasswordValidation.Weak
    }
}

sealed interface PasswordValidation {
    data class Valid(val password: String) : PasswordValidation

    data object Weak : PasswordValidation
}

data class AuthCredentials(
    val email: String,
    val password: String,
) {
    fun normalized(): AuthCredentials = copy(email = email.trim().lowercase())

    fun validate(): CredentialValidation {
        val normalized = normalized()
        return when {
            AuthEmail(normalized.email).validate() !is EmailValidation.Valid ->
                CredentialValidation.InvalidEmail
            AuthPassword(normalized.password).validate() !is PasswordValidation.Valid ->
                CredentialValidation.WeakPassword
            else -> CredentialValidation.Valid(normalized)
        }
    }

    companion object {
        const val MINIMUM_PASSWORD_LENGTH = 8
    }
}

private fun String.hasRequiredCharacterGroups(): Boolean =
    any(Char::isLowerCase) && any(Char::isUpperCase) && any(Char::isDigit)

sealed interface CredentialValidation {
    data class Valid(val credentials: AuthCredentials) : CredentialValidation

    data object InvalidEmail : CredentialValidation

    data object WeakPassword : CredentialValidation
}
