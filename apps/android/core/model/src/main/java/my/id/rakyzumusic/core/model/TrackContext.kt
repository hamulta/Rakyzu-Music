package my.id.rakyzumusic.core.model

import java.net.URI

enum class LyricsKind { Unavailable, Plain, TimeSynced }

data class LyricLine(
    val text: String,
    val startTimeMs: Long? = null,
)

data class TrackLyrics(
    val kind: LyricsKind,
    val lines: List<LyricLine>,
    val providerName: String?,
    val providerNotice: String?,
) {
    val isDisplayable: Boolean
        get() = kind != LyricsKind.Unavailable && lines.isNotEmpty() &&
            lines.size <= MAX_LYRIC_LINES && lines.all {
                it.text.isNotBlank() && it.text.length <= MAX_LYRIC_LINE_LENGTH &&
                    (it.startTimeMs == null || it.startTimeMs >= 0L)
            } && (kind != LyricsKind.TimeSynced || lines.all { it.startTimeMs != null })

    fun activeLineIndex(positionMs: Long): Int? {
        if (kind != LyricsKind.TimeSynced || !isDisplayable) return null
        return lines.indexOfLast { (it.startTimeMs ?: Long.MAX_VALUE) <= positionMs }
            .takeIf { it >= 0 }
    }
}

enum class TrackCreditRole { PrimaryArtist, FeaturedArtist, Songwriter, Producer, Performer }

data class TrackCredit(
    val displayName: String,
    val role: TrackCreditRole,
    val sourceName: String? = null,
)

data class TrackContext(
    val trackId: String,
    val lyrics: TrackLyrics,
    val credits: List<TrackCredit>,
    val catalogRevision: String,
    val cachedAtEpochMillis: Long = 0L,
    val expiresAtEpochMillis: Long = Long.MAX_VALUE,
) {
    val hasCredits: Boolean
        get() = credits.isNotEmpty() && credits.size <= MAX_TRACK_CREDITS &&
            credits.all { it.displayName.isNotBlank() && it.displayName.length <= 120 }

    fun isUsableFor(trackId: String, nowEpochMillis: Long): Boolean =
        this.trackId == trackId && catalogRevision.isNotBlank() &&
            cachedAtEpochMillis in 0L..nowEpochMillis && expiresAtEpochMillis > nowEpochMillis
}

enum class ReleaseNotificationPreference { Off, FollowedArtists, AllSavedArtists }

data class ReleaseNotificationSettings(
    val preference: ReleaseNotificationPreference = ReleaseNotificationPreference.Off,
    val updatedAtEpochMillis: Long = 0L,
)

fun rakyzuTrackShareUri(trackId: String): String? = trackId
    .lowercase()
    .takeIf(TRACK_ID_PATTERN::matches)
    ?.let { "my.id.rakyzumusic://track/$it" }

fun parseRakyzuTrackShareUri(value: String?): String? {
    val uri = value?.let { runCatching { URI(it) }.getOrNull() } ?: return null
    if (!uri.scheme.equals("my.id.rakyzumusic", ignoreCase = true) ||
        !uri.host.equals("track", ignoreCase = true) || uri.port != -1 ||
        uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null
    ) return null
    val trackId = uri.path?.removePrefix("/")?.takeIf { '/' !in it } ?: return null
    return trackId.lowercase().takeIf(TRACK_ID_PATTERN::matches)
}

private const val MAX_LYRIC_LINES = 2_000
private const val MAX_LYRIC_LINE_LENGTH = 500
private const val MAX_TRACK_CREDITS = 200
private val TRACK_ID_PATTERN = Regex(
    "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
)
