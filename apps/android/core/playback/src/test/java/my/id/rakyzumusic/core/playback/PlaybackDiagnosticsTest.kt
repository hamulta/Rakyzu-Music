package my.id.rakyzumusic.core.playback

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackDiagnosticsTest {
    @Test
    fun diagnosticBufferIsBoundedAndContainsOnlyCoarsePlaybackState() {
        val lines = mutableListOf<String>()
        val sink = BoundedPlaybackDiagnosticSink(capacity = 2, logger = lines::add)

        sink.record(PlaybackDiagnosticEvent.QueueStarted(PlaybackQueueSize.One))
        sink.record(
            PlaybackDiagnosticEvent.PlaybackFailed(
                PlaybackFailureKind.Network,
                PlaybackQueueSize.Few,
                retryable = true,
            ),
        )
        sink.record(PlaybackDiagnosticEvent.RecoveryAttempted(2, PlaybackQueueSize.Few))

        assertEquals(2, sink.snapshot().size)
        assertEquals(3, lines.size)
        assertTrue(lines.all { it.length <= 160 })
        assertFalse(lines.joinToString().contains("http", ignoreCase = true))
        assertFalse(lines.joinToString().contains("token", ignoreCase = true))
    }

    @Test
    fun onlyTransientIoFailuresAreRetryable() {
        assertTrue(
            isRetryablePlaybackError(
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            ),
        )
        assertTrue(
            isRetryablePlaybackError(
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                httpResponseCode = 503,
            ),
        )
        assertFalse(
            isRetryablePlaybackError(
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                httpResponseCode = 404,
            ),
        )
        assertFalse(isRetryablePlaybackError(PlaybackException.ERROR_CODE_DECODING_FAILED))
        assertEquals(
            PlaybackFailureKind.Network,
            playbackFailureKind(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED),
        )
        assertEquals(
            PlaybackFailureKind.Unexpected,
            playbackFailureKind(PlaybackException.ERROR_CODE_DECODING_FAILED),
        )
    }
}
