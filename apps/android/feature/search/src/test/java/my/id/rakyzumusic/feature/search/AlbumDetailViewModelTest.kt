package my.id.rakyzumusic.feature.search

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
import my.id.rakyzumusic.core.model.Album
import my.id.rakyzumusic.core.model.Artist
import my.id.rakyzumusic.core.model.CatalogSnapshot
import my.id.rakyzumusic.core.model.HomeFeedSnapshot
import my.id.rakyzumusic.core.model.Track
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumDetailViewModelTest {
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
    fun exactAlbumIdBuildsDiscAndTrackNumberQueue() {
        val state = CATALOG.albumDetailState(ALBUM.id)

        assertEquals(ALBUM, state.album)
        assertEquals(ARTIST, state.artist)
        assertEquals(
            listOf("disc-1-track-1", "disc-1-track-2", "disc-2-track-1"),
            state.tracks.map(Track::id),
        )
        assertEquals(2, state.discCount)
    }

    @Test
    fun unrelatedAlbumTracksNeverLeakIntoDetail() {
        val state = CATALOG.albumDetailState(ALBUM.id)

        assertFalse(state.tracks.any { it.albumId != ALBUM.id })
    }

    @Test
    fun missingAlbumHasExplicitUnavailableStateAfterObservation() = runTest(dispatcher) {
        val viewModel = AlbumDetailViewModel("missing", FakeCatalogRepository(CATALOG))

        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasObservedCatalog)
        assertTrue(viewModel.uiState.value.isUnavailable)
        assertNull(viewModel.uiState.value.album)
    }

    @Test
    fun catalogReplacementUpdatesAlbumQueueWithoutRefresh() = runTest(dispatcher) {
        val repository = FakeCatalogRepository(CATALOG)
        val viewModel = AlbumDetailViewModel(ALBUM.id, repository)
        testScheduler.advanceUntilIdle()

        repository.catalog.value = CATALOG.copy(
            tracks = CATALOG.tracks + track(
                id = "new-first-track",
                disc = 1,
                number = 1,
                title = "A New First Track",
            ),
        )
        testScheduler.advanceUntilIdle()

        assertEquals("new-first-track", viewModel.uiState.value.tracks.first().id)
        assertEquals(0, repository.refreshCalls)
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
        val ARTIST = Artist("artist-1", "Rakyzu Sessions")
        val ALBUM = Album("album-1", ARTIST.id, "Signal Zero", "2026-08-31")
        val OTHER_ALBUM = Album("album-2", ARTIST.id, "Other Signal", "2026-01-01")
        val CATALOG = CatalogSnapshot(
            artists = listOf(ARTIST),
            albums = listOf(ALBUM, OTHER_ALBUM),
            tracks = listOf(
                track("disc-2-track-1", disc = 2, number = 1),
                track("disc-1-track-2", disc = 1, number = 2),
                track("disc-1-track-1", disc = 1, number = 1),
                Track(
                    id = "other-album-track",
                    title = "Other Album Track",
                    artist = ARTIST.name,
                    durationMs = 180_000L,
                    artistId = ARTIST.id,
                    albumId = OTHER_ALBUM.id,
                    albumTitle = OTHER_ALBUM.title,
                ),
            ),
            lastSyncedAtEpochMillis = 42L,
        )

        fun track(
            id: String,
            disc: Int,
            number: Int,
            title: String = id,
        ) = Track(
            id = id,
            title = title,
            artist = ARTIST.name,
            durationMs = 180_000L,
            artistId = ARTIST.id,
            albumId = ALBUM.id,
            albumTitle = ALBUM.title,
            discNumber = disc,
            trackNumber = number,
        )
    }
}
