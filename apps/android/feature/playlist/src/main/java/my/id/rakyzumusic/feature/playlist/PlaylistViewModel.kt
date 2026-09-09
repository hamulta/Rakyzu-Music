package my.id.rakyzumusic.feature.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import my.id.rakyzumusic.core.data.playlist.PlaylistActionResult
import my.id.rakyzumusic.core.data.playlist.PlaylistFailure
import my.id.rakyzumusic.core.data.playlist.PlaylistRepository
import my.id.rakyzumusic.core.model.PlaylistSummary

data class PlaylistUiState(
    val hasObservedPlaylists: Boolean = false,
    val playlists: List<PlaylistSummary> = emptyList(),
    val name: String = "",
    val description: String = "",
    val isRefreshing: Boolean = false,
    val isCreating: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
) {
    val canCreate: Boolean
        get() = name.isNotBlank() && name.trim().length <= MAX_PLAYLIST_NAME_LENGTH &&
            description.trim().length <= MAX_PLAYLIST_DESCRIPTION_LENGTH && !isCreating
}

class PlaylistViewModel internal constructor(
    private val userId: String,
    private val repository: PlaylistRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        PlaylistUiState(
            name = savedStateHandle.get<String>(SAVED_NAME_KEY).orEmpty()
                .take(MAX_PLAYLIST_NAME_LENGTH),
            description = savedStateHandle.get<String>(SAVED_DESCRIPTION_KEY).orEmpty()
                .take(MAX_PLAYLIST_DESCRIPTION_LENGTH),
        ),
    )
    val uiState: StateFlow<PlaylistUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observePlaylists(userId).collect { snapshot ->
                mutableUiState.update {
                    it.copy(
                        hasObservedPlaylists = true,
                        playlists = snapshot.playlists,
                    )
                }
            }
        }
        refresh()
    }

    fun updateName(value: String) {
        val bounded = value.take(MAX_PLAYLIST_NAME_LENGTH)
        savedStateHandle[SAVED_NAME_KEY] = bounded
        mutableUiState.update { it.copy(name = bounded, message = null) }
    }

    fun updateDescription(value: String) {
        val bounded = value.take(MAX_PLAYLIST_DESCRIPTION_LENGTH)
        savedStateHandle[SAVED_DESCRIPTION_KEY] = bounded
        mutableUiState.update { it.copy(description = bounded, message = null) }
    }

    fun refresh() {
        if (mutableUiState.value.isRefreshing) return
        mutableUiState.update { it.copy(isRefreshing = true, message = null) }
        viewModelScope.launch {
            val result = repository.refresh(userId)
            mutableUiState.update {
                when (result) {
                    is PlaylistActionResult.Success -> it.copy(isRefreshing = false)
                    is PlaylistActionResult.Failure -> it.copy(
                        isRefreshing = false,
                        message = result.reason.toMessage(hasSavedContent = it.playlists.isNotEmpty()),
                        messageIsError = true,
                    )
                }
            }
        }
    }

    fun create() {
        val state = mutableUiState.value
        if (!state.canCreate) return
        mutableUiState.update { it.copy(isCreating = true, message = null) }
        viewModelScope.launch {
            val result = repository.create(userId, state.name, state.description)
            mutableUiState.update {
                when (result) {
                    is PlaylistActionResult.Success -> {
                        savedStateHandle[SAVED_NAME_KEY] = ""
                        savedStateHandle[SAVED_DESCRIPTION_KEY] = ""
                        it.copy(
                            name = "",
                            description = "",
                            isCreating = false,
                            message = "Playlist created.",
                            messageIsError = false,
                        )
                    }
                    is PlaylistActionResult.Failure -> it.copy(
                        isCreating = false,
                        message = result.reason.toMessage(hasSavedContent = false),
                        messageIsError = true,
                    )
                }
            }
        }
    }

    companion object {
        fun factory(userId: String, repository: PlaylistRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                    PlaylistViewModel(userId, repository, extras.createSavedStateHandle()) as T
            }
    }
}

private fun PlaylistFailure.toMessage(hasSavedContent: Boolean): String = when (this) {
    PlaylistFailure.NetworkUnavailable -> if (hasSavedContent) {
        "You're offline. Showing playlists saved on this device."
    } else {
        "You're offline. Connect to load your playlists."
    }
    PlaylistFailure.ServiceUnavailable -> "Playlists are temporarily unavailable. Try again."
    PlaylistFailure.InvalidRequest -> "Check the playlist name and description."
    PlaylistFailure.InvalidPayload -> "Playlist data could not be verified. Try again."
}

internal const val MAX_PLAYLIST_NAME_LENGTH = 100
internal const val MAX_PLAYLIST_DESCRIPTION_LENGTH = 300
private const val SAVED_NAME_KEY = "playlist-name"
private const val SAVED_DESCRIPTION_KEY = "playlist-description"
