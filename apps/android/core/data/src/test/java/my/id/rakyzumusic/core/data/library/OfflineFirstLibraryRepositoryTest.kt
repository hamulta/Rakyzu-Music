package my.id.rakyzumusic.core.data.library

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import my.id.rakyzumusic.core.database.catalog.LibraryLocalDataSource
import my.id.rakyzumusic.core.database.catalog.StoredLibraryMutation
import my.id.rakyzumusic.core.database.catalog.StoredLibrarySelection
import my.id.rakyzumusic.core.database.catalog.StoredRemoteLibraryChange
import my.id.rakyzumusic.core.model.LibraryItemKind
import my.id.rakyzumusic.core.model.LibrarySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineFirstLibraryRepositoryTest {
    @Test
    fun firstRefreshUsesBoundedPagesThenPersistsAnchorAndChanges() = runTest {
        val local = FakeLocal()
        val first = RemoteLibrarySelection(LibraryItemKind.Track, "track-1", 40L)
        val second = RemoteLibrarySelection(LibraryItemKind.Album, "album-1", 30L)
        val remote = FakeRemote(
            pages = ArrayDeque(
                listOf(
                    RemoteLibraryPage(
                        listOf(first),
                        RemoteLibraryCursor(40L, LibraryItemKind.Track, "track-1"),
                    ),
                    RemoteLibraryPage(listOf(second), null),
                ),
            ),
            changes = ArrayDeque(
                listOf(
                    RemoteLibraryChangePage(
                        listOf(
                            RemoteLibraryChange(
                                8L,
                                LibraryItemKind.Track,
                                "track-1",
                                false,
                                50L,
                            ),
                        ),
                        hasMore = false,
                    ),
                ),
            ),
            anchor = 7L,
        )

        assertEquals(
            LibraryActionResult.Success,
            OfflineFirstLibraryRepository(local, remote) { 60L }.refresh("listener-1"),
        )

        assertEquals(listOf("track-1", "album-1"), local.replaced.map { it.itemId })
        assertEquals(8L, local.cursor)
        assertEquals(listOf(false), local.appliedChanges.map { it.saved })
        assertEquals(listOf(50, 50), remote.requestedPageSizes)
    }

    @Test
    fun networkFailureKeepsOptimisticMutationInDurableOutbox() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote(mutationFailure = IllegalStateException("offline"))

        val result = OfflineFirstLibraryRepository(local, remote) { 73L }.setSaved(
            "listener-1",
            LibraryItemKind.Artist,
            "artist-1",
            true,
        )

        assertEquals(LibraryActionResult.Queued(1), result)
        assertEquals(true, local.lastSaved)
        assertEquals(1, local.pending.size)
        assertEquals(1, local.pending.single().attemptCount)
    }

    @Test
    fun latestOfflineIntentReplacesEarlierIntentForSameItem() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote(mutationFailure = IllegalStateException("offline"))
        var now = 10L
        val repository = OfflineFirstLibraryRepository(local, remote) { now++ }

        repository.setSaved("listener-1", LibraryItemKind.Track, "track-1", true)
        repository.setSaved("listener-1", LibraryItemKind.Track, "track-1", false)

        assertEquals(1, local.pending.size)
        assertEquals(false, local.pending.single().saved)
        assertEquals(false, local.lastSaved)
    }

    @Test
    fun existingCursorFetchesOnlyIncrementalChanges() = runTest {
        val local = FakeLocal().apply { cursor = 9L }
        val remote = FakeRemote(
            changes = ArrayDeque(
                listOf(
                    RemoteLibraryChangePage(
                        listOf(
                            RemoteLibraryChange(
                                10L,
                                LibraryItemKind.Album,
                                "album-1",
                                false,
                                80L,
                            ),
                        ),
                        false,
                    ),
                ),
            ),
        )

        assertEquals(
            LibraryActionResult.Success,
            OfflineFirstLibraryRepository(local, remote).refresh("listener-1"),
        )
        assertTrue(remote.pages.isNotEmpty())
        assertEquals(10L, local.cursor)
    }

    @Test
    fun rejectedMutationRollsBackOptimisticState() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote(acceptsMutation = false)

        assertEquals(
            LibraryActionResult.Failure(LibraryFailure.InvalidPayload),
            OfflineFirstLibraryRepository(local, remote) { 42L }.setSaved(
                "listener-1",
                LibraryItemKind.Album,
                "missing",
                true,
            ),
        )
        assertEquals(false, local.lastSaved)
        assertTrue(local.pending.isEmpty())
    }

    private class FakeLocal : LibraryLocalDataSource {
        private val state = MutableStateFlow(EMPTY_LIBRARY)
        val pending = mutableListOf<StoredLibraryMutation>()
        var cursor: Long? = null
        var replaced = emptyList<StoredLibrarySelection>()
        var lastSaved: Boolean? = null
        val appliedChanges = mutableListOf<StoredRemoteLibraryChange>()

        override fun observeLibrary(userId: String): Flow<LibrarySnapshot> = state

        override suspend fun replaceLibrary(
            userId: String,
            selections: List<StoredLibrarySelection>,
            syncedAtEpochMillis: Long,
        ) {
            replaced = selections
        }

        override suspend fun setLibraryItem(
            userId: String,
            selection: StoredLibrarySelection,
            saved: Boolean,
        ) {
            lastSaved = saved
        }

        override suspend fun enqueueLibraryMutation(
            userId: String,
            mutation: StoredLibraryMutation,
        ) {
            pending.removeAll { it.kind == mutation.kind && it.itemId == mutation.itemId }
            pending += mutation
            lastSaved = mutation.saved
        }

        override suspend fun getPendingLibraryMutations(
            userId: String,
            limit: Int,
        ): List<StoredLibraryMutation> = pending.take(limit)

        override suspend fun acknowledgeLibraryMutation(
            userId: String,
            mutation: StoredLibraryMutation,
        ): Boolean = pending.remove(mutation)

        override suspend fun countPendingLibraryMutations(userId: String): Int = pending.size

        override suspend fun recordLibraryMutationAttempt(
            userId: String,
            mutation: StoredLibraryMutation,
        ) {
            val index = pending.indexOfFirst {
                it.kind == mutation.kind && it.itemId == mutation.itemId &&
                    it.queuedAtEpochMillis == mutation.queuedAtEpochMillis
            }
            if (index >= 0) {
                pending[index] = pending[index].copy(attemptCount = mutation.attemptCount + 1)
            }
        }

        override suspend fun getLibraryChangeCursor(userId: String): Long? = cursor

        override suspend fun applyRemoteLibraryChanges(
            userId: String,
            changes: List<StoredRemoteLibraryChange>,
        ) {
            appliedChanges += changes
            cursor = changes.lastOrNull()?.sequence ?: cursor
        }

        override suspend fun setLibraryChangeCursor(userId: String, sequence: Long) {
            cursor = sequence
        }
    }

    private class FakeRemote(
        val pages: ArrayDeque<RemoteLibraryPage> = ArrayDeque(
            listOf(RemoteLibraryPage(emptyList(), null)),
        ),
        private val changes: ArrayDeque<RemoteLibraryChangePage> = ArrayDeque(
            listOf(RemoteLibraryChangePage(emptyList(), false)),
        ),
        private val anchor: Long = 0L,
        private val acceptsMutation: Boolean = true,
        private val mutationFailure: Throwable? = null,
    ) : LibraryRemoteDataSource {
        val requestedPageSizes = mutableListOf<Int>()

        override suspend fun fetchSyncAnchor(): Long = anchor

        override suspend fun fetchLibraryPage(
            cursor: RemoteLibraryCursor?,
            limit: Int,
        ): RemoteLibraryPage {
            requestedPageSizes += limit
            return pages.removeFirst()
        }

        override suspend fun fetchLibraryChanges(
            afterSequence: Long,
            limit: Int,
        ): RemoteLibraryChangePage = changes.removeFirst()

        override suspend fun setSaved(
            kind: LibraryItemKind,
            itemId: String,
            saved: Boolean,
        ): Boolean {
            mutationFailure?.let { throw it }
            return acceptsMutation
        }
    }

    private companion object {
        val EMPTY_LIBRARY = LibrarySnapshot(emptyList(), emptyList(), emptyList(), null)
    }
}
