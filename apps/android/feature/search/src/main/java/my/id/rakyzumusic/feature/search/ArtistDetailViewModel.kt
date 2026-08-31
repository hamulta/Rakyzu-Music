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

data class ArtistRelease(
    val album: Album,
    val trackCount: Int,
)

data class ArtistDetailUiState(
    val artistId: String,
    val hasObservedCatalog: Boolean = false,
    val artist: Artist? = null,
    val releases: List<ArtistRelease> = emptyList(),
    val tracks: List<Track> = emptyList(),
) {
    val isUnavailable: Boolean
        get() = hasObservedCatalog && artist == null

    val isReady: Boolean
        get() = hasObservedCatalog && artist != null
}

class ArtistDetailViewModel internal constructor(
    artistId: String,
    repository: CatalogRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ArtistDetailUiState(artistId = artistId))
    val uiState: StateFlow<ArtistDetailUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeCatalog().collect { catalog ->
                mutableUiState.update {
                    catalog.artistDetailState(artistId).copy(hasObservedCatalog = true)
                }
            }
        }
    }

    companion object {
        fun factory(
            artistId: String,
            repository: CatalogRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(ArtistDetailViewModel::class.java))
                return ArtistDetailViewModel(artistId, repository) as T
            }
        }
    }
}

internal fun CatalogSnapshot.artistDetailState(artistId: String): ArtistDetailUiState {
    val artist = artists.firstOrNull { it.id == artistId }
        ?: return ArtistDetailUiState(artistId = artistId)
    val artistAlbums = albums
        .filter { it.artistId == artistId }
        .sortedWith(ALBUM_ORDER)
    val albumOrder = artistAlbums.mapIndexed { index, album -> album.id to index }.toMap()
    val artistTracks = tracks
        .filter { it.artistId == artistId }
        .sortedWith(
            compareBy<Track> { albumOrder[it.albumId] ?: Int.MAX_VALUE }
                .thenBy { it.discNumber }
                .thenBy { it.trackNumber }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                .thenBy { it.id },
        )
    val trackCounts = artistTracks.groupingBy(Track::albumId).eachCount()

    return ArtistDetailUiState(
        artistId = artistId,
        artist = artist,
        releases = artistAlbums.map { album ->
            ArtistRelease(album = album, trackCount = trackCounts[album.id] ?: 0)
        },
        tracks = artistTracks,
    )
}

private val ALBUM_ORDER = compareByDescending<Album> { it.releaseDate.orEmpty() }
    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
    .thenBy { it.id }
