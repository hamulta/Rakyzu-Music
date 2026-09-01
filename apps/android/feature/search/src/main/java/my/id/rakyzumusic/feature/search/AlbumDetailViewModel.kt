package my.id.rakyzumusic.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import my.id.rakyzumusic.core.data.catalog.CatalogRepository
import my.id.rakyzumusic.core.model.Album
import my.id.rakyzumusic.core.model.Artist
import my.id.rakyzumusic.core.model.CatalogSnapshot
import my.id.rakyzumusic.core.model.Track

data class AlbumDetailUiState(
    val albumId: String,
    val hasObservedCatalog: Boolean = false,
    val album: Album? = null,
    val artist: Artist? = null,
    val tracks: List<Track> = emptyList(),
) {
    val isUnavailable: Boolean
        get() = hasObservedCatalog && album == null

    val isReady: Boolean
        get() = hasObservedCatalog && album != null

    val discCount: Int
        get() = tracks.map { it.discNumber.coerceAtLeast(1) }.distinct().size
}

class AlbumDetailViewModel internal constructor(
    albumId: String,
    repository: CatalogRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(AlbumDetailUiState(albumId = albumId))
    val uiState: StateFlow<AlbumDetailUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeCatalog().collect { catalog ->
                mutableUiState.update {
                    catalog.albumDetailState(albumId).copy(hasObservedCatalog = true)
                }
            }
        }
    }

    companion object {
        fun factory(
            albumId: String,
            repository: CatalogRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(AlbumDetailViewModel::class.java))
                return AlbumDetailViewModel(albumId, repository) as T
            }
        }
    }
}

internal fun CatalogSnapshot.albumDetailState(albumId: String): AlbumDetailUiState {
    val album = albums.firstOrNull { it.id == albumId }
        ?: return AlbumDetailUiState(albumId = albumId)
    val albumTracks = tracks
        .filter { it.albumId == albumId }
        .sortedWith(ALBUM_TRACK_ORDER)

    return AlbumDetailUiState(
        albumId = albumId,
        album = album,
        artist = artists.firstOrNull { it.id == album.artistId },
        tracks = albumTracks,
    )
}

private val ALBUM_TRACK_ORDER = compareBy<Track> { it.discNumber.coerceAtLeast(1) }
    .thenBy { it.trackNumber.coerceAtLeast(1) }
    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
    .thenBy { it.id }
