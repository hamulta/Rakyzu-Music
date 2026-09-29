package my.id.rakyzumusic.core.model

enum class LyricsKind { None, Plain, TimeSynced }

data class TrackLyrics(
    val kind: LyricsKind,
    val lines: List<String>,
    val providerName: String?,
    val providerNotice: String?,
) {
    val isDisplayable: Boolean
        get() = kind != LyricsKind.None && lines.isNotEmpty() &&
            lines.size <= MAX_LYRIC_LINES && lines.all { it.length <= MAX_LYRIC_LINE_LENGTH }
}

enum class TrackCreditRole { PrimaryArtist, FeaturedArtist, Songwriter, Producer, Performer }

data class TrackCredit(
    val displayName: String,
    val role: TrackCreditRole,
)

data class TrackContext(
    val trackId: String,
    val lyrics: TrackLyrics,
    val credits: List<TrackCredit>,
    val catalogRevision: String,
) {
    val hasCredits: Boolean
        get() = credits.isNotEmpty() && credits.size <= MAX_TRACK_CREDITS &&
            credits.all { it.displayName.isNotBlank() && it.displayName.length <= 120 }
}

enum class ReleaseNotificationPreference { Off, FollowedArtists, AllSavedArtists }

fun rakyzuTrackShareUri(trackId: String): String? = trackId
    .lowercase()
    .takeIf(TRACK_ID_PATTERN::matches)
    ?.let { "my.id.rakyzumusic://track/$it" }

private const val MAX_LYRIC_LINES = 2_000
private const val MAX_LYRIC_LINE_LENGTH = 500
private const val MAX_TRACK_CREDITS = 200
private val TRACK_ID_PATTERN = Regex(
    "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
)
