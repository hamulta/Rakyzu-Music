package my.id.rakyzumusic.core.playback

import java.net.URI

data class PlaybackNetworkRequest(
    val url: String,
    val headers: Map<String, String>,
) {
    override fun toString(): String = "PlaybackNetworkRequest(url=$url, headers=REDACTED)"
}

fun interface PlaybackStreamRequestProvider {
    fun resolve(trackId: String): PlaybackStreamRequestResult
}

sealed interface PlaybackStreamRequestResult {
    data class Ready(val request: PlaybackNetworkRequest) : PlaybackStreamRequestResult

    data class Failure(val reason: PlaybackRequestFailure) : PlaybackStreamRequestResult
}

enum class PlaybackRequestFailure {
    InvalidMediaId,
    InvalidConfiguration,
    NotAuthenticated,
}

internal class AuthenticatedPlaybackRequestResolver(
    private val provider: PlaybackStreamRequestProvider,
) {
    fun resolve(mediaUri: String): PlaybackNetworkRequest {
        val trackId = mediaUri.trackIdOrNull()
            ?: throw PlaybackRequestException(PlaybackRequestFailure.InvalidMediaId)
        val request = when (val result = provider.resolve(trackId)) {
            is PlaybackStreamRequestResult.Ready -> result.request
            is PlaybackStreamRequestResult.Failure -> throw PlaybackRequestException(result.reason)
        }
        if (!request.hasValidHttpsUrl() || !request.hasAuthorizationHeader()) {
            throw PlaybackRequestException(PlaybackRequestFailure.InvalidConfiguration)
        }
        return request
    }
}

internal class PlaybackRequestException(
    val reason: PlaybackRequestFailure,
) : java.io.IOException("Playback request failed: ${reason.name}")

internal fun String.trackIdOrNull(): String? {
    val uri = runCatching { URI(this) }.getOrNull() ?: return null
    if (uri.scheme != PLAYBACK_SCHEME || uri.host != PLAYBACK_HOST) return null
    if (uri.rawQuery != null || uri.rawFragment != null || uri.rawUserInfo != null) return null
    val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)
    val trackId = segments.singleOrNull()?.lowercase() ?: return null
    return trackId.takeIf(TRACK_ID_PATTERN::matches)
}

private fun PlaybackNetworkRequest.hasValidHttpsUrl(): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    return uri.scheme == "https" &&
        !uri.host.isNullOrBlank() &&
        uri.rawUserInfo == null &&
        uri.rawFragment == null
}

private fun PlaybackNetworkRequest.hasAuthorizationHeader(): Boolean =
    headers.entries.singleOrNull { it.key.equals("Authorization", ignoreCase = true) }
        ?.value
        ?.takeIf { it.startsWith("Bearer ") }
        ?.removePrefix("Bearer ")
        ?.isNotBlank() == true

internal const val PLAYBACK_SCHEME = "rakyzu"
internal const val PLAYBACK_HOST = "tracks"

private val TRACK_ID_PATTERN = Regex(
    "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
    RegexOption.IGNORE_CASE,
)
