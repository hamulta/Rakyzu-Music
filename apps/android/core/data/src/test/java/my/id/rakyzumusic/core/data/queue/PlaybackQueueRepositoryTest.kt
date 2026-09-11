package my.id.rakyzumusic.core.data.queue

import kotlinx.coroutines.test.runTest
import my.id.rakyzumusic.core.database.catalog.PlaybackQueueLocalDataSource
import my.id.rakyzumusic.core.model.PersistedPlaybackQueue
import my.id.rakyzumusic.core.model.PlaybackQueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueRepositoryTest {
    @Test
    fun replacementKeepsOrderAndCurrentIndexWithinTheAccount() = runTest {
        val local = FakePlaybackQueueLocalDataSource()
        val repository = OfflineFirstPlaybackQueueRepository(local) { 42L }
        val items = listOf(queueItem("first"), queueItem("second"))

        assertTrue(repository.replace("listener-1", items, currentIndex = 1))

        assertEquals(
            PersistedPlaybackQueue(items, currentIndex = 1, updatedAtEpochMillis = 42L),
            repository.read("listener-1"),
        )
        assertTrue(repository.read("listener-2").isEmpty)
    }

    @Test
    fun invalidAccountIndexAndItemAreRejectedBeforeRoom() = runTest {
        val local = FakePlaybackQueueLocalDataSource()
        val repository = OfflineFirstPlaybackQueueRepository(local)

        assertFalse(repository.replace("", listOf(queueItem("track")), 0))
        assertFalse(repository.replace("listener", listOf(queueItem("track")), 1))
        assertFalse(repository.replace("listener", listOf(queueItem("")), 0))
        assertFalse(repository.replace("listener", emptyList(), 0))
        assertEquals(0, local.writeCount)
    }

    @Test
    fun replacementCopiesTheQueueContainer() = runTest {
        val local = FakePlaybackQueueLocalDataSource()
        val repository = OfflineFirstPlaybackQueueRepository(local) { 7L }
        val items = mutableListOf(queueItem("stable"))

        assertTrue(repository.replace("listener", items, 0))
        items.clear()

        assertEquals(listOf("stable"), repository.read("listener").items.map { it.mediaId })
    }

    private fun queueItem(id: String) = PlaybackQueueItem(
        mediaId = id,
        title = "Title $id",
        artist = "Rakyzu Artist",
        albumTitle = "Rakyzu Album",
        durationMs = 120_000L,
        artistId = "artist",
        albumId = "album",
    )
}

private class FakePlaybackQueueLocalDataSource : PlaybackQueueLocalDataSource {
    private val queues = mutableMapOf<String, PersistedPlaybackQueue>()
    var writeCount = 0

    override suspend fun read(userId: String): PersistedPlaybackQueue =
        queues[userId] ?: PersistedPlaybackQueue()

    override suspend fun replace(userId: String, queue: PersistedPlaybackQueue) {
        writeCount += 1
        queues[userId] = queue
    }
}
