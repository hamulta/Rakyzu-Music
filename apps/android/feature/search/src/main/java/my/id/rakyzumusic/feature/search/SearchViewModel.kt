package my.id.rakyzumusic.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import my.id.rakyzumusic.core.data.catalog.CatalogRepository
import my.id.rakyzumusic.core.data.catalog.CatalogSearchFailure
import my.id.rakyzumusic.core.data.catalog.CatalogSearchPage
import my.id.rakyzumusic.core.data.catalog.CatalogSearchResult
import my.id.rakyzumusic.core.data.network.ConnectivityMonitor
import my.id.rakyzumusic.core.data.search.RecentSearchRepository
import my.id.rakyzumusic.core.data.search.RecentSearchState
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
    val remoteTotalMatches: Int? = null,
) {
    val displayedCount: Int
        get() = artists.size + albums.size + tracks.size

    val totalMatches: Int
        get() = remoteTotalMatches
            ?: (totalArtistMatches + totalAlbumMatches + totalTrackMatches)

    val hasResults: Boolean
        get() = displayedCount > 0
}

data class BrowseCategory(
    val id: String,
    val title: String,
    val subtitle: String?,
    val tracks: List<Track>,
)

enum class RemoteSearchStatus {
    Idle,
    Loading,
    Loaded,
    Offline,
    Failed,
}

data class SearchUiState(
    val query: String = "",
    val catalog: CatalogSnapshot = EMPTY_CATALOG,
    val results: SearchResults = SearchResults(),
    val browseCategories: List<BrowseCategory> = emptyList(),
    val selectedBrowseCategoryId: String? = null,
    val recentSearchesEnabled: Boolean = false,
    val recentSearches: List<String> = emptyList(),
    val hasObservedCatalog: Boolean = false,
    val isOnline: Boolean = true,
    val remoteSearchStatus: RemoteSearchStatus = RemoteSearchStatus.Idle,
    val remoteSearchFailure: CatalogSearchFailure? = null,
    val nextRemoteOffset: Int? = null,
    val isLoadingMore: Boolean = false,
) {
    val selectedBrowseCategory: BrowseCategory?
        get() = browseCategories.firstOrNull { it.id == selectedBrowseCategoryId }

    val isCatalogEmpty: Boolean
        get() = hasObservedCatalog && catalog.isEmpty

    val isReadyToBrowse: Boolean
        get() = hasObservedCatalog && !catalog.isEmpty && query.isBlank() &&
            selectedBrowseCategory == null

    val isBrowsingCategory: Boolean
        get() = hasObservedCatalog && query.isBlank() && selectedBrowseCategory != null

    val hasNoResults: Boolean
        get() = hasObservedCatalog && query.isNotBlank() && !results.hasResults &&
            remoteSearchStatus != RemoteSearchStatus.Loading

    val canLoadMore: Boolean
        get() = remoteSearchStatus == RemoteSearchStatus.Loaded &&
            nextRemoteOffset != null && !isLoadingMore && isOnline
}

class SearchViewModel internal constructor(
    private val repository: CatalogRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val userId: String = TEST_USER_ID,
    private val recentSearchRepository: RecentSearchRepository = DisabledRecentSearchRepository,
    private val connectivityMonitor: ConnectivityMonitor = AlwaysOnlineConnectivityMonitor,
) : ViewModel() {
    private val restoredQuery = savedStateHandle.get<String>(SAVED_SEARCH_QUERY_KEY)
        .orEmpty()
        .take(MAX_QUERY_LENGTH)
    private val restoredCategoryId = savedStateHandle.get<String>(SAVED_BROWSE_CATEGORY_KEY)
        ?.takeIf(String::isNotBlank)
    private val mutableUiState = MutableStateFlow(
        SearchUiState(
            query = restoredQuery,
            selectedBrowseCategoryId = restoredCategoryId,
            isOnline = connectivityMonitor.isCurrentlyOnline(),
        ),
    )
    val uiState: StateFlow<SearchUiState> = mutableUiState.asStateFlow()

    private var remoteSearchJob: Job? = null
    private var requestGeneration = 0
    private var submittedQuery: String? = null

    init {
        observeCatalog()
        observeRecentSearches()
        observeConnectivity()
    }

    fun updateQuery(value: String) {
        val boundedQuery = value.take(MAX_QUERY_LENGTH)
        cancelRemoteSearch()
        submittedQuery = null
        savedStateHandle[SAVED_SEARCH_QUERY_KEY] = boundedQuery
        if (boundedQuery.isNotBlank()) savedStateHandle[SAVED_BROWSE_CATEGORY_KEY] = null
        mutableUiState.update { state ->
            state.copy(
                query = boundedQuery,
                selectedBrowseCategoryId = if (boundedQuery.isBlank()) {
                    state.selectedBrowseCategoryId
                } else {
                    null
                },
                results = state.catalog.search(boundedQuery),
                remoteSearchStatus = RemoteSearchStatus.Idle,
                remoteSearchFailure = null,
                nextRemoteOffset = null,
                isLoadingMore = false,
            )
        }
    }

    fun clearQuery() {
        savedStateHandle[SAVED_BROWSE_CATEGORY_KEY] = null
        mutableUiState.update { it.copy(selectedBrowseCategoryId = null) }
        updateQuery("")
    }

    fun openBrowseCategory(categoryId: String) {
        val category = mutableUiState.value.browseCategories.firstOrNull { it.id == categoryId }
            ?: return
        cancelRemoteSearch()
        submittedQuery = null
        savedStateHandle[SAVED_SEARCH_QUERY_KEY] = ""
        savedStateHandle[SAVED_BROWSE_CATEGORY_KEY] = category.id
        mutableUiState.update { state ->
            state.copy(
                query = "",
                selectedBrowseCategoryId = category.id,
                results = category.toSearchResults(),
                remoteSearchStatus = RemoteSearchStatus.Idle,
                remoteSearchFailure = null,
                nextRemoteOffset = null,
                isLoadingMore = false,
            )
        }
    }

    fun closeBrowseCategory() {
        savedStateHandle[SAVED_BROWSE_CATEGORY_KEY] = null
        mutableUiState.update { state ->
            state.copy(
                selectedBrowseCategoryId = null,
                results = state.catalog.search(state.query),
            )
        }
    }

    fun submitSearch() {
        val query = mutableUiState.value.query.trim().replace(WHITESPACE, " ")
        if (query.length < MIN_REMOTE_QUERY_LENGTH) return
        submittedQuery = query
        viewModelScope.launch { recentSearchRepository.record(userId, query) }
        requestRemotePage(query = query, offset = 0, append = false)
    }

    fun retrySearch() {
        val query = submittedQuery ?: mutableUiState.value.query
            .trim()
            .replace(WHITESPACE, " ")
            .takeIf { it.length >= MIN_REMOTE_QUERY_LENGTH }
            ?: return
        submittedQuery = query
        requestRemotePage(query = query, offset = 0, append = false)
    }

    fun loadNextPage() {
        val state = mutableUiState.value
        val query = submittedQuery ?: return
        val offset = state.nextRemoteOffset ?: return
        if (!state.canLoadMore) return
        requestRemotePage(query = query, offset = offset, append = true)
    }

    fun selectRecentSearch(query: String) {
        updateQuery(query)
        submitSearch()
    }

    fun setRecentSearchesEnabled(enabled: Boolean) {
        viewModelScope.launch { recentSearchRepository.setEnabled(userId, enabled) }
    }

    fun clearRecentSearches() {
        viewModelScope.launch { recentSearchRepository.clear(userId) }
    }

    private fun observeCatalog() {
        viewModelScope.launch {
            repository.observeCatalog().collect { catalog ->
                mutableUiState.update { state ->
                    val categories = catalog.browseCategories()
                    val selectedId = state.selectedBrowseCategoryId
                        ?.takeIf { id -> categories.any { it.id == id } }
                    val keepRemoteResults = submittedQuery != null &&
                        state.query.normalizedSearchText() == submittedQuery?.normalizedSearchText() &&
                        state.remoteSearchStatus in setOf(
                            RemoteSearchStatus.Loading,
                            RemoteSearchStatus.Loaded,
                            RemoteSearchStatus.Offline,
                            RemoteSearchStatus.Failed,
                        )
                    val results = when {
                        keepRemoteResults -> state.results
                        state.query.isNotBlank() -> catalog.search(state.query)
                        selectedId != null -> categories.first { it.id == selectedId }.toSearchResults()
                        else -> SearchResults()
                    }
                    state.copy(
                        catalog = catalog,
                        results = results,
                        browseCategories = categories,
                        selectedBrowseCategoryId = selectedId,
                        hasObservedCatalog = true,
                    )
                }
            }
        }
    }

    private fun observeRecentSearches() {
        viewModelScope.launch {
            recentSearchRepository.observe(userId).collect { history ->
                mutableUiState.update {
                    it.copy(
                        recentSearchesEnabled = history.isEnabled,
                        recentSearches = history.queries,
                    )
                }
            }
        }
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            var previousOnline: Boolean? = null
            connectivityMonitor.isOnline.collect { isOnline ->
                val shouldRetry = previousOnline == false && isOnline &&
                    submittedQuery != null &&
                    mutableUiState.value.remoteSearchStatus == RemoteSearchStatus.Offline
                if (!isOnline) {
                    cancelRemoteSearch()
                }
                mutableUiState.update { state ->
                    state.copy(
                        isOnline = isOnline,
                        remoteSearchStatus = if (!isOnline && submittedQuery != null) {
                            RemoteSearchStatus.Offline
                        } else {
                            state.remoteSearchStatus
                        },
                        isLoadingMore = if (!isOnline) false else state.isLoadingMore,
                    )
                }
                previousOnline = isOnline
                if (shouldRetry) retrySearch()
            }
        }
    }

    private fun requestRemotePage(query: String, offset: Int, append: Boolean) {
        if (!mutableUiState.value.isOnline) {
            mutableUiState.update {
                it.copy(
                    remoteSearchStatus = RemoteSearchStatus.Offline,
                    remoteSearchFailure = CatalogSearchFailure.NetworkUnavailable,
                    isLoadingMore = false,
                )
            }
            return
        }

        remoteSearchJob?.cancel()
        val generation = ++requestGeneration
        mutableUiState.update {
            it.copy(
                remoteSearchStatus = if (append) it.remoteSearchStatus else RemoteSearchStatus.Loading,
                remoteSearchFailure = null,
                isLoadingMore = append,
            )
        }
        remoteSearchJob = viewModelScope.launch {
            when (val result = repository.searchCatalog(query, offset, REMOTE_PAGE_SIZE)) {
                is CatalogSearchResult.Success -> {
                    if (generation != requestGeneration || submittedQuery != query) return@launch
                    mutableUiState.update { state ->
                        val pageResults = result.page.toSearchResults()
                        state.copy(
                            results = if (append) state.results.append(pageResults) else pageResults,
                            remoteSearchStatus = RemoteSearchStatus.Loaded,
                            remoteSearchFailure = null,
                            nextRemoteOffset = result.page.nextOffset,
                            isLoadingMore = false,
                        )
                    }
                }
                is CatalogSearchResult.Failure -> {
                    if (generation != requestGeneration || submittedQuery != query) return@launch
                    mutableUiState.update { state ->
                        state.copy(
                            remoteSearchStatus = if (
                                result.reason == CatalogSearchFailure.NetworkUnavailable ||
                                !state.isOnline
                            ) {
                                RemoteSearchStatus.Offline
                            } else {
                                RemoteSearchStatus.Failed
                            },
                            remoteSearchFailure = result.reason,
                            nextRemoteOffset = null,
                            isLoadingMore = false,
                        )
                    }
                }
            }
        }
    }

    private fun cancelRemoteSearch() {
        requestGeneration += 1
        remoteSearchJob?.cancel()
        remoteSearchJob = null
    }

    companion object {
        fun factory(
            userId: String,
            repository: CatalogRepository,
            recentSearchRepository: RecentSearchRepository,
            connectivityMonitor: ConnectivityMonitor,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(SearchViewModel::class.java))
                return SearchViewModel(
                    repository = repository,
                    userId = userId,
                    recentSearchRepository = recentSearchRepository,
                    connectivityMonitor = connectivityMonitor,
                ) as T
            }

            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras,
            ): T {
                require(modelClass.isAssignableFrom(SearchViewModel::class.java))
                return SearchViewModel(
                    repository = repository,
                    userId = userId,
                    recentSearchRepository = recentSearchRepository,
                    connectivityMonitor = connectivityMonitor,
                    savedStateHandle = extras.createSavedStateHandle(),
                ) as T
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
        )?.let { score -> Ranked(score, track.title, track.id, track) }
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

internal fun CatalogSnapshot.browseCategories(): List<BrowseCategory> {
    val trackIds = tracks.mapTo(mutableSetOf(), Track::id)
    return editorialShelves.asSequence()
        .sortedWith(compareBy({ it.position }, { it.id }))
        .map { shelf ->
            BrowseCategory(
                id = shelf.id,
                title = shelf.title.trim(),
                subtitle = shelf.subtitle?.trim()?.takeIf(String::isNotEmpty),
                tracks = shelf.tracks.filter { it.id in trackIds }.distinctBy(Track::id),
            )
        }
        .filter { it.id.isNotBlank() && it.title.isNotBlank() && it.tracks.isNotEmpty() }
        .take(MAX_BROWSE_CATEGORIES)
        .toList()
}

internal fun String.normalizedSearchText(): String = Normalizer.normalize(
    trim().replace(WHITESPACE, " "),
    Normalizer.Form.NFD,
).replace(COMBINING_MARKS, "").lowercase(Locale.ROOT)

private fun BrowseCategory.toSearchResults() = SearchResults(
    tracks = tracks,
    totalTrackMatches = tracks.size,
)

private fun CatalogSearchPage.toSearchResults() = SearchResults(
    artists = artists,
    albums = albums.map { SearchAlbumResult(it.album, it.artistName) },
    tracks = tracks,
    totalArtistMatches = artists.size,
    totalAlbumMatches = albums.size,
    totalTrackMatches = tracks.size,
    remoteTotalMatches = totalCount,
)

private fun SearchResults.append(page: SearchResults) = copy(
    artists = (artists + page.artists).distinctBy(Artist::id),
    albums = (albums + page.albums).distinctBy { it.album.id },
    tracks = (tracks + page.tracks).distinctBy(Track::id),
    totalArtistMatches = totalArtistMatches + page.totalArtistMatches,
    totalAlbumMatches = totalAlbumMatches + page.totalAlbumMatches,
    totalTrackMatches = totalTrackMatches + page.totalTrackMatches,
    remoteTotalMatches = page.remoteTotalMatches ?: remoteTotalMatches,
)

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

private data object DisabledRecentSearchRepository : RecentSearchRepository {
    override fun observe(userId: String): Flow<RecentSearchState> = flowOf(RecentSearchState())
    override suspend fun setEnabled(userId: String, enabled: Boolean) = Unit
    override suspend fun record(userId: String, query: String) = Unit
    override suspend fun clear(userId: String) = Unit
}

private data object AlwaysOnlineConnectivityMonitor : ConnectivityMonitor {
    override val isOnline: Flow<Boolean> = flowOf(true)
    override fun isCurrentlyOnline() = true
}

private val WHITESPACE = Regex("\\s+")
private val COMBINING_MARKS = Regex("\\p{M}+")

internal const val MAX_QUERY_LENGTH = 100
internal const val MAX_RESULTS_PER_TYPE = 50
internal const val SAVED_SEARCH_QUERY_KEY = "search_query"
internal const val SAVED_BROWSE_CATEGORY_KEY = "search_browse_category"
internal const val REMOTE_PAGE_SIZE = 30
private const val MIN_REMOTE_QUERY_LENGTH = 2
private const val MAX_BROWSE_CATEGORIES = 12
private const val ASSOCIATED_MATCH_OFFSET = 4
private const val TEST_USER_ID = "test-listener"

private val EMPTY_CATALOG = CatalogSnapshot(
    artists = emptyList(),
    albums = emptyList(),
    tracks = emptyList(),
    lastSyncedAtEpochMillis = null,
)
