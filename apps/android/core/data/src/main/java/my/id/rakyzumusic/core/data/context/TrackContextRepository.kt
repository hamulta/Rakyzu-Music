package my.id.rakyzumusic.core.data.context

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import my.id.rakyzumusic.core.data.media.AccessTokenProvider
import my.id.rakyzumusic.core.data.media.RakyzuApiConfiguration
import my.id.rakyzumusic.core.database.catalog.TrackContextLocalDataSource
import my.id.rakyzumusic.core.model.LyricLine
import my.id.rakyzumusic.core.model.LyricsKind
import my.id.rakyzumusic.core.model.ReleaseNotificationPreference
import my.id.rakyzumusic.core.model.ReleaseNotificationSettings
import my.id.rakyzumusic.core.model.TrackContext
import my.id.rakyzumusic.core.model.TrackCredit
import my.id.rakyzumusic.core.model.TrackCreditRole
import my.id.rakyzumusic.core.model.TrackLyrics

interface TrackContextRepository {
    fun observe(userId: String, trackId: String): Flow<TrackContext?>

    suspend fun refresh(userId: String, trackId: String): TrackContextRefreshResult

    suspend fun reconcileCatalog(userId: String, trackIds: Set<String>): Int

    suspend fun cachedTrackCount(userId: String): Int

    suspend fun notificationSettings(): NotificationSettingsResult

    suspend fun updateNotificationSettings(
        preference: ReleaseNotificationPreference,
    ): NotificationSettingsResult
}

sealed interface TrackContextRefreshResult {
    data class Success(val context: TrackContext) : TrackContextRefreshResult
    data class Failure(val reason: TrackContextFailure) : TrackContextRefreshResult
}

sealed interface NotificationSettingsResult {
    data class Success(val settings: ReleaseNotificationSettings) : NotificationSettingsResult
    data class Failure(val reason: TrackContextFailure) : NotificationSettingsResult
}

enum class TrackContextFailure { InvalidRequest, NotAuthenticated, Forbidden, Network, Unavailable }

internal class AuthenticatedTrackContextRepository(
    configuration: RakyzuApiConfiguration,
    private val tokens: AccessTokenProvider,
    private val local: TrackContextLocalDataSource,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : TrackContextRepository {
    private val origin = configuration.normalizedOriginOrNull()
    private val json = Json { ignoreUnknownKeys = true }

    override fun observe(userId: String, trackId: String): Flow<TrackContext?> =
        if (userId.isBlank() || !UUID.matches(trackId)) flowOf(null)
        else local.observe(userId, trackId.lowercase()).map { context ->
            context?.takeIf { it.isUsableFor(trackId.lowercase(), currentTimeMillis()) }
        }

    override suspend fun refresh(userId: String, trackId: String): TrackContextRefreshResult =
        withContext(Dispatchers.IO) {
            if (userId.isBlank() || !UUID.matches(trackId)) {
                return@withContext TrackContextRefreshResult.Failure(
                    TrackContextFailure.InvalidRequest,
                )
            }
            val normalizedId = trackId.lowercase()
            val response = request("GET", "/v1/tracks/$normalizedId/context")
            if (response.status != 200 || response.body == null) {
                return@withContext TrackContextRefreshResult.Failure(response.toFailure())
            }
            val context = parseContext(response.body, normalizedId, currentTimeMillis())
                ?: return@withContext TrackContextRefreshResult.Failure(
                    TrackContextFailure.Unavailable,
                )
            local.replace(userId, context)
            TrackContextRefreshResult.Success(context)
        }

    override suspend fun reconcileCatalog(userId: String, trackIds: Set<String>): Int =
        local.reconcileCatalog(userId, trackIds.filter(UUID::matches).map(String::lowercase).toSet())

    override suspend fun cachedTrackCount(userId: String): Int = local.stats(userId).trackCount

    override suspend fun notificationSettings(): NotificationSettingsResult =
        withContext(Dispatchers.IO) {
            val response = request("GET", "/v1/account/release-notifications")
            if (response.status != 200 || response.body == null) {
                return@withContext NotificationSettingsResult.Failure(response.toFailure())
            }
            parseNotificationSettings(response.body)?.let(NotificationSettingsResult::Success)
                ?: NotificationSettingsResult.Failure(TrackContextFailure.Unavailable)
        }

    override suspend fun updateNotificationSettings(
        preference: ReleaseNotificationPreference,
    ): NotificationSettingsResult = withContext(Dispatchers.IO) {
        val wire = when (preference) {
            ReleaseNotificationPreference.Off -> "off"
            ReleaseNotificationPreference.FollowedArtists -> "followed_artists"
            ReleaseNotificationPreference.AllSavedArtists -> "all_saved_artists"
        }
        val response = request(
            method = "PUT",
            path = "/v1/account/release-notifications",
            body = "{\"preference\":\"$wire\"}",
        )
        if (response.status != 200 || response.body == null) {
            return@withContext NotificationSettingsResult.Failure(response.toFailure())
        }
        parseNotificationSettings(response.body)?.let(NotificationSettingsResult::Success)
            ?: NotificationSettingsResult.Failure(TrackContextFailure.Unavailable)
    }

    private fun request(method: String, path: String, body: String? = null): ApiResponse {
        val base = origin ?: return ApiResponse(503, null)
        val token = tokens.currentAccessTokenOrNull()?.takeIf(String::isNotBlank)
            ?: return ApiResponse(401, null)
        val connection = URI("$base$path").toURL().openConnection() as HttpURLConnection
        return try {
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.requestMethod = method
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                val bytes = body.encodeToByteArray()
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            val value = if (status in 200..299) {
                readBounded(connection.inputStream)?.let {
                    runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull()
                }
            } else null
            ApiResponse(status, value)
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            ApiResponse(0, null)
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(input: java.io.InputStream): String? {
        val output = ByteArrayOutputStream()
        input.use {
            val buffer = ByteArray(8_192)
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                if (output.size() + count > MAX_CONTEXT_BYTES) return null
                output.write(buffer, 0, count)
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun parseContext(value: JsonObject, expectedTrackId: String, now: Long): TrackContext? {
        if (value.string("trackId")?.lowercase() != expectedTrackId) return null
        val revision = value.string("revision")?.takeIf { it.length in 1..120 } ?: return null
        val lyricsObject = value["lyrics"] as? JsonObject ?: return null
        val kind = when (lyricsObject.string("kind")) {
            "unavailable" -> LyricsKind.Unavailable
            "plain" -> LyricsKind.Plain
            "time_synced" -> LyricsKind.TimeSynced
            else -> return null
        }
        val lines = (lyricsObject["lines"] as? JsonArray).orEmpty().mapNotNull { raw ->
            val line = raw as? JsonObject ?: return@mapNotNull null
            val text = line.string("text")?.takeIf { it.isNotBlank() && it.length <= 500 }
                ?: return@mapNotNull null
            val start = (line["startTimeMs"] as? JsonPrimitive)?.longOrNull
            if (start != null && start < 0) return@mapNotNull null
            LyricLine(text, start)
        }.take(MAX_LYRIC_LINES)
        val lyrics = TrackLyrics(
            kind = kind,
            lines = if (kind == LyricsKind.Unavailable) emptyList() else lines,
            providerName = lyricsObject.string("providerName")?.take(120),
            providerNotice = lyricsObject.string("providerNotice")?.take(240),
        )
        if (kind != LyricsKind.Unavailable && !lyrics.isDisplayable) return null
        val credits = (value["credits"] as? JsonArray).orEmpty().mapNotNull { raw ->
            val credit = raw as? JsonObject ?: return@mapNotNull null
            val role = when (credit.string("role")) {
                "primary_artist" -> TrackCreditRole.PrimaryArtist
                "featured_artist" -> TrackCreditRole.FeaturedArtist
                "songwriter" -> TrackCreditRole.Songwriter
                "producer" -> TrackCreditRole.Producer
                "performer" -> TrackCreditRole.Performer
                else -> return@mapNotNull null
            }
            val name = credit.string("displayName")
                ?.takeIf { it.isNotBlank() && it.length <= 120 } ?: return@mapNotNull null
            TrackCredit(name, role, credit.string("sourceName")?.take(120))
        }.take(MAX_CREDITS)
        val ttlSeconds = (value["cacheTtlSeconds"] as? JsonPrimitive)?.longOrNull
            ?.coerceIn(MIN_CACHE_TTL_SECONDS, MAX_CACHE_TTL_SECONDS)
            ?: DEFAULT_CACHE_TTL_SECONDS
        return TrackContext(
            trackId = expectedTrackId,
            lyrics = lyrics,
            credits = credits,
            catalogRevision = revision,
            cachedAtEpochMillis = now,
            expiresAtEpochMillis = now + ttlSeconds * 1_000L,
        )
    }

    private fun parseNotificationSettings(value: JsonObject): ReleaseNotificationSettings? {
        val preference = when (value.string("preference")) {
            "off" -> ReleaseNotificationPreference.Off
            "followed_artists" -> ReleaseNotificationPreference.FollowedArtists
            "all_saved_artists" -> ReleaseNotificationPreference.AllSavedArtists
            else -> return null
        }
        val updatedAt = (value["updatedAtEpochMillis"] as? JsonPrimitive)?.longOrNull ?: 0L
        return ReleaseNotificationSettings(preference, updatedAt.coerceAtLeast(0L))
    }

    private fun ApiResponse.toFailure(): TrackContextFailure = when (status) {
        400, 404, 422 -> TrackContextFailure.InvalidRequest
        401 -> TrackContextFailure.NotAuthenticated
        403 -> TrackContextFailure.Forbidden
        0 -> TrackContextFailure.Network
        else -> TrackContextFailure.Unavailable
    }

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull

    private data class ApiResponse(val status: Int, val body: JsonObject?)

    private companion object {
        val UUID = Regex(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
            RegexOption.IGNORE_CASE,
        )
        const val MAX_CONTEXT_BYTES = 1_048_576
        const val MAX_LYRIC_LINES = 2_000
        const val MAX_CREDITS = 200
        const val MIN_CACHE_TTL_SECONDS = 3_600L
        const val DEFAULT_CACHE_TTL_SECONDS = 86_400L
        const val MAX_CACHE_TTL_SECONDS = 604_800L
    }
}

internal data object UnavailableTrackContextRepository : TrackContextRepository {
    override fun observe(userId: String, trackId: String): Flow<TrackContext?> = flowOf(null)
    override suspend fun refresh(userId: String, trackId: String) =
        TrackContextRefreshResult.Failure(TrackContextFailure.Unavailable)
    override suspend fun reconcileCatalog(userId: String, trackIds: Set<String>) = 0
    override suspend fun cachedTrackCount(userId: String) = 0
    override suspend fun notificationSettings() =
        NotificationSettingsResult.Failure(TrackContextFailure.Unavailable)
    override suspend fun updateNotificationSettings(preference: ReleaseNotificationPreference) =
        NotificationSettingsResult.Failure(TrackContextFailure.Unavailable)
}
