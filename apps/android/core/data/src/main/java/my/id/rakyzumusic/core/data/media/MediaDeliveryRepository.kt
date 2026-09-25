package my.id.rakyzumusic.core.data.media

import java.net.URI

private val mediaIdPattern = Regex(
    "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
    RegexOption.IGNORE_CASE,
)

data class RakyzuApiConfiguration(
    val baseUrl: String,
) {
    internal fun normalizedOriginOrNull(): String? {
        val uri = runCatching { URI(baseUrl) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host.isNullOrBlank()) return null
        if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
        if (!uri.rawPath.isNullOrEmpty() && uri.rawPath != "/") return null
        return URI("https", null, uri.host.lowercase(), uri.port, null, null, null).toASCIIString()
    }
}

interface MediaDeliveryRepository {
    fun streamRequest(trackId: String): MediaStreamRequestResult
    fun downloadRequest(trackId: String): OfflineDownloadRequestResult

    fun artworkRequest(albumId: String): ArtworkRequestResult
    fun recommendationArtworkRequest(shelfId: String): ArtworkRequestResult
    fun profileAvatarRequest(userId: String): ArtworkRequestResult
}

sealed interface MediaStreamRequestResult {
    data class Ready(val request: MediaStreamRequest) : MediaStreamRequestResult

    data class Failure(val reason: MediaStreamRequestFailure) : MediaStreamRequestResult
}

enum class MediaStreamRequestFailure {
    InvalidConfiguration,
    InvalidTrackId,
    NotAuthenticated,
}

sealed interface OfflineDownloadRequestResult {
    data class Ready(val request: MediaStreamRequest) : OfflineDownloadRequestResult
    data class Failure(val reason: MediaStreamRequestFailure) : OfflineDownloadRequestResult
}

sealed interface ArtworkRequestResult {
    data class Ready(val request: ArtworkRequest) : ArtworkRequestResult

    data class Failure(val reason: ArtworkRequestFailure) : ArtworkRequestResult
}

enum class ArtworkRequestFailure {
    InvalidConfiguration,
    InvalidAlbumId,
    NotAuthenticated,
}

class MediaStreamRequest internal constructor(
    val url: String,
    private val accessToken: String,
) {
    fun requestHeaders(): Map<String, String> = mapOf(
        "Authorization" to "Bearer $accessToken",
    )

    override fun toString(): String = "MediaStreamRequest(url=$url, authorization=REDACTED)"
}

class ArtworkRequest internal constructor(
    val url: String,
    val albumId: String,
    private val accessToken: String,
) {
    fun requestHeaders(): Map<String, String> = mapOf(
        "Authorization" to "Bearer $accessToken",
    )

    override fun toString(): String =
        "ArtworkRequest(url=$url, albumId=$albumId, authorization=REDACTED)"
}

internal fun interface AccessTokenProvider {
    fun currentAccessTokenOrNull(): String?
}

internal class AuthenticatedMediaDeliveryRepository(
    configuration: RakyzuApiConfiguration,
    private val accessTokenProvider: AccessTokenProvider,
) : MediaDeliveryRepository {
    private val apiOrigin = configuration.normalizedOriginOrNull()

    override fun streamRequest(trackId: String): MediaStreamRequestResult {
        val origin = apiOrigin
            ?: return MediaStreamRequestResult.Failure(
                MediaStreamRequestFailure.InvalidConfiguration,
            )
        if (!mediaIdPattern.matches(trackId)) {
            return MediaStreamRequestResult.Failure(MediaStreamRequestFailure.InvalidTrackId)
        }
        val accessToken = accessTokenProvider.currentAccessTokenOrNull()
            ?.takeIf(String::isNotBlank)
            ?: return MediaStreamRequestResult.Failure(MediaStreamRequestFailure.NotAuthenticated)
        val normalizedTrackId = trackId.lowercase()
        return MediaStreamRequestResult.Ready(
            MediaStreamRequest(
                url = "$origin/v1/tracks/$normalizedTrackId/stream",
                accessToken = accessToken,
            ),
        )
    }

    override fun downloadRequest(trackId: String): OfflineDownloadRequestResult {
        val origin = apiOrigin
            ?: return OfflineDownloadRequestResult.Failure(
                MediaStreamRequestFailure.InvalidConfiguration,
            )
        if (!mediaIdPattern.matches(trackId)) {
            return OfflineDownloadRequestResult.Failure(MediaStreamRequestFailure.InvalidTrackId)
        }
        val accessToken = accessTokenProvider.currentAccessTokenOrNull()
            ?.takeIf(String::isNotBlank)
            ?: return OfflineDownloadRequestResult.Failure(MediaStreamRequestFailure.NotAuthenticated)
        return OfflineDownloadRequestResult.Ready(
            MediaStreamRequest(
                url = "$origin/v1/tracks/${trackId.lowercase()}/download",
                accessToken = accessToken,
            ),
        )
    }

    override fun artworkRequest(albumId: String): ArtworkRequestResult {
        val origin = apiOrigin
            ?: return ArtworkRequestResult.Failure(
                ArtworkRequestFailure.InvalidConfiguration,
            )
        if (!mediaIdPattern.matches(albumId)) {
            return ArtworkRequestResult.Failure(ArtworkRequestFailure.InvalidAlbumId)
        }
        val accessToken = accessTokenProvider.currentAccessTokenOrNull()
            ?.takeIf(String::isNotBlank)
            ?: return ArtworkRequestResult.Failure(ArtworkRequestFailure.NotAuthenticated)
        val normalizedAlbumId = albumId.lowercase()
        return ArtworkRequestResult.Ready(
            ArtworkRequest(
                url = "$origin/v1/albums/$normalizedAlbumId/artwork",
                albumId = normalizedAlbumId,
                accessToken = accessToken,
            ),
        )
    }

    override fun recommendationArtworkRequest(shelfId: String): ArtworkRequestResult {
        val origin = apiOrigin
            ?: return ArtworkRequestResult.Failure(ArtworkRequestFailure.InvalidConfiguration)
        if (!mediaIdPattern.matches(shelfId)) {
            return ArtworkRequestResult.Failure(ArtworkRequestFailure.InvalidAlbumId)
        }
        val accessToken = accessTokenProvider.currentAccessTokenOrNull()
            ?.takeIf(String::isNotBlank)
            ?: return ArtworkRequestResult.Failure(ArtworkRequestFailure.NotAuthenticated)
        val normalizedId = shelfId.lowercase()
        return ArtworkRequestResult.Ready(
            ArtworkRequest(
                url = "$origin/v1/recommendations/$normalizedId/artwork",
                albumId = normalizedId,
                accessToken = accessToken,
            ),
        )
    }

    override fun profileAvatarRequest(userId: String): ArtworkRequestResult {
        val origin = apiOrigin
            ?: return ArtworkRequestResult.Failure(ArtworkRequestFailure.InvalidConfiguration)
        if (!mediaIdPattern.matches(userId)) {
            return ArtworkRequestResult.Failure(ArtworkRequestFailure.InvalidAlbumId)
        }
        val accessToken = accessTokenProvider.currentAccessTokenOrNull()
            ?.takeIf(String::isNotBlank)
            ?: return ArtworkRequestResult.Failure(ArtworkRequestFailure.NotAuthenticated)
        val normalizedId = userId.lowercase()
        return ArtworkRequestResult.Ready(
            ArtworkRequest(
                url = "$origin/v1/profiles/$normalizedId/avatar",
                albumId = normalizedId,
                accessToken = accessToken,
            ),
        )
    }
}

internal data object UnavailableMediaDeliveryRepository : MediaDeliveryRepository {
    override fun streamRequest(trackId: String): MediaStreamRequestResult =
        MediaStreamRequestResult.Failure(MediaStreamRequestFailure.InvalidConfiguration)

    override fun downloadRequest(trackId: String): OfflineDownloadRequestResult =
        OfflineDownloadRequestResult.Failure(MediaStreamRequestFailure.InvalidConfiguration)

    override fun artworkRequest(albumId: String): ArtworkRequestResult =
        ArtworkRequestResult.Failure(ArtworkRequestFailure.InvalidConfiguration)
    override fun recommendationArtworkRequest(shelfId: String): ArtworkRequestResult =
        ArtworkRequestResult.Failure(ArtworkRequestFailure.InvalidConfiguration)
    override fun profileAvatarRequest(userId: String): ArtworkRequestResult =
        ArtworkRequestResult.Failure(ArtworkRequestFailure.InvalidConfiguration)
}
