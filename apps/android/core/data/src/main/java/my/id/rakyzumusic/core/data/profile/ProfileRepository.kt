package my.id.rakyzumusic.core.data.profile

interface ProfileRepository {
    suspend fun getProfile(): ProfileResult

    suspend fun updateProfile(
        displayName: String,
        completeOnboarding: Boolean,
    ): ProfileResult
}

data class ListenerProfile(
    val userId: String,
    val displayName: String,
    val onboardingCompleted: Boolean,
)

sealed interface ProfileResult {
    data class Success(val profile: ListenerProfile) : ProfileResult

    data class Failure(val reason: ProfileFailure) : ProfileResult
}

enum class ProfileFailure {
    InvalidDisplayName,
    NoActiveSession,
    NetworkUnavailable,
    ServiceUnavailable,
    Unexpected,
}

data class DisplayName(val value: String) {
    fun validate(): DisplayNameValidation {
        val normalized = value.trim().replace(WHITESPACE, " ")
        return if (
            normalized.length in MINIMUM_LENGTH..MAXIMUM_LENGTH &&
            normalized.none(Char::isISOControl)
        ) {
            DisplayNameValidation.Valid(normalized)
        } else {
            DisplayNameValidation.Invalid
        }
    }

    companion object {
        const val MINIMUM_LENGTH = 2
        const val MAXIMUM_LENGTH = 60
        private val WHITESPACE = Regex("\\s+")
    }
}

sealed interface DisplayNameValidation {
    data class Valid(val displayName: String) : DisplayNameValidation

    data object Invalid : DisplayNameValidation
}
