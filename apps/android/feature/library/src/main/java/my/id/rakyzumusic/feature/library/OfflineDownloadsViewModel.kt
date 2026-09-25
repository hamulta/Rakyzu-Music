package my.id.rakyzumusic.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import my.id.rakyzumusic.core.data.download.OfflineDownloadActionResult
import my.id.rakyzumusic.core.data.download.OfflineDownloadFailure
import my.id.rakyzumusic.core.data.download.OfflineDownloadRepository
import my.id.rakyzumusic.core.model.DownloadCollectionKind
import my.id.rakyzumusic.core.model.OfflineDownloadsSnapshot
import my.id.rakyzumusic.core.model.Track

data class OfflineDownloadsUiState(
    val snapshot: OfflineDownloadsSnapshot = OfflineDownloadsSnapshot(),
    val isWorking: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
)

class OfflineDownloadsViewModel internal constructor(
    private val userId: String,
    private val repository: OfflineDownloadRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(OfflineDownloadsUiState())
    val uiState: StateFlow<OfflineDownloadsUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observe(userId).collect { snapshot ->
                mutableState.update { it.copy(snapshot = snapshot) }
            }
        }
    }

    fun downloadAlbum(albumId: String, title: String, tracks: List<Track>) = perform {
        repository.enqueueCollection(
            userId,
            DownloadCollectionKind.Album,
            albumId,
            title,
            tracks,
        )
    }

    fun downloadPlaylist(playlistId: String, title: String, tracks: List<Track>) = perform {
        repository.enqueueCollection(
            userId,
            DownloadCollectionKind.Playlist,
            playlistId,
            title,
            tracks,
        )
    }

    fun pause(trackId: String) = perform { repository.pause(userId, trackId) }
    fun resume(trackId: String) = perform { repository.resume(userId, trackId) }
    fun cancel(trackId: String) = perform { repository.cancel(userId, trackId) }
    fun retry(trackId: String) = perform { repository.retry(userId, trackId) }
    fun setAllowMobileDownloads(allow: Boolean) = perform {
        repository.setAllowMobileDownloads(userId, allow)
    }
    fun clearCompleted() = perform { repository.clearCompleted(userId) }

    private fun perform(action: suspend () -> OfflineDownloadActionResult) {
        if (mutableState.value.isWorking) return
        mutableState.update { it.copy(isWorking = true, message = null, messageIsError = false) }
        viewModelScope.launch {
            try {
                when (val result = action()) {
                    OfflineDownloadActionResult.Accepted -> mutableState.update {
                        it.copy(isWorking = false, message = "Offline downloads updated.")
                    }
                    is OfflineDownloadActionResult.Rejected -> mutableState.update {
                        it.copy(
                            isWorking = false,
                            message = result.reason.toMessage(),
                            messageIsError = true,
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(
                        isWorking = false,
                        message = "Offline download action failed. Try again.",
                        messageIsError = true,
                    )
                }
            }
        }
    }

    companion object {
        fun factory(userId: String, repository: OfflineDownloadRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(OfflineDownloadsViewModel::class.java))
                    return OfflineDownloadsViewModel(userId, repository) as T
                }
            }
    }
}

private fun OfflineDownloadFailure.toMessage(): String = when (this) {
    OfflineDownloadFailure.InvalidRequest -> "This collection has no downloadable tracks."
    OfflineDownloadFailure.WrongAccount -> "Downloads belong to the active Rakyzu Music account."
    OfflineDownloadFailure.InsufficientStorage -> "Keep at least 1 GB free before downloading."
    OfflineDownloadFailure.NotAuthenticated -> "Sign in again to renew download access."
    OfflineDownloadFailure.NotEntitled -> "Your account no longer has offline access to this track."
    OfflineDownloadFailure.MediaUnavailable -> "This track is not available for offline listening."
    OfflineDownloadFailure.Network -> "Download interrupted. Retry when your connection is stable."
    OfflineDownloadFailure.Storage -> "Encrypted device storage is unavailable."
}
