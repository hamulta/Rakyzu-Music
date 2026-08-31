package my.id.rakyzumusic.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import java.text.Normalizer
import java.util.Locale
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

data class SearchAlbumResult(
    val album: Album,
    val artistName: String,
)

data class SearchResults(
    val artists: List<Artist> = emptyList(),
    val albums: List<SearchAlbumResult> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val totalArtistMatches: Int = 0,
    val totalAlbumMatches: Int = 0,
    val totalTrackMatches: Int = 0,
) {
    val displayedCount: Int
        get() = artists.size + albums.size + tracks.size

    val totalMatches: Int
        get() = totalArtistMatches + totalAlbumMatches + totalTrackMatches

    val hasResults: Boolean
        get() = displayedCount > 0
}

data class SearchUiState(
    val query: String = "",
    val catalog: CatalogSnapshot = EMPTY_CATALOG,
    val results: SearchResults = SearchResults(),
    val hasObservedCatalog: Boolean = false,
) {
    val isCatalogEmpty: Boolean
        get() = hasObservedCatalog && catalog.isEmpty

    val isReadyToBrowse: Boolean
        get() = hasObservedCatalog && !catalog.isEmpty && query.isBlank()

    val hasNoResults: Boolean
        get() = hasObservedCatalog && query.isNotBlank() && !results.hasResults
}

class SearchViewModel internal constructor(
    private val repository: CatalogRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val restoredQuery = savedStateHandle.get<String>(SAVED_SEARCH_QUERY_KEY)
        .orEmpty()
        .take(MAX_QUERY_LENGTH)
    private val mutableUiState = MutableStateFlow(SearchUiState(query = restoredQuery))
    val uiState: StateFlow<SearchUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeCatalog().collect { catalog ->
                mutableUiState.update { state ->
                    state.copy(
                        catalog = catalog,
                        results = catalog.search(state.query),
                        hasObservedCatalog = true,
                    )
                }
            }
        }
    }

    fun updateQuery(value: String) {
        val boundedQuery = value.take(MAX_QUERY_LENGTH)
        savedStateHandle[SAVED_SEARCH_QUERY_KEY] = boundedQuery
        mutableUiState.update { state ->
            state.copy(
                query = boundedQuery,
                results = state.catalog.search(boundedQuery),
            )
        }
    }

    fun clearQuery() = updateQuery("")

    companion object {
        fun factory(repository: CatalogRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(SearchViewModel::class.java))
                    return SearchViewModel(repository) as T
                }

                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras,
                ): T {
                    require(modelClass.isAssignableFrom(SearchViewModel::class.java))
                    return SearchViewModel(repository, extras.createSavedStateHandle()) as T
                }
            }
    }
}

internal fun CatalogSnapshot.search(rawQuery: String): SearchResults {
    val query = rawQuery.normalizedSearchText()
    if (query.isBlank()) return SearchResults()

    val artistsById = artists.associateBy(Artist::id)
    val artistMatches = artists.mapNotNull { artist ->
        artist.name.searchScore(query)?.let { score -> Ranked(score, artist.name, artist.id, artist) }
    }.sortedWith(RANKED_ORDER)

    val albumMatches = albums.mapNotNull { album ->
        val artistName = artistsById[album.artistId]?.name.orEmpty()
        primaryOrAssociatedSearchScore(query, album.title, artistName)?.let { score ->
            Ranked(
                score = score,
                label = album.title,
                id = album.id,
                value = SearchAlbumResult(album, artistName),
            )
        }
    }.sortedWith(RANKED_ORDER)

    val trackMatches = tracks.mapNotNull { track ->
        primaryOrAssociatedSearchScore(
            query,
            track.title,
            track.artist,
            track.albumTitle,
        )?.let { score ->
            Ranked(score, track.title, track.id, track)
        }
    }.sortedWith(RANKED_ORDER)

    return SearchResults(
        artists = artistMatches.take(MAX_RESULTS_PER_TYPE).map(Ranked<Artist>::value),
        albums = albumMatches.take(MAX_RESULTS_PER_TYPE).map(Ranked<SearchAlbumResult>::value),
        tracks = trackMatches.take(MAX_RESULTS_PER_TYPE).map(Ranked<Track>::value),
        totalArtistMatches = artistMatches.size,
        totalAlbumMatches = albumMatches.size,
        totalTrackMatches = trackMatches.size,
    )
}

internal fun String.normalizedSearchText(): String = Normalizer.normalize(
    trim().replace(WHITESPACE, " "),
    Normalizer.Form.NFD,
).replace(COMBINING_MARKS, "").lowercase(Locale.ROOT)

private fun primaryOrAssociatedSearchScore(
    query: String,
    primary: String,
    vararg associated: String,
): Int? = primary.searchScore(query) ?: associated
    .mapNotNull { it.searchScore(query) }
    .minOrNull()
    ?.plus(ASSOCIATED_MATCH_OFFSET)

private fun String.searchScore(query: String): Int? {
    val candidate = normalizedSearchText()
    return when {
        candidate == query -> 0
        candidate.startsWith(query) -> 1
        candidate.split(' ').any { it.startsWith(query) } -> 2
        candidate.contains(query) -> 3
        else -> null
    }
}

private data class Ranked<T>(
    val score: Int,
    val label: String,
    val id: String,
    val value: T,
)

private val RANKED_ORDER = compareBy<Ranked<*>> { it.score }
    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
    .thenBy { it.id }

private val WHITESPACE = Regex("\\s+")
private val COMBINING_MARKS = Regex("\\p{M}+")

internal const val MAX_QUERY_LENGTH = 100
internal const val MAX_RESULTS_PER_TYPE = 50
internal const val SAVED_SEARCH_QUERY_KEY = "search_query"
private const val ASSOCIATED_MATCH_OFFSET = 4

private val EMPTY_CATALOG = CatalogSnapshot(
    artists = emptyList(),
    albums = emptyList(),
    tracks = emptyList(),
    lastSyncedAtEpochMillis = null,
)
