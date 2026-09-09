package my.id.rakyzumusic.core.data.playlist

import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.id.rakyzumusic.core.data.media.AccessTokenProvider
import my.id.rakyzumusic.core.data.media.RakyzuApiConfiguration

sealed interface PlaylistArtworkResult {
    class Image(val bytes: ByteArray) : PlaylistArtworkResult
    data object Absent : PlaylistArtworkResult
    data object Updated : PlaylistArtworkResult
    data object Failed : PlaylistArtworkResult
}

/** No redirects, token logging, disk cache, or client-side privileged credentials. */
internal class PlaylistArtworkRemoteDataSource(
    configuration: RakyzuApiConfiguration,
    private val tokens: AccessTokenProvider,
) {
    private val origin = configuration.normalizedOriginOrNull()

    suspend fun request(id: String, method: String, bytes: ByteArray? = null): PlaylistArtworkResult =
        withContext(Dispatchers.IO) {
            val base = origin ?: return@withContext PlaylistArtworkResult.Failed
            val token = tokens.currentAccessTokenOrNull()?.takeIf { it.isNotBlank() }
                ?: return@withContext PlaylistArtworkResult.Failed
            val connection = URI("$base/v1/playlists/$id/artwork").toURL().openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.requestMethod = method
                connection.setRequestProperty("Authorization", "Bearer $token")
                if (method == "PUT") {
                    require(bytes != null && bytes.size in 45..MAX_PLAYLIST_ARTWORK_BYTES)
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "image/png")
                    connection.setFixedLengthStreamingMode(bytes.size)
                    connection.outputStream.use { it.write(bytes) }
                }
                when (connection.responseCode) {
                    204 -> PlaylistArtworkResult.Updated
                    404 -> PlaylistArtworkResult.Absent
                    200 -> {
                        if (connection.contentType != "image/png") return@withContext PlaylistArtworkResult.Failed
                        val output = java.io.ByteArrayOutputStream()
                        connection.inputStream.use { input ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (output.size() + count > MAX_PLAYLIST_ARTWORK_BYTES) return@withContext PlaylistArtworkResult.Failed
                                output.write(buffer, 0, count)
                            }
                        }
                        PlaylistArtworkResult.Image(output.toByteArray())
                    }
                    else -> PlaylistArtworkResult.Failed
                }
            } catch (_: java.io.IOException) {
                PlaylistArtworkResult.Failed
            } finally {
                connection.disconnect()
            }
        }
}

const val MAX_PLAYLIST_ARTWORK_BYTES = 1024 * 1024
