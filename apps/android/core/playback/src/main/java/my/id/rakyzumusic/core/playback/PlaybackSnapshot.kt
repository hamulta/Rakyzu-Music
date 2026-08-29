package my.id.rakyzumusic.core.playback

data class PlaybackSnapshot(
    val mediaId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val status: PlaybackStatus = PlaybackStatus.Idle,
    val error: PlaybackError? = null,
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
