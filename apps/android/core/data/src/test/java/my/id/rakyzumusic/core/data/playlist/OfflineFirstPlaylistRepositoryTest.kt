package my.id.rakyzumusic.core.data.playlist

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import my.id.rakyzumusic.core.database.catalog.PlaylistLocalDataSource
import my.id.rakyzumusic.core.model.PlaylistSnapshot
import my.id.rakyzumusic.core.model.PlaylistSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineFirstPlaylistRepositoryTest {
    @Test
    fun refreshReplacesOnlyRequestedAccountCache() = runTest {
        val local = FakeLocalDataSource()
        val remote = FakeRemoteDataSource(listOf(PLAYLIST))
        val repository = OfflineFirstPlaylistRepository(
            localDataSource = local,
            remoteDataSource = remote,
            currentTimeMillis = { 2_000L },
        )

        val result = repository.refresh("listener-1")

        assertEquals(PlaylistActionResult.Success(), result)
        assertEquals("listener-1", local.replacedUserId)
        assertEquals(listOf(PLAYLIST), local.snapshot.value.playlists)
        assertEquals(2_000L, local.snapshot.value.lastSyncedAtEpochMillis)
    }

    @Test
    fun createNormalizesMetadataAndPersistsVerifiedResponse() = runTest {
        val local = FakeLocalDataSource()
        val remote = FakeRemoteDataSource(emptyList())
        val repository = OfflineFirstPlaylistRepository(
            localDataSource = local,
            remoteDataSource = remote,
            idFactory = { PLAYLIST.id },
        )

        val result = repository.create("listener-1", "  Road   Trip ", "  Coast  ")

        assertTrue(result is PlaylistActionResult.Success)
        assertEquals("Road Trip", remote.createdName)
        assertEquals("Coast", remote.createdDescription)
        assertEquals(PLAYLIST, local.upserted)
    }

    @Test
    fun invalidInputNeverCrossesRemoteBoundary() = runTest {
        val remote = FakeRemoteDataSource(emptyList())
        val repository = OfflineFirstPlaylistRepository(FakeLocalDataSource(), remote)

        val result = repository.create("listener-1", "   ", "")

        assertEquals(
            PlaylistActionResult.Failure(PlaylistFailure.InvalidRequest),
            result,
        )
        assertNull(remote.createdName)
    }

    @Test
    fun malformedRefreshDoesNotReplaceSavedCache() = runTest {
        val malformed = PLAYLIST.copy(revision = 0)
        val local = FakeLocalDataSource()
        val repository = OfflineFirstPlaylistRepository(
            localDataSource = local,
            remoteDataSource = FakeRemoteDataSource(listOf(malformed)),
        )

        val result = repository.refresh("listener-1")

        assertEquals(
            PlaylistActionResult.Failure(PlaylistFailure.InvalidPayload),
            result,
        )
        assertNull(local.replacedUserId)
    }

    private class FakeLocalDataSource : PlaylistLocalDataSource {
        val snapshot = MutableStateFlow(PlaylistSnapshot(emptyList(), null))
        var replacedUserId: String? = null
        var upserted: PlaylistSummary? = null

        override fun observePlaylists(userId: String): Flow<PlaylistSnapshot> = snapshot

        override suspend fun replacePlaylists(
            userId: String,
            playlists: List<PlaylistSummary>,
            syncedAtEpochMillis: Long,
        ) {
            replacedUserId = userId
            snapshot.value = PlaylistSnapshot(playlists, syncedAtEpochMillis)
        }

        override suspend fun upsertPlaylist(userId: String, playlist: PlaylistSummary) {
            upserted = playlist
            snapshot.value = snapshot.value.copy(playlists = listOf(playlist))
        }
    }

    private class FakeRemoteDataSource(
        private val playlists: List<PlaylistSummary>,
    ) : PlaylistRemoteDataSource {
        var createdName: String? = null
        var createdDescription: String? = null

        override suspend fun getMine(limit: Int) = playlists

        override suspend fun create(id: String, name: String, description: String): PlaylistSummary {
            createdName = name
            createdDescription = description
            return PLAYLIST.copy(id = id, name = name, description = description)
        }
    }

    private companion object {
        val PLAYLIST = PlaylistSummary(
            id = "90000000-0000-4000-8000-000000000001",
            name = "Road Trip",
            description = "Coast",
            trackCount = 0,
            revision = 1,
            createdAtEpochMillis = 1_000L,
            updatedAtEpochMillis = 1_000L,
        )
    }
}
