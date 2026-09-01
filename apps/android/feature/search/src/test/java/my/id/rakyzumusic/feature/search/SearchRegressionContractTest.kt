package my.id.rakyzumusic.feature.search

import my.id.rakyzumusic.core.model.CatalogSnapshot
import my.id.rakyzumusic.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRegressionContractTest {
    @Test(timeout = 4_000L)
    fun cachedIndexKeepsRepeatedLargeCatalogSearchBoundedAndDeterministic() {
        val tracks = List(10_000) { index ->
            track("track-$index", "Signal ${index.toString().padStart(5, '0')}")
        }
        val index = SearchCatalogIndex.from(catalog(tracks))

        repeat(40) {
            val results = index.search("signal")
            assertEquals(10_000, results.totalTrackMatches)
            assertEquals(MAX_RESULTS_PER_TYPE, results.tracks.size)
            assertEquals("track-0", results.tracks.first().id)
            assertEquals("track-49", results.tracks.last().id)
        }
    }

    @Test
    fun paginationStateRequiresLoadedOnlineIdleRequest() {
        val base = SearchUiState(
            query = "signal",
            catalog = catalog(listOf(track("track-1", "Signal"))),
            hasObservedCatalog = true,
            remoteSearchStatus = RemoteSearchStatus.Loaded,
            nextRemoteOffset = 30,
        )

        assertTrue(base.canLoadMore)
        assertFalse(base.copy(isOnline = false).canLoadMore)
        assertFalse(base.copy(isLoadingMore = true).canLoadMore)
        assertFalse(base.copy(remoteSearchStatus = RemoteSearchStatus.Failed).canLoadMore)
        assertFalse(base.copy(nextRemoteOffset = null).canLoadMore)
    }

    @Test
    fun offlineAndFailedRemoteStatesKeepSavedMatchesOutOfEmptyState() {
        val result = track("track-1", "Midnight Signal")
        val base = SearchUiState(
            query = "signal",
            catalog = catalog(listOf(result)),
            results = SearchResults(tracks = listOf(result), totalTrackMatches = 1),
            hasObservedCatalog = true,
        )

        assertFalse(base.copy(remoteSearchStatus = RemoteSearchStatus.Offline).hasNoResults)
        assertFalse(base.copy(remoteSearchStatus = RemoteSearchStatus.Failed).hasNoResults)
    }

    private fun catalog(tracks: List<Track>) = CatalogSnapshot(
        artists = emptyList(),
        albums = emptyList(),
        tracks = tracks,
        lastSyncedAtEpochMillis = 42L,
    )

    private fun track(id: String, title: String) = Track(
        id = id,
        title = title,
        artist = "Rakyzu Sessions",
        durationMs = 180_000L,
    )
}
