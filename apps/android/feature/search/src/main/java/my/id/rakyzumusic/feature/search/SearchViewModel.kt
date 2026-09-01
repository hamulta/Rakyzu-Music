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
    private val diagnostics: SearchDiagnosticSink = NoOpSearchDiagnosticSink,
    private val elapsedRealtimeMillis: () -> Long = {
        System.nanoTime() / NANOS_PER_MILLISECOND
    },
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
    private var searchIndex = SearchCatalogIndex.from(EMPTY_CATALOG)

    init {
        observeCatalog()
        observeRecentSearches()
        observeConnectivity()
    }

    fun updateQuery(value: String) {
        val boundedQuery = value.take(MAX_QUERY_LENGTH)
        val localResults = searchLocally(boundedQuery)
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
                results = localResults,
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
        val localResults = searchLocally(mutableUiState.value.query)
        mutableUiState.update { state ->
            state.copy(
                selectedBrowseCategoryId = null,
                results = localResults,
            )
        }
    }

    fun submitSearch() {
        val query = mutableUiState.value.query.trim().replace(WHITESPACE, " ")
        if (query.length < MIN_REMOTE_QUERY_LENGTH) return
        submittedQuery = query
        viewModelScope.launch { recentSearchRepository.record(userId, query) }
        requestRemotePage(
            query = query,
            offset = 0,
            append = false,
            trigger = RemoteSearchTrigger.Submit,
        )
    }

    fun retrySearch() = retrySearch(RemoteSearchTrigger.ManualRetry)

    private fun retrySearch(trigger: RemoteSearchTrigger) {
        val query = submittedQuery ?: mutableUiState.value.query
            .trim()
            .replace(WHITESPACE, " ")
            .takeIf { it.length >= MIN_REMOTE_QUERY_LENGTH }
            ?: return
        submittedQuery = query
        requestRemotePage(query = query, offset = 0, append = false, trigger = trigger)
    }

    fun loadNextPage() {
        val state = mutableUiState.value
        val query = submittedQuery ?: return
        val offset = state.nextRemoteOffset ?: return
        if (!state.canLoadMore) return
        requestRemotePage(
            query = query,
            offset = offset,
            append = true,
            trigger = RemoteSearchTrigger.LoadMore,
        )
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
                val categories = catalog.browseCategories()
                searchIndex = SearchCatalogIndex.from(catalog)
                recordDiagnostic(
                    SearchDiagnosticEvent.CatalogObserved(
                        SearchCatalogShape.from(catalog, categories.size),
                    ),
                )
                mutableUiState.update { state ->
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
                        state.query.isNotBlank() -> searchLocally(state.query)
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
                recordDiagnostic(
                    SearchDiagnosticEvent.ConnectivityObserved(
                        isOnline = isOnline,
                        triggersRecovery = shouldRetry,
                    ),
                )
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
                if (shouldRetry) retrySearch(RemoteSearchTrigger.ConnectivityRecovery)
            }
        }
    }

    private fun requestRemotePage(
        query: String,
        offset: Int,
        append: Boolean,
        trigger: RemoteSearchTrigger,
    ) {
        val page = if (append) RemoteSearchPage.Additional else RemoteSearchPage.Initial
        if (!mutableUiState.value.isOnline) {
            recordDiagnostic(SearchDiagnosticEvent.RemoteSearchSkippedOffline(trigger, page))
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
        val startedAtMillis = elapsedRealtimeMillis()
        recordDiagnostic(SearchDiagnosticEvent.RemoteSearchStarted(trigger, page))
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
                    val pageResults = result.page.toSearchResults()
                    recordDiagnostic(
                        SearchDiagnosticEvent.RemoteSearchSucceeded(
                            trigger = trigger,
                            page = page,
                            duration = SearchDuration.from(
                                elapsedRealtimeMillis() - startedAtMillis,
                            ),
                            results = SearchResultShape.from(pageResults),
                            hasNextPage = result.page.nextOffset != null,
                        ),
                    )
                    mutableUiState.update { state ->
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
                    recordDiagnostic(
                        SearchDiagnosticEvent.RemoteSearchFailed(
                            trigger = trigger,
                            page = page,
                            duration = SearchDuration.from(
                                elapsedRealtimeMillis() - startedAtMillis,
                            ),
                            failure = result.reason,
                        ),
                    )
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

    private fun searchLocally(rawQuery: String): SearchResults {
        if (rawQuery.isBlank()) return SearchResults()
        val startedAtMillis = elapsedRealtimeMillis()
        val results = searchIndex.search(rawQuery)
        recordDiagnostic(
            SearchDiagnosticEvent.LocalSearchCompleted(
                duration = SearchDuration.from(elapsedRealtimeMillis() - startedAtMillis),
                results = SearchResultShape.from(results),
            ),
        )
        return results
    }

    private fun recordDiagnostic(event: SearchDiagnosticEvent) {
        try {
            diagnostics.record(event)
        } catch (_: RuntimeException) {
            // Diagnostics must never alter Search behavior.
        }
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
                    diagnostics = AndroidSearchDiagnostics.sink,
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
                    diagnostics = AndroidSearchDiagnostics.sink,
                ) as T
            }
        }
    }
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
private const val TEST_USER_ID = "test-listener"
private const val NANOS_PER_MILLISECOND = 1_000_000L

private val EMPTY_CATALOG = CatalogSnapshot(
    artists = emptyList(),
    albums = emptyList(),
    tracks = emptyList(),
    lastSyncedAtEpochMillis = null,
)
