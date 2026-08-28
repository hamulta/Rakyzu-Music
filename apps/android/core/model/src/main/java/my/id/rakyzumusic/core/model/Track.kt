package my.id.rakyzumusic.core.model

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
)

fun Track.formattedDuration(): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
