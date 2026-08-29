package my.id.rakyzumusic.core.playback

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

data class PlaybackQueueItem(
    val mediaId: String,
    val title: String,
    val artist: String,
    val albumTitle: String?,
    val durationMs: Long,
)

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
