package my.id.rakyzumusic.core.playback

import org.junit.Assert.assertEquals
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
}
