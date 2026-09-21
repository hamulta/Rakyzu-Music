package my.id.rakyzumusic.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import my.id.rakyzumusic.core.data.profile.DisplayName
import my.id.rakyzumusic.core.data.profile.ListenerProfile
import my.id.rakyzumusic.core.data.profile.ProfileAppearance
import my.id.rakyzumusic.core.data.profile.ProfileFailure
import my.id.rakyzumusic.core.data.profile.ProfileRepository
import my.id.rakyzumusic.core.data.profile.ProfileResult

data class ProfileUiState(
    val profile: ListenerProfile? = null,
    val displayName: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
)

class ProfileViewModel(
    private val repository: ProfileRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = mutableUiState.asStateFlow()

    init {
        loadProfile()
    }

    fun updateDisplayName(displayName: String) {
        mutableUiState.update {
            it.copy(
                displayName = displayName.take(DisplayName.MAXIMUM_LENGTH),
                message = null,
            )
        }
    }

    fun loadProfile() {
        if (mutableUiState.value.isSaving) return
        mutableUiState.update {
            it.copy(isLoading = true, message = null, messageIsError = false)
        }
        viewModelScope.launch {
            when (val result = repository.getProfile()) {
                is ProfileResult.Success -> mutableUiState.update {
                    it.copy(
                        profile = result.profile,
                        displayName = result.profile.displayName,
                        isLoading = false,
                        message = null,
                        messageIsError = false,
                    )
                }
                is ProfileResult.Failure -> mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        message = result.reason.toSafeMessage(),
                        messageIsError = true,
                    )
                }
            }
        }
    }

    fun saveProfile(completeOnboarding: Boolean) {
        val state = mutableUiState.value
        if (state.isSaving) return

        mutableUiState.update { it.copy(isSaving = true, message = null) }
        viewModelScope.launch {
            when (
                val result = repository.updateProfile(
                    displayName = state.displayName,
                    completeOnboarding = completeOnboarding,
                )
            ) {
                is ProfileResult.Success -> mutableUiState.update {
                    it.copy(
                        profile = result.profile,
                        displayName = result.profile.displayName,
                        isSaving = false,
                        message = if (completeOnboarding) null else "Profile updated.",
                        messageIsError = false,
                    )
                }
                is ProfileResult.Failure -> mutableUiState.update {
                    it.copy(
                        isSaving = false,
                        message = result.reason.toSafeMessage(),
                        messageIsError = true,
                    )
                }
            }
        }
    }

    fun acceptArtistTerms(version: String) {
        if (mutableUiState.value.isSaving || version.isBlank()) return
        mutableUiState.update { it.copy(isSaving = true, message = null) }
        viewModelScope.launch { applyResult(repository.acceptArtistTerms(version), "Welcome to Rakyzu Music Artists.") }
    }

    fun updateArtistBiography(biography: String) {
        if (mutableUiState.value.isSaving || biography.length > 1_500) return
        mutableUiState.update { it.copy(isSaving = true, message = null) }
        viewModelScope.launch {
            applyResult(repository.updateArtistBiography(biography), "Artist biography updated.")
        }
    }

    fun updateAppearance(mode: ProfileAppearance) {
        if (mutableUiState.value.isSaving) return
        mutableUiState.update { it.copy(isSaving = true, message = null) }
        viewModelScope.launch { applyResult(repository.updateAppearance(mode), "Profile appearance updated.") }
    }

    fun uploadAvatar(webpBytes: ByteArray) {
        if (mutableUiState.value.isSaving) return
        mutableUiState.update { it.copy(isSaving = true, message = null) }
        viewModelScope.launch {
            applyResult(repository.uploadAvatar(webpBytes), "Profile photo updated.")
        }
    }

    fun deleteAvatar() {
        if (mutableUiState.value.isSaving) return
        mutableUiState.update { it.copy(isSaving = true, message = null) }
        viewModelScope.launch {
            applyResult(repository.deleteAvatar(), "Profile photo removed.")
        }
    }

    private fun applyResult(result: ProfileResult, successMessage: String) {
        when (result) {
            is ProfileResult.Success -> mutableUiState.update { it.copy(
                profile = result.profile, displayName = result.profile.displayName,
                isSaving = false, message = successMessage, messageIsError = false,
            ) }
            is ProfileResult.Failure -> mutableUiState.update { it.copy(
                isSaving = false, message = result.reason.toSafeMessage(), messageIsError = true,
            ) }
        }
    }

    fun resetDraft() {
        mutableUiState.update {
            it.copy(
                displayName = it.profile?.displayName.orEmpty(),
                message = null,
                messageIsError = false,
            )
        }
    }

    companion object {
        fun factory(repository: ProfileRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(ProfileViewModel::class.java))
                    return ProfileViewModel(repository) as T
                }
            }
    }
}

private fun ProfileFailure.toSafeMessage(): String = when (this) {
    ProfileFailure.InvalidRequest -> "The requested profile action is no longer available."
    ProfileFailure.InvalidDisplayName ->
        "Use ${DisplayName.MINIMUM_LENGTH}-${DisplayName.MAXIMUM_LENGTH} characters for your name."
    ProfileFailure.NoActiveSession -> "Your session expired. Sign in again to continue."
    ProfileFailure.NetworkUnavailable -> "No connection. Check your network and try again."
    ProfileFailure.ServiceUnavailable -> "Your Rakyzu Music profile is temporarily unavailable."
    ProfileFailure.Unexpected -> "Unable to load your profile. Try again."
}
