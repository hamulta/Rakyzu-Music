package my.id.rakyzumusic.core.playback

import my.id.rakyzumusic.core.model.PlaybackQueueItem
import my.id.rakyzumusic.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PlaybackSnapshotTest {
    @Test
    fun progressFractionsClampInvalidPositions() {
        val beyondDuration = PlaybackSnapshot(
            positionMs = 12_000L,
            bufferedPositionMs = 15_000L,
            durationMs = 10_000L,
        )
        val unknownDuration = PlaybackSnapshot(
            positionMs = 1_000L,
            bufferedPositionMs = 2_000L,
        )

        assertEquals(1f, beyondDuration.progressFraction)
        assertEquals(1f, beyondDuration.bufferedFraction)
        assertEquals(0f, unknownDuration.progressFraction)
        assertEquals(0f, unknownDuration.bufferedFraction)
    }

    @Test
    fun bufferedProgressNeverFallsBehindPlaybackPosition() {
        val snapshot = PlaybackSnapshot(
            positionMs = 5_000L,
            bufferedPositionMs = 2_000L,
            durationMs = 10_000L,
        )

        assertEquals(0.5f, snapshot.progressFraction)
        assertEquals(0.5f, snapshot.bufferedFraction)
    }

    @Test
    fun playbackTimeLabelsSupportMinutesAndHours() {
        assertEquals("0:00", (-1L).toPlaybackTimeLabel())
        assertEquals("3:05", 185_900L.toPlaybackTimeLabel())
        assertEquals("1:02:03", 3_723_000L.toPlaybackTimeLabel())
    }

    @Test
    fun queueSnapshotIsReusedUntilTheTimelineChanges() {
        val cachedQueue = listOf(queueItem("one"), queueItem("two"))
        var readCount = 0

        val unchanged = resolvePlaybackQueue(
            cachedQueue = cachedQueue,
            mediaItemCount = 2,
            timelineChanged = false,
        ) {
            readCount += 1
            emptyList()
        }

        assertSame(cachedQueue, unchanged)
        assertEquals(0, readCount)

        val rebuilt = resolvePlaybackQueue(
            cachedQueue = cachedQueue,
            mediaItemCount = 2,
            timelineChanged = true,
        ) {
            readCount += 1
            listOf(queueItem("updated"))
        }

        assertEquals(listOf(queueItem("updated")), rebuilt)
        assertEquals(1, readCount)
    }

    @Test
    fun queueSnapshotRebuildsWhenTheItemCountChanges() {
        val rebuilt = resolvePlaybackQueue(
            cachedQueue = listOf(queueItem("one")),
            mediaItemCount = 2,
            timelineChanged = false,
        ) {
            listOf(queueItem("one"), queueItem("two"))
        }

        assertEquals(2, rebuilt.size)
    }

    @Test
    fun queueEditingIndicesAreDeterministicAndBounded() {
        assertEquals(2, nextQueueInsertionIndex(currentIndex = 1, itemCount = 4))
        assertEquals(4, nextQueueInsertionIndex(currentIndex = -1, itemCount = 4))
        assertEquals(0, nextQueueInsertionIndex(currentIndex = -1, itemCount = 0))

        assertEquals(true, validQueueMove(fromIndex = 0, toIndex = 2, itemCount = 3))
        assertEquals(false, validQueueMove(fromIndex = 0, toIndex = 0, itemCount = 3))
        assertEquals(false, validQueueMove(fromIndex = -1, toIndex = 1, itemCount = 3))
        assertEquals(false, validQueueMove(fromIndex = 1, toIndex = 3, itemCount = 3))
    }

    @Test
    fun queuePersistenceTracksTimelineAndActiveItemOnly() {
        val first = PlaybackSnapshot(
            status = PlaybackStatus.Paused,
            currentIndex = 0,
        )
        val second = PlaybackSnapshot(currentIndex = 1)

        assertEquals(true, shouldPersistQueueState(first, first, timelineChanged = true))
        assertEquals(true, shouldPersistQueueState(first, second, timelineChanged = false))
        assertEquals(false, shouldPersistQueueState(first, first, timelineChanged = false))
        assertEquals(
            false,
            shouldPersistQueueState(
                previous = PlaybackSnapshot(status = PlaybackStatus.Connecting),
                current = PlaybackSnapshot(status = PlaybackStatus.Idle),
                timelineChanged = true,
            ),
        )
    }

    @Test
    fun queueItemKeepsCanonicalLibraryIdentifiers() {
        val queueItem = Track(
            id = "track-1",
            title = "Midnight Signal",
            artist = "Rakyzu Sessions",
            durationMs = 185_900L,
            artistId = "artist-1",
            albumId = "album-1",
            albumTitle = "Signal Zero",
        ).toPlaybackQueueItem()

        assertEquals("artist-1", queueItem.artistId)
        assertEquals("album-1", queueItem.albumId)
    }

    private fun queueItem(id: String) = PlaybackQueueItem(
        mediaId = id,
        title = id,
        artist = "Rakyzu Music",
        albumTitle = null,
        durationMs = 1_000L,
    )
}
