package my.id.rakyzumusic.core.playback

enum class PlaybackQuality(val requestValue: String) {
    Low("low"),
    Standard("standard"),
    High("high"),
}

data class PlaybackPreferences(
    val wifiQuality: PlaybackQuality = PlaybackQuality.Standard,
    val mobileQuality: PlaybackQuality = PlaybackQuality.Standard,
    val dataSaverEnabled: Boolean = false,
) {
    fun effectiveQuality(isMetered: Boolean): PlaybackQuality = when {
        isMetered && dataSaverEnabled -> PlaybackQuality.Low
        isMetered -> mobileQuality
        else -> wifiQuality
    }
}

fun interface PlaybackQualityProvider {
    fun currentQuality(): PlaybackQuality
}

const val PLAYBACK_QUALITY_HEADER = "X-Rakyzu-Audio-Quality"
