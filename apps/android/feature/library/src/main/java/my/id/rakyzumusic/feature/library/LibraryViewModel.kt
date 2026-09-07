package my.id.rakyzumusic.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import my.id.rakyzumusic.core.data.library.LibraryActionResult
import my.id.rakyzumusic.core.data.library.LibraryFailure
import my.id.rakyzumusic.core.data.library.LibraryRepository
import my.id.rakyzumusic.core.model.LibraryItemKind
import my.id.rakyzumusic.core.model.LibrarySnapshot

data class LibraryUiState(
    val hasObservedLibrary: Boolean = false,
    val library: LibrarySnapshot = EMPTY_LIBRARY,
    val isRefreshing: Boolean = false,
    val pendingItems: Set<String> = emptySet(),
    val message: String? = null,
    val messageIsError: Boolean = false,
) {
    val likedTrackIds: Set<String>
        get() = library.likedTracks.mapTo(mutableSetOf()) { it.id }

    val savedAlbumIds: Set<String>
        get() = library.savedAlbums.mapTo(mutableSetOf()) { it.album.id }

    val followedArtistIds: Set<String>
        get() = library.followedArtists.mapTo(mutableSetOf()) { it.id }

    val pendingTrackIds: Set<String>
        get() = pendingItems.filterItemIds(LibraryItemKind.Track)

    val pendingAlbumIds: Set<String>
        get() = pendingItems.filterItemIds(LibraryItemKind.Album)

    val pendingArtistIds: Set<String>
        get() = pendingItems.filterItemIds(LibraryItemKind.Artist)

    fun isPending(kind: LibraryItemKind, itemId: String): Boolean =
        itemKey(kind, itemId) in pendingItems
}

class LibraryViewModel internal constructor(
    private val userId: String,
    private val repository: LibraryRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeLibrary(userId).collect { library ->
                mutableUiState.update {
                    it.copy(hasObservedLibrary = true, library = library)
                }
            }
        }
        refresh()
    }

    fun refresh() {
        if (mutableUiState.value.isRefreshing) return
        mutableUiState.update { it.copy(isRefreshing = true, message = null) }
        viewModelScope.launch {
            val result = repository.refresh(userId)
            mutableUiState.update {
                it.copy(
                    isRefreshing = false,
                    message = result.failureMessage(),
                    messageIsError = result is LibraryActionResult.Failure,
                )
            }
        }
    }

    fun setSaved(kind: LibraryItemKind, itemId: String, saved: Boolean) {
        val key = itemKey(kind, itemId)
        if (itemId.isBlank() || key in mutableUiState.value.pendingItems) return
        mutableUiState.update {
            it.copy(pendingItems = it.pendingItems + key, message = null)
        }
        viewModelScope.launch {
            val result = repository.setSaved(userId, kind, itemId, saved)
            mutableUiState.update {
                it.copy(
                    pendingItems = it.pendingItems - key,
                    message = when (result) {
                        LibraryActionResult.Success -> kind.successMessage(saved)
                        is LibraryActionResult.Failure -> result.failureMessage()
                    },
                    messageIsError = result is LibraryActionResult.Failure,
                )
            }
        }
    }

    companion object {
        fun factory(userId: String, repository: LibraryRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(LibraryViewModel::class.java))
                    return LibraryViewModel(userId, repository) as T
                }
            }
    }
}

private fun LibraryActionResult.failureMessage(): String? = when (this) {
    LibraryActionResult.Success -> null
    is LibraryActionResult.Failure -> when (reason) {
        LibraryFailure.NetworkUnavailable -> "Library sync needs an internet connection."
        LibraryFailure.ServiceUnavailable -> "Your Library is temporarily unavailable."
        LibraryFailure.InvalidRequest -> "That Library action is not valid."
        LibraryFailure.InvalidPayload -> "Library returned an invalid response."
    }
}

private fun LibraryItemKind.successMessage(saved: Boolean): String = when (this) {
    LibraryItemKind.Track -> if (saved) "Added to Liked Songs." else "Removed from Liked Songs."
    LibraryItemKind.Album -> if (saved) "Album saved." else "Album removed."
    LibraryItemKind.Artist -> if (saved) "Artist followed." else "Artist unfollowed."
}

private fun itemKey(kind: LibraryItemKind, itemId: String) = "${kind.name}:$itemId"

private fun Set<String>.filterItemIds(kind: LibraryItemKind): Set<String> {
    val prefix = "${kind.name}:"
    return asSequence()
        .filter { it.startsWith(prefix) }
        .map { it.removePrefix(prefix) }
        .toSet()
}

private val EMPTY_LIBRARY = LibrarySnapshot(
    likedTracks = emptyList(),
    savedAlbums = emptyList(),
    followedArtists = emptyList(),
    lastSyncedAtEpochMillis = null,
)
