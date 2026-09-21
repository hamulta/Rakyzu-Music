package my.id.rakyzumusic.core.data.profile

interface ProfileRepository {
    suspend fun getProfile(): ProfileResult
    suspend fun updateProfile(displayName: String, completeOnboarding: Boolean): ProfileResult
    suspend fun acceptArtistTerms(version: String): ProfileResult
    suspend fun updateArtistBiography(biography: String): ProfileResult
    suspend fun updateAppearance(mode: ProfileAppearance): ProfileResult
    suspend fun uploadAvatar(webpBytes: ByteArray): ProfileResult
    suspend fun deleteAvatar(): ProfileResult
}

enum class ProfileAppearance(val wireName: String) {
    Default("default"), Role("role");
    companion object {
        fun fromWire(value: String?) = entries.firstOrNull { it.wireName == value } ?: Role
    }
}

data class ArtistIdentity(
    val id: String,
    val name: String,
    val status: String,
    val termsVersion: String?,
    val termsTitle: String?,
    val termsSummary: String?,
    val termsText: String?,
    val biography: String = "",
) {
    val requiresConsent: Boolean get() = status == "pending_consent"
    val isActive: Boolean get() = status == "active"
}

data class ListenerProfile(
    val userId: String,
    val displayName: String,
    val onboardingCompleted: Boolean,
    val avatarAvailable: Boolean = false,
    val appearance: ProfileAppearance = ProfileAppearance.Role,
    val identityKind: String = "listener",
    val role: String? = null,
    val verified: Boolean = false,
    val artist: ArtistIdentity? = null,
    val avatarVersion: String? = null,
)

sealed interface ProfileResult {
    data class Success(val profile: ListenerProfile) : ProfileResult
    data class Failure(val reason: ProfileFailure) : ProfileResult
}

enum class ProfileFailure {
    InvalidDisplayName, InvalidRequest, NoActiveSession, NetworkUnavailable,
    ServiceUnavailable, Unexpected,
}

data class DisplayName(val value: String) {
    fun validate(): DisplayNameValidation {
        val normalized = value.trim().replace(WHITESPACE, " ")
        return if (normalized.length in MINIMUM_LENGTH..MAXIMUM_LENGTH &&
            normalized.none(Char::isISOControl)
        ) DisplayNameValidation.Valid(normalized) else DisplayNameValidation.Invalid
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
