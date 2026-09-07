package my.id.rakyzumusic.core.data.library

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import my.id.rakyzumusic.core.database.catalog.LibraryLocalDataSource
import my.id.rakyzumusic.core.database.catalog.StoredLibrarySelection
import my.id.rakyzumusic.core.model.LibraryItemKind
import my.id.rakyzumusic.core.model.LibrarySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class OfflineFirstLibraryRepositoryTest {
    @Test
    fun refreshReplacesOnlyCurrentListenersCache() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote(
            listOf(
                RemoteLibrarySelection(LibraryItemKind.Track, "track-1", 40L),
                RemoteLibrarySelection(LibraryItemKind.Album, "album-1", 30L),
            ),
        )
        val repository = OfflineFirstLibraryRepository(local, remote) { 50L }

        assertEquals(LibraryActionResult.Success, repository.refresh("listener-1"))
        assertEquals("listener-1", local.replacedUserId)
        assertEquals(50L, local.replacedAt)
        assertEquals(listOf("track-1", "album-1"), local.replaced.map { it.itemId })
    }

    @Test
    fun duplicateRemoteItemFailsClosedWithoutReplacingCache() = runTest {
        val local = FakeLocal()
        val duplicate = RemoteLibrarySelection(LibraryItemKind.Track, "track-1", 40L)
        val repository = OfflineFirstLibraryRepository(
            local,
            FakeRemote(listOf(duplicate, duplicate)),
        )

        assertEquals(
            LibraryActionResult.Failure(LibraryFailure.InvalidPayload),
            repository.refresh("listener-1"),
        )
        assertEquals(null, local.replacedUserId)
    }

    @Test
    fun successfulMutationUpdatesLocalCacheAfterRemoteConfirmation() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote(emptyList())
        val repository = OfflineFirstLibraryRepository(local, remote) { 73L }

        assertEquals(
            LibraryActionResult.Success,
            repository.setSaved(
                "listener-1",
                LibraryItemKind.Artist,
                "artist-1",
                true,
            ),
        )

        assertEquals(
            StoredLibrarySelection(LibraryItemKind.Artist, "artist-1", 73L),
            local.lastSelection,
        )
        assertEquals(true, local.lastSaved)
        assertEquals(LibraryItemKind.Artist, remote.lastKind)
    }

    @Test
    fun rejectedMutationDoesNotAlterLocalCache() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote(emptyList(), acceptsMutation = false)
        val repository = OfflineFirstLibraryRepository(local, remote)

        assertEquals(
            LibraryActionResult.Failure(LibraryFailure.InvalidPayload),
            repository.setSaved("listener-1", LibraryItemKind.Track, "track-1", true),
        )
        assertEquals(null, local.lastSelection)
    }

    @Test
    fun remoteFailurePreservesLastKnownLibrary() = runTest {
        val cached = EMPTY_LIBRARY.copy(lastSyncedAtEpochMillis = 21L)
        val local = FakeLocal(cached)
        val repository = OfflineFirstLibraryRepository(
            local,
            object : LibraryRemoteDataSource {
                override suspend fun fetchLibrary(): List<RemoteLibrarySelection> =
                    error("service unavailable")
                override suspend fun setSaved(
                    kind: LibraryItemKind,
                    itemId: String,
                    saved: Boolean,
                ) = error("service unavailable")
            },
        )

        assertEquals(
            LibraryActionResult.Failure(LibraryFailure.ServiceUnavailable),
            repository.refresh("listener-1"),
        )
        assertSame(cached, local.state.value)
    }

    private class FakeLocal(initial: LibrarySnapshot = EMPTY_LIBRARY) : LibraryLocalDataSource {
        val state = MutableStateFlow(initial)
        var replacedUserId: String? = null
        var replaced: List<StoredLibrarySelection> = emptyList()
        var replacedAt: Long? = null
        var lastSelection: StoredLibrarySelection? = null
        var lastSaved: Boolean? = null

        override fun observeLibrary(userId: String): Flow<LibrarySnapshot> = state

        override suspend fun replaceLibrary(
            userId: String,
            selections: List<StoredLibrarySelection>,
            syncedAtEpochMillis: Long,
        ) {
            replacedUserId = userId
            replaced = selections
            replacedAt = syncedAtEpochMillis
        }

        override suspend fun setLibraryItem(
            userId: String,
            selection: StoredLibrarySelection,
            saved: Boolean,
        ) {
            lastSelection = selection
            lastSaved = saved
        }
    }

    private class FakeRemote(
        private val selections: List<RemoteLibrarySelection>,
        private val acceptsMutation: Boolean = true,
    ) : LibraryRemoteDataSource {
        var lastKind: LibraryItemKind? = null

        override suspend fun fetchLibrary() = selections

        override suspend fun setSaved(
            kind: LibraryItemKind,
            itemId: String,
            saved: Boolean,
        ): Boolean {
            lastKind = kind
            return acceptsMutation
        }
    }

    private companion object {
        val EMPTY_LIBRARY = LibrarySnapshot(emptyList(), emptyList(), emptyList(), null)
    }
}
