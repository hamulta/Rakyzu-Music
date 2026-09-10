package my.id.rakyzumusic.core.data.playlist

import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import my.id.rakyzumusic.core.database.catalog.PlaylistLocalDataSource
import my.id.rakyzumusic.core.database.catalog.StoredPlaylistMutation
import my.id.rakyzumusic.core.model.PlaylistDetail
import my.id.rakyzumusic.core.model.PlaylistItem
import my.id.rakyzumusic.core.model.PlaylistSnapshot
import my.id.rakyzumusic.core.model.PlaylistSummary
import my.id.rakyzumusic.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistResilienceRepositoryTest {
    @Test fun networkMutationQueuesAndRetriesWithTheSameOperationId() = runTest {
        val local = Local()
        val remote = Remote(FULL)
        val repository = OfflineFirstPlaylistRepository(
            local,
            remote,
            operationIdFactory = { OPERATION_ID },
        )
        repository.refreshDetail(USER, PLAYLIST_ID)
        remote.failMutation = true
        assertEquals(
            PlaylistActionResult.Queued(1),
            repository.mutate(USER, PLAYLIST_ID, 1, PlaylistMutation.Remove(TRACK_ONE.id)),
        )

        remote.failMutation = false
        remote.detail = FULL.copy(
            playlist = SUMMARY.copy(trackCount = 1, revision = 2),
            items = listOf(PlaylistItem(TRACK_TWO.id, TRACK_TWO)),
            totalItems = 1,
        )
        assertTrue(repository.retryPending(USER, PLAYLIST_ID) is PlaylistActionResult.Success)
        assertEquals(listOf(OPERATION_ID, OPERATION_ID), remote.operationIds)
        assertEquals(0, local.pendingMutationCount(USER, PLAYLIST_ID))
    }

    @Test fun boundedDetailPagesMergeWithoutChangingServerOrder() = runTest {
        val local = Local()
        val first = FULL.copy(items = FULL.items.take(1), nextOffset = 1)
        val second = FULL.copy(items = FULL.items.drop(1), nextOffset = null)
        val remote = Remote(first).apply { detailPages = mapOf(0 to first, 1 to second) }
        val repository = OfflineFirstPlaylistRepository(local, remote)

        repository.refreshDetail(USER, PLAYLIST_ID)
        assertEquals(1, repository.observeDetail(USER, PLAYLIST_ID).first()!!.items.size)
        repository.loadMoreDetail(USER, PLAYLIST_ID)
        val merged = repository.observeDetail(USER, PLAYLIST_ID).first()!!
        assertEquals(listOf(TRACK_ONE.id, TRACK_TWO.id), merged.items.map { it.trackId })
        assertEquals(null, merged.nextOffset)
    }

    @Test fun playlistCursorAppendsTheNextBoundedPage() = runTest {
        val first = SUMMARY.copy(id = PLAYLIST_ID, updatedAtEpochMillis = 2_000)
        val second = SUMMARY.copy(
            id = "90000000-0000-4000-8000-000000000002",
            updatedAtEpochMillis = 1_000,
        )
        val local = Local()
        val remote = Remote(FULL).apply {
            listPages = listOf(
                PlaylistRemotePage(listOf(first), true),
                PlaylistRemotePage(listOf(second), false),
            )
        }
        val repository = OfflineFirstPlaylistRepository(local, remote)
        repository.refresh(USER)
        assertTrue(repository.observePlaylists(USER).first().hasMore)
        repository.loadMore(USER)
        val snapshot = repository.observePlaylists(USER).first()
        assertEquals(listOf(first, second), snapshot.playlists)
        assertFalse(snapshot.hasMore)
    }

    private class Local : PlaylistLocalDataSource {
        private val details = mutableMapOf<Pair<String, String>, MutableStateFlow<String?>>()
        private val lists = mutableMapOf<String, MutableStateFlow<PlaylistSnapshot>>()
        private val outbox = mutableListOf<StoredPlaylistMutation>()

        override fun observeDetailPayload(userId: String, playlistId: String) =
            details.getOrPut(userId to playlistId) { MutableStateFlow(null) }
        override suspend fun readDetailPayload(userId: String, playlistId: String) =
            observeDetailPayload(userId, playlistId).value
        override suspend fun storeDetailPayload(
            userId: String,
            playlist: PlaylistSummary,
            payload: String,
        ) {
            observeDetailPayload(userId, playlist.id).value = payload
        }
        override fun observePlaylists(userId: String) =
            lists.getOrPut(userId) { MutableStateFlow(PlaylistSnapshot(emptyList(), null)) }
        override suspend fun replacePlaylists(
            userId: String,
            playlists: List<PlaylistSummary>,
            syncedAtEpochMillis: Long,
        ) {
            observePlaylists(userId).value = PlaylistSnapshot(playlists, syncedAtEpochMillis)
        }
        override suspend fun appendPlaylists(userId: String, playlists: List<PlaylistSummary>) {
            val current = observePlaylists(userId).value
            observePlaylists(userId).value = current.copy(playlists = current.playlists + playlists)
        }
        override suspend fun upsertPlaylist(userId: String, playlist: PlaylistSummary) = Unit
        override suspend fun enqueueMutation(
            userId: String,
            playlistId: String,
            operationId: String,
            expectedRevision: Long,
            payload: String,
            queuedAtEpochMillis: Long,
        ) {
            outbox += StoredPlaylistMutation(operationId, expectedRevision, payload, queuedAtEpochMillis, 0)
        }
        override suspend fun pendingMutations(userId: String, playlistId: String, limit: Int) =
            outbox.take(limit)
        override suspend fun pendingMutationCount(userId: String, playlistId: String) = outbox.size
        override suspend fun acknowledgeMutation(userId: String, operationId: String) {
            outbox.removeAll { it.operationId == operationId }
        }
    }

    private class Remote(var detail: PlaylistDetail) : PlaylistRemoteDataSource {
        var failMutation = false
        var detailPages: Map<Int, PlaylistDetail> = emptyMap()
        var listPages: List<PlaylistRemotePage> = emptyList()
        val operationIds = mutableListOf<String>()
        private var listCall = 0

        override suspend fun detail(id: String) = detail
        override suspend fun detailPage(id: String, offset: Int, limit: Int) =
            detailPages[offset] ?: detail
        override suspend fun mutate(id: String, revision: Long, mutation: PlaylistMutation) = detail
        override suspend fun mutateIdempotent(
            id: String,
            operationId: String,
            revision: Long,
            mutation: PlaylistMutation,
        ): PlaylistDetail {
            operationIds += operationId
            if (failMutation) throw IOException("offline")
            return detail
        }
        override suspend fun getMine(limit: Int) = listOf(detail.playlist)
        override suspend fun getPage(
            limit: Int,
            beforeUpdatedAtEpochMillis: Long?,
            beforeId: String?,
        ) = listPages.getOrElse(listCall++) { PlaylistRemotePage(listOf(detail.playlist), false) }
        override suspend fun create(id: String, name: String, description: String) = detail.playlist
    }

    private companion object {
        const val USER = "listener"
        const val PLAYLIST_ID = "90000000-0000-4000-8000-000000000001"
        const val OPERATION_ID = "92000000-0000-4000-8000-000000000001"
        val TRACK_ONE = Track(
            "a3000000-0000-4000-8000-000000000001", "One", "Artist", 2_000,
            "a1000000-0000-4000-8000-000000000001",
            "a2000000-0000-4000-8000-000000000001",
        )
        val TRACK_TWO = TRACK_ONE.copy(
            id = "a3000000-0000-4000-8000-000000000002",
            title = "Two",
        )
        val SUMMARY = PlaylistSummary(PLAYLIST_ID, "Mix", "", 2, 1, 1_000, 1_000)
        val FULL = PlaylistDetail(
            SUMMARY,
            listOf(PlaylistItem(TRACK_ONE.id, TRACK_ONE), PlaylistItem(TRACK_TWO.id, TRACK_TWO)),
        )
    }
}
