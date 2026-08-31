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
import my.id.rakyzumusic.core.model.CatalogSnapshot
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

    private class FakeCatalogRepository(initial: CatalogSnapshot) : CatalogRepository {
        val catalog = MutableStateFlow(initial)
        var refreshCalls = 0

        override fun observeCatalog() = catalog

        override fun observeHomeFeed(userId: String) = MutableStateFlow(
            HomeFeedSnapshot(catalog.value, emptyList()),
        )

        override suspend fun refresh(): CatalogRefreshResult {
            refreshCalls += 1
            return CatalogRefreshResult.Success(42L)
        }

        override suspend fun recordRecentlyPlayed(userId: String, trackId: String) = true
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
