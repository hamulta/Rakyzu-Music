package my.id.rakyzumusic.feature.search

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import my.id.rakyzumusic.core.data.catalog.CatalogRefreshResult
import my.id.rakyzumusic.core.data.catalog.CatalogRepository
import my.id.rakyzumusic.core.data.catalog.CatalogSearchPage
import my.id.rakyzumusic.core.data.catalog.CatalogSearchResult
import my.id.rakyzumusic.core.data.network.ConnectivityMonitor
import my.id.rakyzumusic.core.data.search.RecentSearchRepository
import my.id.rakyzumusic.core.data.search.RecentSearchState
import my.id.rakyzumusic.core.model.CatalogSnapshot
import my.id.rakyzumusic.core.model.EditorialShelf
import my.id.rakyzumusic.core.model.HomeFeedSnapshot
import my.id.rakyzumusic.core.model.Track
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun firstCatalogEmissionOpensOfflineBrowseState() = runTest(dispatcher) {
        val repository = FakeCatalogRepository(CATALOG)
        val viewModel = SearchViewModel(repository)

        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasObservedCatalog)
        assertTrue(viewModel.uiState.value.isReadyToBrowse)
        assertFalse(viewModel.uiState.value.isCatalogEmpty)
    }

    @Test
    fun queryUpdatesResultsImmediatelyWithoutRepositoryRefresh() = runTest(dispatcher) {
        val repository = FakeCatalogRepository(CATALOG)
        val viewModel = SearchViewModel(repository)
        testScheduler.advanceUntilIdle()

        viewModel.updateQuery("midnight")

        assertEquals(listOf("track-1"), viewModel.uiState.value.results.tracks.map(Track::id))
        assertEquals(0, repository.refreshCalls)
    }

    @Test
    fun catalogReplacementRecomputesTheActiveQuery() = runTest(dispatcher) {
        val repository = FakeCatalogRepository(CATALOG)
        val viewModel = SearchViewModel(repository)
        testScheduler.advanceUntilIdle()
        viewModel.updateQuery("afterglow")
        assertTrue(viewModel.uiState.value.hasNoResults)

        repository.catalog.value = CATALOG.copy(
            tracks = CATALOG.tracks + track("track-2", "Afterglow Circuit"),
        )
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("track-2"), viewModel.uiState.value.results.tracks.map(Track::id))
        assertFalse(viewModel.uiState.value.hasNoResults)
    }

    @Test
    fun queryLengthIsBoundedAndClearResetsBrowseState() = runTest(dispatcher) {
        val viewModel = SearchViewModel(FakeCatalogRepository(CATALOG))
        testScheduler.advanceUntilIdle()

        viewModel.updateQuery("x".repeat(MAX_QUERY_LENGTH + 25))
        assertEquals(MAX_QUERY_LENGTH, viewModel.uiState.value.query.length)

        viewModel.clearQuery()
        assertEquals("", viewModel.uiState.value.query)
        assertTrue(viewModel.uiState.value.isReadyToBrowse)
    }

    @Test
    fun savedQueryIsRestoredAndAppliedToTheFirstCatalog() = runTest(dispatcher) {
        val savedState = SavedStateHandle(mapOf(SAVED_SEARCH_QUERY_KEY to "midnight"))
        val viewModel = SearchViewModel(FakeCatalogRepository(CATALOG), savedState)

        testScheduler.advanceUntilIdle()

        assertEquals("midnight", viewModel.uiState.value.query)
        assertEquals(listOf("track-1"), viewModel.uiState.value.results.tracks.map(Track::id))
    }

    @Test
    fun boundedQueryAndClearAreWrittenToSavedState() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        val viewModel = SearchViewModel(FakeCatalogRepository(CATALOG), savedState)
        testScheduler.advanceUntilIdle()

        viewModel.updateQuery("x".repeat(MAX_QUERY_LENGTH + 25))
        assertEquals(
            "x".repeat(MAX_QUERY_LENGTH),
            savedState.get<String>(SAVED_SEARCH_QUERY_KEY),
        )

        viewModel.clearQuery()
        assertEquals("", savedState.get<String>(SAVED_SEARCH_QUERY_KEY))
    }

    @Test
    fun curatedCategoryOpensDeterministicOfflineQueueAndRestoresBrowse() = runTest(dispatcher) {
        val categoryTrack = track("track-2", "Afterglow Circuit")
        val catalog = CATALOG.copy(
            tracks = CATALOG.tracks + categoryTrack,
            editorialShelves = listOf(
                EditorialShelf("shelf-1", "Fresh Signals", "New discoveries", 0, listOf(categoryTrack)),
            ),
        )
        val viewModel = SearchViewModel(FakeCatalogRepository(catalog))
        testScheduler.advanceUntilIdle()

        viewModel.openBrowseCategory("shelf-1")

        assertTrue(viewModel.uiState.value.isBrowsingCategory)
        assertEquals(listOf("track-2"), viewModel.uiState.value.results.tracks.map(Track::id))
        viewModel.closeBrowseCategory()
        assertTrue(viewModel.uiState.value.isReadyToBrowse)
    }

    @Test
    fun submitAndLoadMoreUseOneAuthenticatedPagedBoundary() = runTest(dispatcher) {
        val repository = FakeCatalogRepository(CATALOG).apply {
            searchResponse = { offset ->
                val track = if (offset == 0) {
                    track("remote-1", "Signal One")
                } else {
                    track("remote-2", "Signal Two")
                }
                CatalogSearchResult.Success(
                    CatalogSearchPage(
                        artists = emptyList(),
                        albums = emptyList(),
                        tracks = listOf(track),
                        totalCount = 2,
                        nextOffset = if (offset == 0) 1 else null,
                    ),
                )
            }
        }
        val history = FakeRecentSearchRepository(RecentSearchState(isEnabled = true))
        val viewModel = SearchViewModel(
            repository = repository,
            userId = "listener-1",
            recentSearchRepository = history,
        )
        testScheduler.advanceUntilIdle()

        viewModel.updateQuery("signal")
        viewModel.submitSearch()
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(0), repository.searchOffsets)
        assertEquals(listOf("remote-1"), viewModel.uiState.value.results.tracks.map(Track::id))
        assertTrue(viewModel.uiState.value.canLoadMore)
        assertEquals(listOf("signal"), history.state.value.queries)

        viewModel.loadNextPage()
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(0, 1), repository.searchOffsets)
        assertEquals(
            listOf("remote-1", "remote-2"),
            viewModel.uiState.value.results.tracks.map(Track::id),
        )
        assertFalse(viewModel.uiState.value.canLoadMore)
    }

    @Test
    fun offlineSubmitKeepsLocalResultsAndRetriesOnceOnValidatedRecovery() = runTest(dispatcher) {
        val repository = FakeCatalogRepository(CATALOG).apply {
            searchResponse = {
                CatalogSearchResult.Success(
                    CatalogSearchPage(
                        artists = emptyList(),
                        albums = emptyList(),
                        tracks = listOf(track("remote-1", "Midnight Remote")),
                        totalCount = 1,
                        nextOffset = null,
                    ),
                )
            }
        }
        val connectivity = FakeConnectivityMonitor(false)
        val viewModel = SearchViewModel(
            repository = repository,
            connectivityMonitor = connectivity,
        )
        testScheduler.advanceUntilIdle()
        viewModel.updateQuery("midnight")

        viewModel.submitSearch()

        assertEquals(RemoteSearchStatus.Offline, viewModel.uiState.value.remoteSearchStatus)
        assertEquals(listOf("track-1"), viewModel.uiState.value.results.tracks.map(Track::id))
        assertEquals(emptyList<Int>(), repository.searchOffsets)

        connectivity.online.value = true
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(0), repository.searchOffsets)
        assertEquals(RemoteSearchStatus.Loaded, viewModel.uiState.value.remoteSearchStatus)
        assertEquals(listOf("remote-1"), viewModel.uiState.value.results.tracks.map(Track::id))
    }

    @Test
    fun historyIsOptInAndCanBeClearedWithoutChangingQuery() = runTest(dispatcher) {
        val history = FakeRecentSearchRepository()
        val viewModel = SearchViewModel(
            repository = FakeCatalogRepository(CATALOG),
            userId = "listener-1",
            recentSearchRepository = history,
        )
        testScheduler.advanceUntilIdle()

        viewModel.setRecentSearchesEnabled(true)
        testScheduler.advanceUntilIdle()
        viewModel.updateQuery("midnight")
        viewModel.submitSearch()
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.recentSearchesEnabled)
        assertEquals(listOf("midnight"), viewModel.uiState.value.recentSearches)
        viewModel.clearRecentSearches()
        testScheduler.advanceUntilIdle()
        assertEquals(emptyList<String>(), viewModel.uiState.value.recentSearches)
        assertEquals("midnight", viewModel.uiState.value.query)
    }

    private class FakeCatalogRepository(initial: CatalogSnapshot) : CatalogRepository {
        val catalog = MutableStateFlow(initial)
        var refreshCalls = 0
        val searchOffsets = mutableListOf<Int>()
        var searchResponse: (Int) -> CatalogSearchResult = {
            CatalogSearchResult.Success(
                CatalogSearchPage(emptyList(), emptyList(), emptyList(), 0, null),
            )
        }

        override fun observeCatalog() = catalog

        override fun observeHomeFeed(userId: String) = MutableStateFlow(
            HomeFeedSnapshot(catalog.value, emptyList()),
        )

        override suspend fun refresh(): CatalogRefreshResult {
            refreshCalls += 1
            return CatalogRefreshResult.Success(42L)
        }

        override suspend fun recordRecentlyPlayed(userId: String, trackId: String) = true

        override suspend fun searchCatalog(
            query: String,
            offset: Int,
            limit: Int,
        ): CatalogSearchResult {
            searchOffsets += offset
            assertEquals(REMOTE_PAGE_SIZE, limit)
            return searchResponse(offset)
        }
    }

    private class FakeRecentSearchRepository(
        initial: RecentSearchState = RecentSearchState(),
    ) : RecentSearchRepository {
        val state = MutableStateFlow(initial)

        override fun observe(userId: String) = state

        override suspend fun setEnabled(userId: String, enabled: Boolean) {
            state.value = state.value.copy(
                isEnabled = enabled,
                queries = if (enabled) state.value.queries else emptyList(),
            )
        }

        override suspend fun record(userId: String, query: String) {
            if (state.value.isEnabled) {
                state.value = state.value.copy(
                    queries = listOf(query) + state.value.queries.filterNot {
                        it.equals(query, ignoreCase = true)
                    },
                )
            }
        }

        override suspend fun clear(userId: String) {
            state.value = state.value.copy(queries = emptyList())
        }
    }

    private class FakeConnectivityMonitor(initial: Boolean) : ConnectivityMonitor {
        val online = MutableStateFlow(initial)
        override val isOnline = online
        override fun isCurrentlyOnline() = online.value
    }

    private companion object {
        val CATALOG = CatalogSnapshot(
            artists = emptyList(),
            albums = emptyList(),
            tracks = listOf(track("track-1", "Midnight Signal")),
            lastSyncedAtEpochMillis = 42L,
        )

        fun track(id: String, title: String) = Track(
            id = id,
            title = title,
            artist = "Rakyzu Sessions",
            durationMs = 180_000L,
        )
    }
}
