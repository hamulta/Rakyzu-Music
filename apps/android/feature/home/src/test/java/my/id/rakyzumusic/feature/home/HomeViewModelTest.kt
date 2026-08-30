package my.id.rakyzumusic.feature.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import my.id.rakyzumusic.core.data.catalog.CatalogRefreshFailure
import my.id.rakyzumusic.core.data.catalog.CatalogRefreshResult
import my.id.rakyzumusic.core.data.catalog.CatalogRepository
import my.id.rakyzumusic.core.model.Album
import my.id.rakyzumusic.core.model.Artist
import my.id.rakyzumusic.core.model.CatalogSnapshot
import my.id.rakyzumusic.core.model.HomeFeedSnapshot
import my.id.rakyzumusic.core.model.Track
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
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
    fun initialRefreshPublishesCatalogFromLocalSource() = runTest(dispatcher) {
        val repository = FakeCatalogRepository(refreshResult = CatalogRefreshResult.Success(42L))
        val viewModel = HomeViewModel("listener-1", repository)
        repository.catalog.value = CATALOG
        testScheduler.advanceUntilIdle()

        assertEquals("Midnight Signal", viewModel.uiState.value.catalog.tracks.single().title)
        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals(null, viewModel.uiState.value.refreshMessage)
    }

    @Test
    fun failedRefreshKeepsCacheAndMarksSavedCatalog() = runTest(dispatcher) {
        val repository = FakeCatalogRepository(
            initial = CATALOG.copy(lastSyncedAtEpochMillis = NOW - (25L * 60L * 60L * 1_000L)),
            refreshResult = CatalogRefreshResult.Failure(CatalogRefreshFailure.NetworkUnavailable),
        )
        val viewModel = HomeViewModel("listener-1", repository, currentTimeMillis = { NOW })
        testScheduler.advanceUntilIdle()

        assertEquals(repository.catalog.value, viewModel.uiState.value.catalog)
        assertTrue(viewModel.uiState.value.isShowingSavedCatalog)
        assertTrue(viewModel.uiState.value.isShowingStaleSavedCatalog)
        assertEquals("Updated 1 day ago", viewModel.uiState.value.catalogFreshness.label)
        assertTrue(viewModel.uiState.value.refreshMessage?.contains("offline") == true)
        assertFalse(viewModel.uiState.value.isEmptyAfterRefresh)
    }

    @Test
    fun recentSavedCatalogIsNotMarkedStaleAfterRefreshFailure() = runTest(dispatcher) {
        val repository = FakeCatalogRepository(
            initial = CATALOG.copy(lastSyncedAtEpochMillis = NOW - (3L * 60L * 60L * 1_000L)),
            refreshResult = CatalogRefreshResult.Failure(CatalogRefreshFailure.ServiceUnavailable),
        )
        val viewModel = HomeViewModel("listener-1", repository, currentTimeMillis = { NOW })
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isShowingSavedCatalog)
        assertFalse(viewModel.uiState.value.isShowingStaleSavedCatalog)
        assertEquals("Updated 3 hours ago", viewModel.uiState.value.catalogFreshness.label)
    }

    @Test
    fun catalogFreshnessHandlesUnknownAndFutureTimestamps() {
        assertEquals("Update time unavailable", null.toCatalogFreshness(NOW).label)
        assertEquals("Updated just now", (NOW + 60_000L).toCatalogFreshness(NOW).label)
    }

    @Test
    fun successfulEmptyRefreshPublishesExplicitEmptyState() = runTest(dispatcher) {
        val repository = FakeCatalogRepository(refreshResult = CatalogRefreshResult.Success(42L))

        val viewModel = HomeViewModel("listener-1", repository)
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRefreshing)
        assertFalse(viewModel.uiState.value.hasPlayableContent)
        assertTrue(viewModel.uiState.value.isEmptyAfterRefresh)
    }

    @Test
    fun refreshWhileRequestIsRunningIsIgnored() = runTest(dispatcher) {
        val repository = FakeCatalogRepository(refreshResult = CatalogRefreshResult.Success(42L))

        val viewModel = HomeViewModel("listener-1", repository)
        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        assertEquals(1, repository.refreshCalls)
    }

    @Test
    fun homeFeedPublishesRecentlyPlayedForRequestedListener() = runTest(dispatcher) {
        val recent = CATALOG.tracks.single().copy(title = "Played most recently")
        val repository = FakeCatalogRepository(
            initial = CATALOG,
            recentlyPlayed = listOf(recent),
            refreshResult = CatalogRefreshResult.Success(42L),
        )

        val viewModel = HomeViewModel("listener-73", repository)
        testScheduler.advanceUntilIdle()

        assertEquals("listener-73", repository.observedUserId)
        assertEquals(listOf(recent), viewModel.uiState.value.recentlyPlayed)
    }

    @Test
    fun equivalentFeedReusesCatalogAndDerivedSectionReferences() {
        val recent = CATALOG.tracks.single().copy(title = "Played most recently")
        val state = HomeUiState(
            catalog = CATALOG,
            recentlyPlayed = listOf(recent),
            isRefreshing = false,
        )

        val updated = state.withHomeFeed(
            feed = HomeFeedSnapshot(
                catalog = CATALOG.copy(
                    artists = CATALOG.artists.toList(),
                    albums = CATALOG.albums.toList(),
                    tracks = CATALOG.tracks.toList(),
                ),
                recentlyPlayed = listOf(recent.copy()),
            ),
            currentTimeMillis = NOW,
        )

        assertSame(state.catalog, updated.catalog)
        assertSame(state.recentlyPlayed, updated.recentlyPlayed)
        assertSame(state.derivedSections, updated.derivedSections)
    }

    @Test
    fun changedCatalogRebuildsDerivedSectionsInReleaseOrder() {
        val olderTrack = CATALOG.tracks.single()
        val newerTrack = olderTrack.copy(
            id = "track-2",
            title = "Tomorrow's Signal",
            albumId = "album-2",
            albumTitle = "Signal One",
        )
        val changedCatalog = CATALOG.copy(
            albums = CATALOG.albums + Album(
                id = "album-2",
                artistId = "artist-1",
                title = "Signal One",
                releaseDate = "2026-08-30",
            ),
            tracks = CATALOG.tracks + newerTrack,
        )
        val state = HomeUiState(catalog = CATALOG, isRefreshing = false)

        val updated = state.withHomeFeed(
            feed = HomeFeedSnapshot(changedCatalog, emptyList()),
            currentTimeMillis = NOW,
        )

        assertSame(changedCatalog, updated.catalog)
        assertNotSame(state.derivedSections, updated.derivedSections)
        assertEquals(
            listOf("track-2", "track-1"),
            updated.derivedSections.newReleaseTracks.map(Track::id),
        )
        assertEquals(
            listOf("track-1", "track-2"),
            updated.derivedSections.featuredQueue.map(Track::id),
        )
    }

    @Test
    fun recentlyPlayedOnlyUpdateKeepsCatalogSectionsStable() {
        val recent = listOf(CATALOG.tracks.single())
        val state = HomeUiState(catalog = CATALOG, isRefreshing = false)

        val updated = state.withHomeFeed(
            feed = HomeFeedSnapshot(CATALOG.copy(), recent),
            currentTimeMillis = NOW,
        )

        assertSame(state.catalog, updated.catalog)
        assertSame(state.derivedSections, updated.derivedSections)
        assertSame(recent, updated.recentlyPlayed)
    }

    @Test
    fun playActionLabelUsesTrimmedTrackTitle() {
        val track = CATALOG.tracks.single().copy(title = "  Midnight Signal  ")

        assertEquals("Play Midnight Signal", track.homePlayActionLabel())
    }

    @Test
    fun homeSubtitleAvoidsEmptyMetadataSeparators() {
        val track = CATALOG.tracks.single()

        assertEquals("Rakyzu Sessions · Signal Zero", track.homeSubtitle())
        assertEquals("Signal Zero", track.copy(artist = "  ").homeSubtitle())
        assertEquals("Rakyzu Sessions", track.copy(albumTitle = "").homeSubtitle())
    }

    private class FakeCatalogRepository(
        initial: CatalogSnapshot = EMPTY,
        private val recentlyPlayed: List<Track> = emptyList(),
        private val refreshResult: CatalogRefreshResult,
    ) : CatalogRepository {
        val catalog = MutableStateFlow(initial)
        var observedUserId: String? = null
        var refreshCalls: Int = 0

        override fun observeCatalog(): Flow<CatalogSnapshot> = catalog

        override fun observeHomeFeed(userId: String): Flow<HomeFeedSnapshot> = catalog.map {
            observedUserId = userId
            HomeFeedSnapshot(catalog = it, recentlyPlayed = recentlyPlayed)
        }

        override suspend fun refresh(): CatalogRefreshResult {
            refreshCalls += 1
            return refreshResult
        }

        override suspend fun recordRecentlyPlayed(userId: String, trackId: String) = false
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        val EMPTY = CatalogSnapshot(emptyList(), emptyList(), emptyList(), null)
        val CATALOG = CatalogSnapshot(
            artists = listOf(Artist("artist-1", "Rakyzu Sessions")),
            albums = listOf(Album("album-1", "artist-1", "Signal Zero", "2026-08-29")),
            tracks = listOf(
                Track(
                    id = "track-1",
                    title = "Midnight Signal",
                    artist = "Rakyzu Sessions",
                    durationMs = 185_900L,
                    artistId = "artist-1",
                    albumId = "album-1",
                    albumTitle = "Signal Zero",
                ),
            ),
            lastSyncedAtEpochMillis = 42L,
        )
    }
}
