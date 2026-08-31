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
class ArtistDetailViewModelTest {
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
    fun exactArtistIdBuildsDeterministicReleasesAndTrackQueue() = runTest(dispatcher) {
        val state = CATALOG.artistDetailState(ARTIST.id)

        assertEquals(ARTIST, state.artist)
        assertEquals(listOf("album-new", "album-old"), state.releases.map { it.album.id })
        assertEquals(listOf(2, 1), state.releases.map(ArtistRelease::trackCount))
        assertEquals(
            listOf("track-new-1", "track-new-2", "track-old", "track-orphan"),
            state.tracks.map(Track::id),
        )
        assertTrue(state.isReady.not())
    }

    @Test
    fun unrelatedCatalogRowsNeverLeakIntoArtistDetail() {
        val state = CATALOG.artistDetailState(ARTIST.id)

        assertFalse(state.releases.any { it.album.artistId != ARTIST.id })
        assertFalse(state.tracks.any { it.artistId != ARTIST.id })
    }

    @Test
    fun missingArtistHasExplicitUnavailableStateAfterObservation() = runTest(dispatcher) {
        val viewModel = ArtistDetailViewModel("missing", FakeCatalogRepository(CATALOG))

        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasObservedCatalog)
        assertTrue(viewModel.uiState.value.isUnavailable)
        assertNull(viewModel.uiState.value.artist)
    }

    @Test
    fun catalogReplacementUpdatesExistingArtistWithoutRefresh() = runTest(dispatcher) {
        val repository = FakeCatalogRepository(CATALOG)
        val viewModel = ArtistDetailViewModel(ARTIST.id, repository)
        testScheduler.advanceUntilIdle()

        repository.catalog.value = CATALOG.copy(
            tracks = CATALOG.tracks + track(
                id = "track-latest",
                albumId = "album-new",
                disc = 1,
                number = 1,
            ),
        )
        testScheduler.advanceUntilIdle()

        assertEquals("track-latest", viewModel.uiState.value.tracks.first().id)
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
        val OTHER_ARTIST = Artist("artist-2", "Other Artist")
        val CATALOG = CatalogSnapshot(
            artists = listOf(ARTIST, OTHER_ARTIST),
            albums = listOf(
                Album("album-old", ARTIST.id, "First Signal", "2025-02-01"),
                Album("album-new", ARTIST.id, "Latest Signal", "2026-08-31"),
                Album("album-other", OTHER_ARTIST.id, "Other Album", "2027-01-01"),
            ),
            tracks = listOf(
                track("track-old", "album-old", disc = 1, number = 1),
                track("track-new-2", "album-new", disc = 1, number = 2),
                track("track-new-1", "album-new", disc = 1, number = 1),
                track("track-orphan", "missing-album", disc = 1, number = 1),
                Track(
                    id = "track-other",
                    title = "Other Track",
                    artist = OTHER_ARTIST.name,
                    durationMs = 180_000L,
                    artistId = OTHER_ARTIST.id,
                    albumId = "album-other",
                    albumTitle = "Other Album",
                ),
            ),
            lastSyncedAtEpochMillis = 42L,
        )

        fun track(
            id: String,
            albumId: String,
            disc: Int,
            number: Int,
        ) = Track(
            id = id,
            title = id,
            artist = ARTIST.name,
            durationMs = 180_000L,
            artistId = ARTIST.id,
            albumId = albumId,
            albumTitle = albumId,
            discNumber = disc,
            trackNumber = number,
        )
    }
}
