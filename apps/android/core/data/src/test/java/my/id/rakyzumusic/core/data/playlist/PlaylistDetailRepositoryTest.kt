package my.id.rakyzumusic.core.data.playlist

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import my.id.rakyzumusic.core.database.catalog.PlaylistLocalDataSource
import my.id.rakyzumusic.core.model.PlaylistDetail
import my.id.rakyzumusic.core.model.PlaylistItem
import my.id.rakyzumusic.core.model.PlaylistSnapshot
import my.id.rakyzumusic.core.model.PlaylistSummary
import my.id.rakyzumusic.core.model.Track
import org.junit.Assert.*
import org.junit.Test

class PlaylistDetailRepositoryTest {
    @Test fun detailPreservesServerOrderAcrossOfflineReads() = runTest {
        val local = Local()
        val remote = Remote(DETAIL)
        val repository = OfflineFirstPlaylistRepository(local, remote)
        assertTrue(repository.refreshDetail("owner", ID) is PlaylistActionResult.Success)
        remote.failure = IllegalStateException()
        assertTrue(repository.refreshDetail("owner", ID) is PlaylistActionResult.Failure)
        assertEquals(DETAIL, repository.observeDetail("owner", ID).first())
        assertNull(repository.observeDetail("other", ID).first())
    }

    @Test fun malformedCountAndDuplicatesDoNotReplaceSnapshot() = runTest {
        val local = Local()
        val remote = Remote(DETAIL)
        val repository = OfflineFirstPlaylistRepository(local, remote)
        repository.refreshDetail("owner", ID)
        for (bad in listOf(DETAIL.copy(playlist = SUMMARY.copy(trackCount = 9)),
            DETAIL.copy(items = listOf(DETAIL.items[0], DETAIL.items[0])))) {
            remote.value = bad
            assertEquals(PlaylistActionResult.Failure(PlaylistFailure.InvalidPayload), repository.refreshDetail("owner", ID))
            assertEquals(DETAIL, repository.observeDetail("owner", ID).first())
        }
    }

    @Test fun invalidMutationNeverCallsServer() = runTest {
        val remote = Remote(DETAIL)
        val repository = OfflineFirstPlaylistRepository(Local(), remote)
        for (mutation in listOf(PlaylistMutation.Add("bad"), PlaylistMutation.Reorder(listOf(TRACK.id, TRACK.id)),
            PlaylistMutation.Metadata(" ", ""))) {
            assertEquals(PlaylistActionResult.Failure(PlaylistFailure.InvalidRequest), repository.mutate("owner", ID, 1, mutation))
        }
        assertEquals(0, remote.mutations)
    }

    @Test fun successfulMutationPersistsSummaryAndDetailTogether() = runTest {
        val local = Local()
        val updated = DETAIL.copy(playlist = SUMMARY.copy(revision = 2), items = DETAIL.items.reversed())
        val remote = Remote(updated)
        val repository = OfflineFirstPlaylistRepository(local, remote)
        val intent = PlaylistMutation.Reorder(updated.items.map { it.trackId })
        assertTrue(repository.mutate("owner", ID, 1, intent) is PlaylistActionResult.Success)
        assertEquals(updated, repository.observeDetail("owner", ID).first())
        assertEquals(updated.playlist, local.summary)
        assertEquals(1L, remote.expectedRevision)
    }

    @Test fun accountSwitchDuringResponseCannotPoisonPreviousUsersCache() = runTest {
        var currentUser = "owner"
        val local = Local()
        val remote = Remote(DETAIL).apply { onRead = { currentUser = "other" } }
        val repository = OfflineFirstPlaylistRepository(local, remote, activeUserId = { currentUser })
        assertEquals(PlaylistActionResult.Failure(PlaylistFailure.InvalidRequest), repository.refreshDetail("owner", ID))
        assertNull(repository.observeDetail("owner", ID).first())
        assertNull(local.summary)
    }

    @Test fun cancellationIsNotConvertedIntoServiceFailure() = runTest {
        val repository = OfflineFirstPlaylistRepository(Local(), Remote(DETAIL).apply { failure = CancellationException() })
        var cancelled = false
        try { repository.refreshDetail("owner", ID) } catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
    }

    @Test fun unavailableTrackKeepsPositionButIsExcludedFromQueue() = runTest {
        val detail = DETAIL.copy(items = listOf(DETAIL.items[0].copy(track = null), DETAIL.items[1]))
        val repository = OfflineFirstPlaylistRepository(Local(), Remote(detail))
        assertTrue(repository.refreshDetail("owner", ID) is PlaylistActionResult.Success)
        val saved = repository.observeDetail("owner", ID).first()!!
        assertEquals(2, saved.items.size)
        assertEquals(listOf(DETAIL.items[1].track), saved.playableTracks)
    }

    private class Local : PlaylistLocalDataSource {
        private val details = mutableMapOf<Pair<String, String>, MutableStateFlow<String?>>()
        var summary: PlaylistSummary? = null
        override fun observeDetailPayload(userId: String, playlistId: String) =
            details.getOrPut(userId to playlistId) { MutableStateFlow(null) }
        override suspend fun storeDetailPayload(userId: String, playlist: PlaylistSummary, payload: String) {
            summary = playlist
            observeDetailPayload(userId, playlist.id).value = payload
        }
        override fun observePlaylists(userId: String) = MutableStateFlow(PlaylistSnapshot(emptyList(), null))
        override suspend fun replacePlaylists(userId: String, playlists: List<PlaylistSummary>, syncedAtEpochMillis: Long) = Unit
        override suspend fun upsertPlaylist(userId: String, playlist: PlaylistSummary) = Unit
    }

    private class Remote(var value: PlaylistDetail) : PlaylistRemoteDataSource {
        var failure: Exception? = null
        var onRead: () -> Unit = {}
        var mutations = 0
        var expectedRevision = 0L
        override suspend fun detail(id: String): PlaylistDetail {
            failure?.let { throw it }; onRead(); return value
        }
        override suspend fun mutate(id: String, revision: Long, mutation: PlaylistMutation): PlaylistDetail {
            mutations++; expectedRevision = revision; return detail(id)
        }
        override suspend fun getMine(limit: Int) = listOf(value.playlist)
        override suspend fun create(id: String, name: String, description: String) = value.playlist
    }

    companion object {
        private const val ID = "90000000-0000-4000-8000-000000000001"
        private val SUMMARY = PlaylistSummary(ID, "Mix", "", 2, 1, 1000, 1000)
        private val TRACK = Track("a3000000-0000-4000-8000-000000000001", "One", "Artist", 2000,
            "a1000000-0000-4000-8000-000000000001", "a2000000-0000-4000-8000-000000000001")
        private val SECOND = TRACK.copy(id = "a3000000-0000-4000-8000-000000000002", title = "Two")
        private val DETAIL = PlaylistDetail(SUMMARY, listOf(PlaylistItem(TRACK.id, TRACK), PlaylistItem(SECOND.id, SECOND)))
    }
}
