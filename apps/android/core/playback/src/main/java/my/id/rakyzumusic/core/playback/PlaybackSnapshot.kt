package my.id.rakyzumusic.core.playback

import my.id.rakyzumusic.core.model.PlaybackQueueItem

data class PlaybackSnapshot(
    val mediaId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val albumTitle: String? = null,
    val status: PlaybackStatus = PlaybackStatus.Idle,
    val positionMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val currentIndex: Int = -1,
    val queue: List<PlaybackQueueItem> = emptyList(),
    val canSkipPrevious: Boolean = false,
    val canSkipNext: Boolean = false,
    val autoplayPolicy: AutoplayPolicy = AutoplayPolicy.ExplicitQueueOnly,
    val device: PlaybackDeviceState = PlaybackDeviceState(),
    val recovery: PlaybackRecoveryState = PlaybackRecoveryState(),
    val error: PlaybackError? = null,
) {
    val progressFraction: Float
        get() = if (durationMs <= 0L) {
            0f
        } else {
            (positionMs.toDouble() / durationMs.toDouble()).coerceIn(0.0, 1.0).toFloat()
        }

    val bufferedFraction: Float
        get() = if (durationMs <= 0L) {
            0f
        } else {
            (bufferedPositionMs.toDouble() / durationMs.toDouble())
                .coerceIn(progressFraction.toDouble(), 1.0)
                .toFloat()
        }
}

/**
 * Rakyzu never appends recommendation media to a listener's queue implicitly.
 * The current queue can still advance through items deliberately selected by the listener.
 */
enum class AutoplayPolicy {
    ExplicitQueueOnly,
}

/**
 * Foundation for the device picker. Rakyzu currently represents the local player and intentionally
 * makes no remote-control or device-transfer claim.
 */
data class PlaybackDeviceState(
    val id: String = LOCAL_DEVICE_ID,
    val name: String = "This device",
    val isActive: Boolean = true,
    val supportsRemoteControl: Boolean = false,
) {
    companion object {
        const val LOCAL_DEVICE_ID = "rakyzu-local-device"
    }
}

enum class PlaybackStatus {
    Idle,
    Connecting,
    Buffering,
    Playing,
    Paused,
    Ended,
}

data class PlaybackError(
    val code: String,
)

data class PlaybackRecoveryState(
    val canRetry: Boolean = false,
    val attemptCount: Int = 0,
    val retainedQueueSize: Int = 0,
)

internal fun resolvePlaybackQueue(
    cachedQueue: List<PlaybackQueueItem>,
    mediaItemCount: Int,
    timelineChanged: Boolean,
    readQueue: () -> List<PlaybackQueueItem>,
): List<PlaybackQueueItem> = if (
    timelineChanged || cachedQueue.size != mediaItemCount
) {
    readQueue()
} else {
    cachedQueue
}

fun Long.toPlaybackTimeLabel(): String {
    val totalSeconds = coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
