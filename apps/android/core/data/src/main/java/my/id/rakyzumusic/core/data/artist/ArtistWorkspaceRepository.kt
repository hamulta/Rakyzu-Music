package my.id.rakyzumusic.core.data.artist

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import my.id.rakyzumusic.core.data.media.AccessTokenProvider
import my.id.rakyzumusic.core.data.media.RakyzuApiConfiguration

data class ArtistWorkspace(
    val artistId: String,
    val artistName: String,
    val hasArtwork: Boolean,
    val albums: List<ArtistAlbumDraft>,
    val tracks: List<ArtistTrackDraft>,
    val team: List<ArtistTeamMember>,
    val reviews: List<ArtistReview>,
    val analytics: ArtistAnalytics? = null,
)

data class ArtistAnalytics(
    val streams: Long,
    val listeners: Long?,
    val privacyThresholdMet: Boolean,
    val completedStreams: Long,
    val followers: Long,
    val releases: Long,
    val geography: List<ArtistGeography>,
)

data class ArtistGeography(val country: String, val streams: Long, val listeners: Long)

data class ArtistAlbumDraft(
    val id: String,
    val title: String,
    val releaseDate: String?,
    val published: Boolean,
    val hasArtwork: Boolean,
)

data class ArtistTrackDraft(
    val id: String,
    val albumId: String,
    val title: String,
    val durationMs: Int,
    val published: Boolean,
    val hasStandardAudio: Boolean,
)

data class ArtistTeamMember(
    val userId: String,
    val email: String,
    val accessLevel: String,
    val active: Boolean,
)

data class ArtistReview(
    val id: String,
    val albumId: String,
    val reviewType: String,
    val status: String,
    val submittedAt: String,
    val decisionNotes: String?,
)

sealed interface ArtistWorkspaceResult {
    data class Success(val workspace: ArtistWorkspace) : ArtistWorkspaceResult
    data class Failure(val reason: ArtistWorkspaceFailure) : ArtistWorkspaceResult
}

sealed interface ArtistWorkspaceActionResult {
    data class Success(val message: String) : ArtistWorkspaceActionResult
    data class Failure(val reason: ArtistWorkspaceFailure) : ArtistWorkspaceActionResult
}

enum class ArtistWorkspaceFailure {
    InvalidInput,
    NotAuthenticated,
    Forbidden,
    PayloadTooLarge,
    ServiceUnavailable,
}

interface ArtistWorkspaceRepository {
    suspend fun load(): ArtistWorkspaceResult
    suspend fun assignTeam(email: String, accessLevel: String, active: Boolean): ArtistWorkspaceActionResult
    suspend fun createAlbum(
        artistId: String,
        title: String,
        releaseDate: String?,
    ): ArtistWorkspaceActionResult
    suspend fun createTrack(
        albumId: String,
        title: String,
        durationMs: Int,
        discNumber: Int,
        trackNumber: Int,
        explicit: Boolean,
    ): ArtistWorkspaceActionResult
    suspend fun uploadArtistArtwork(bytes: ByteArray): ArtistWorkspaceActionResult
    suspend fun uploadAlbumArtwork(albumId: String, bytes: ByteArray): ArtistWorkspaceActionResult
    suspend fun uploadTrackAudio(
        trackId: String,
        quality: String,
        bytes: ByteArray,
    ): ArtistWorkspaceActionResult
    suspend fun submitReview(
        type: String,
        albumId: String,
        notes: String,
    ): ArtistWorkspaceActionResult
}

internal class AuthenticatedArtistWorkspaceRepository(
    configuration: RakyzuApiConfiguration,
    private val tokens: AccessTokenProvider,
) : ArtistWorkspaceRepository {
    private val origin = configuration.normalizedOriginOrNull()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun load(): ArtistWorkspaceResult = withContext(Dispatchers.IO) {
        val response = request("GET", "/v1/artists/me/workspace")
        val parsed = response.body?.let(::parseWorkspace)
        if (response.status !in 200..299 || parsed == null) {
            return@withContext ArtistWorkspaceResult.Failure(response.toFailure())
        }
        val end = LocalDate.now(ZoneOffset.UTC)
        val analyticsResponse = request(
            "GET",
            "/v1/artists/${parsed.artistId}/analytics?from=${end.minusDays(29)}&to=$end",
        )
        val analytics = analyticsResponse.body?.let(::parseAnalytics)
        ArtistWorkspaceResult.Success(parsed.copy(analytics = analytics))
    }

    override suspend fun assignTeam(
        email: String,
        accessLevel: String,
        active: Boolean,
    ): ArtistWorkspaceActionResult = action("POST", "/v1/artists/me/team", buildJsonObject {
        put("email", email.trim().lowercase())
        put("accessLevel", accessLevel)
        put("active", active)
    }, "Artist team updated")

    override suspend fun createAlbum(
        artistId: String,
        title: String,
        releaseDate: String?,
    ): ArtistWorkspaceActionResult = action("POST", "/v1/artists/me/albums", buildJsonObject {
            put("artistId", artistId.trim().lowercase())
            put("title", title.trim())
            if (releaseDate.isNullOrBlank()) put("releaseDate", JsonNull)
            else put("releaseDate", releaseDate.trim())
        }, "Album draft created")

    override suspend fun createTrack(
        albumId: String,
        title: String,
        durationMs: Int,
        discNumber: Int,
        trackNumber: Int,
        explicit: Boolean,
    ): ArtistWorkspaceActionResult = action("POST", "/v1/artists/me/tracks", buildJsonObject {
        put("albumId", albumId.trim().lowercase())
        put("title", title.trim())
        put("durationMs", durationMs)
        put("discNumber", discNumber)
        put("trackNumber", trackNumber)
        put("explicit", explicit)
    }, "Track draft created")

    override suspend fun uploadArtistArtwork(bytes: ByteArray): ArtistWorkspaceActionResult =
        binaryAction("/v1/artists/me/artwork", bytes, "image/webp", "Artist image updated")

    override suspend fun uploadAlbumArtwork(
        albumId: String,
        bytes: ByteArray,
    ): ArtistWorkspaceActionResult = binaryAction(
        "/v1/artist/albums/${albumId.trim().lowercase()}/artwork",
        bytes,
        "image/webp",
        "Album image uploaded for review",
    )

    override suspend fun uploadTrackAudio(
        trackId: String,
        quality: String,
        bytes: ByteArray,
    ): ArtistWorkspaceActionResult = withContext(Dispatchers.IO) {
        val audioType = detectAudioType(bytes)
        if (!UUID.matches(trackId) || quality !in AUDIO_QUALITIES ||
            bytes.size !in 4..MAX_AUDIO_BYTES || audioType == null
        ) return@withContext ArtistWorkspaceActionResult.Failure(ArtistWorkspaceFailure.InvalidInput)
        request("PUT", "/v1/artist/tracks/${trackId.lowercase()}/audio/$quality",
            binary = bytes, binaryContentType = audioType).toAction("Track audio uploaded")
    }

    override suspend fun submitReview(
        type: String,
        albumId: String,
        notes: String,
    ): ArtistWorkspaceActionResult = action("POST", "/v1/artists/me/reviews", buildJsonObject {
        put("type", type)
        put("albumId", albumId.trim().lowercase())
        put("notes", notes.trim())
    }, "Release submitted for independent review")

    private suspend fun binaryAction(
        path: String,
        bytes: ByteArray,
        contentType: String,
        message: String,
    ): ArtistWorkspaceActionResult = withContext(Dispatchers.IO) {
        if (bytes.size !in 12..MAX_ARTWORK_BYTES || !looksLikeWebp(bytes)) {
            return@withContext ArtistWorkspaceActionResult.Failure(ArtistWorkspaceFailure.InvalidInput)
        }
        request("PUT", path, binary = bytes, binaryContentType = contentType).toAction(message)
    }

    private suspend fun action(
        method: String,
        path: String,
        body: JsonObject,
        message: String,
    ): ArtistWorkspaceActionResult = withContext(Dispatchers.IO) {
        request(method, path, body).toAction(message)
    }

    private fun request(
        method: String,
        path: String,
        body: JsonObject? = null,
        binary: ByteArray? = null,
        binaryContentType: String = "application/octet-stream",
    ): ApiResponse {
        val base = origin ?: return ApiResponse(503, null)
        val token = tokens.currentAccessTokenOrNull()?.takeIf(String::isNotBlank)
            ?: return ApiResponse(401, null)
        val connection = URI("$base$path").toURL().openConnection() as HttpURLConnection
        return try {
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = 10_000
            connection.readTimeout = if (binary == null) 20_000 else 60_000
            connection.requestMethod = method
            connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null || binary != null) {
                val bytes = binary ?: body.toString().encodeToByteArray()
                connection.doOutput = true
                connection.setRequestProperty("Content-Type",
                    if (binary == null) "application/json" else binaryContentType)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            val responseBody = if (status in 200..299 && status != 204) {
                readBounded(connection.inputStream)?.let { raw ->
                    runCatching { json.parseToJsonElement(raw) }.getOrNull()
                }
            } else null
            ApiResponse(status, responseBody)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: IOException) {
            ApiResponse(503, null)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseWorkspace(element: JsonElement): ArtistWorkspace? {
        val root = element as? JsonObject ?: return null
        return ArtistWorkspace(
            artistId = root.string("artistId") ?: return null,
            artistName = root.string("artistName") ?: return null,
            hasArtwork = root.boolean("hasArtwork") ?: false,
            albums = root.array("albums").mapNotNull { raw ->
                val item = raw as? JsonObject ?: return@mapNotNull null
                ArtistAlbumDraft(item.string("id") ?: return@mapNotNull null,
                    item.string("title") ?: return@mapNotNull null, item.string("releaseDate"),
                    item.boolean("published") ?: false, item.boolean("hasArtwork") ?: false)
            },
            tracks = root.array("tracks").mapNotNull { raw ->
                val item = raw as? JsonObject ?: return@mapNotNull null
                ArtistTrackDraft(item.string("id") ?: return@mapNotNull null,
                    item.string("albumId") ?: return@mapNotNull null,
                    item.string("title") ?: return@mapNotNull null,
                    item.int("durationMs") ?: return@mapNotNull null,
                    item.boolean("published") ?: false,
                    item.boolean("hasStandardAudio") ?: false)
            },
            team = root.array("team").mapNotNull { raw ->
                val item = raw as? JsonObject ?: return@mapNotNull null
                ArtistTeamMember(item.string("userId") ?: return@mapNotNull null,
                    item.string("email") ?: return@mapNotNull null,
                    item.string("accessLevel") ?: return@mapNotNull null,
                    item.boolean("active") ?: false)
            },
            reviews = root.array("reviews").mapNotNull { raw ->
                val item = raw as? JsonObject ?: return@mapNotNull null
                ArtistReview(item.string("id") ?: return@mapNotNull null,
                    item.string("albumId") ?: return@mapNotNull null,
                    item.string("reviewType") ?: return@mapNotNull null,
                    item.string("status") ?: return@mapNotNull null,
                    item.string("submittedAt") ?: return@mapNotNull null,
                    item.string("decisionNotes"))
            },
        )
    }

    private fun parseAnalytics(element: JsonElement): ArtistAnalytics? {
        val root = element as? JsonObject ?: return null
        return ArtistAnalytics(
            streams = root.long("streams") ?: return null,
            listeners = root.long("listeners"),
            privacyThresholdMet = root.boolean("privacyThresholdMet") ?: false,
            completedStreams = root.long("completedStreams") ?: 0,
            followers = root.long("followers") ?: 0,
            releases = root.long("releases") ?: 0,
            geography = root.array("geography").mapNotNull { raw ->
                val item = raw as? JsonObject ?: return@mapNotNull null
                ArtistGeography(
                    country = item.string("country") ?: return@mapNotNull null,
                    streams = item.long("streams") ?: return@mapNotNull null,
                    listeners = item.long("listeners") ?: return@mapNotNull null,
                )
            },
        )
    }

    private fun readBounded(input: java.io.InputStream): String? {
        val output = ByteArrayOutputStream()
        input.use {
            val buffer = ByteArray(8192)
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                if (output.size() + count > MAX_JSON_BYTES) return null
                output.write(buffer, 0, count)
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private data class ApiResponse(val status: Int, val body: JsonElement?) {
        fun toFailure(): ArtistWorkspaceFailure = when (status) {
            400, 404, 409, 422 -> ArtistWorkspaceFailure.InvalidInput
            401 -> ArtistWorkspaceFailure.NotAuthenticated
            403 -> ArtistWorkspaceFailure.Forbidden
            413 -> ArtistWorkspaceFailure.PayloadTooLarge
            else -> ArtistWorkspaceFailure.ServiceUnavailable
        }
        fun toAction(message: String): ArtistWorkspaceActionResult = if (status in 200..299) {
            ArtistWorkspaceActionResult.Success(message)
        } else ArtistWorkspaceActionResult.Failure(toFailure())
    }

    private companion object {
        val UUID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
            RegexOption.IGNORE_CASE)
        val AUDIO_QUALITIES = setOf("low", "standard", "high")
        const val MAX_ARTWORK_BYTES = 5 * 1024 * 1024
        const val MAX_AUDIO_BYTES = 50 * 1024 * 1024
        const val MAX_JSON_BYTES = 512 * 1024
    }
}

internal data object UnavailableArtistWorkspaceRepository : ArtistWorkspaceRepository {
    private fun failure() = ArtistWorkspaceActionResult.Failure(ArtistWorkspaceFailure.ServiceUnavailable)
    override suspend fun load() = ArtistWorkspaceResult.Failure(ArtistWorkspaceFailure.ServiceUnavailable)
    override suspend fun assignTeam(email: String, accessLevel: String, active: Boolean) = failure()
    override suspend fun createAlbum(artistId: String, title: String, releaseDate: String?) = failure()
    override suspend fun createTrack(albumId: String, title: String, durationMs: Int,
        discNumber: Int, trackNumber: Int, explicit: Boolean) = failure()
    override suspend fun uploadArtistArtwork(bytes: ByteArray) = failure()
    override suspend fun uploadAlbumArtwork(albumId: String, bytes: ByteArray) = failure()
    override suspend fun uploadTrackAudio(trackId: String, quality: String, bytes: ByteArray) = failure()
    override suspend fun submitReview(type: String, albumId: String, notes: String) = failure()
}

private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
private fun JsonObject.boolean(name: String): Boolean? = this[name]?.jsonPrimitive?.booleanOrNull
private fun JsonObject.int(name: String): Int? = this[name]?.jsonPrimitive?.intOrNull
private fun JsonObject.long(name: String): Long? = this[name]?.jsonPrimitive?.longOrNull
private fun JsonObject.array(name: String): JsonArray = this[name] as? JsonArray ?: JsonArray(emptyList())

private fun looksLikeWebp(bytes: ByteArray): Boolean = bytes.size >= 12 &&
    bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() &&
    bytes[3] == 0x46.toByte() && bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
    bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()

private fun detectAudioType(bytes: ByteArray): String? {
    if (bytes.size < 4) return null
    if (bytes[0] == 0x49.toByte() && bytes[1] == 0x44.toByte() && bytes[2] == 0x33.toByte() ||
        bytes[0] == 0xff.toByte() && (bytes[1].toInt() and 0xe0) == 0xe0
    ) return "audio/mpeg"
    if (bytes[0] == 0xff.toByte() && (bytes[1].toInt() and 0xf6) == 0xf0) return "audio/aac"
    if (bytes.size >= 12 && bytes.copyOfRange(4, 8).contentEquals("ftyp".encodeToByteArray())) return "audio/mp4"
    if (bytes[0] == 0x1a.toByte() && bytes[1] == 0x45.toByte() &&
        bytes[2] == 0xdf.toByte() && bytes[3] == 0xa3.toByte()) return "audio/webm"
    if (bytes.size >= 12 && bytes.copyOfRange(0, 4).contentEquals("RIFF".encodeToByteArray()) &&
        bytes.copyOfRange(8, 12).contentEquals("WAVE".encodeToByteArray())) return "audio/wav"
    if (bytes.copyOfRange(0, 4).contentEquals("fLaC".encodeToByteArray())) return "audio/flac"
    return null
}
