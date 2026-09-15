package my.id.rakyzumusic.core.data.admin

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import my.id.rakyzumusic.core.data.media.AccessTokenProvider
import my.id.rakyzumusic.core.data.media.RakyzuApiConfiguration

enum class StaffRole(val wireName: String, val displayName: String) {
    Officer("officer", "Officer"),
    Supervisor("supervisor", "Supervisor"),
    Manager("manager", "Manager"),
    CLevelExecutive("c_level_executive", "C-Level Executive"),
    Ceo("ceo", "CEO");

    companion object {
        fun fromWire(value: String?): StaffRole? = entries.firstOrNull { it.wireName == value }
    }
}

enum class StaffPermission(val wireName: String) {
    AdminAccess("admin.access"),
    ModerationView("moderation.view"),
    ModerationTriage("moderation.triage"),
    ModerationDecide("moderation.decide"),
    CatalogDraft("catalog.draft"),
    CatalogUploadAudio("catalog.upload_audio"),
    CatalogPublish("catalog.publish"),
    StaffManage("staff.manage");

    companion object {
        fun fromWire(value: String): StaffPermission? = entries.firstOrNull { it.wireName == value }
    }
}

data class StaffAccessContext(
    val isStaff: Boolean,
    val role: StaffRole?,
    val displayRole: String?,
    val fullAccess: Boolean,
    val permissions: Set<StaffPermission>,
) {
    fun can(permission: StaffPermission): Boolean = fullAccess || permission in permissions
}

data class ModerationCase(
    val id: String,
    val subjectType: String,
    val subjectId: String,
    val reason: String,
    val status: String,
    val priority: Int,
    val assignedTo: String?,
)

data class CatalogDraft(
    val id: String,
    val kind: String,
    val title: String,
    val parentId: String?,
    val published: Boolean,
    val hasStandardAudio: Boolean? = null,
)

data class StaffAssignment(
    val userId: String,
    val email: String,
    val displayName: String,
    val role: StaffRole,
    val active: Boolean,
)

data class AdminDashboard(
    val context: StaffAccessContext,
    val moderationCases: List<ModerationCase> = emptyList(),
    val catalog: List<CatalogDraft> = emptyList(),
    val staff: List<StaffAssignment> = emptyList(),
)

sealed interface AdminDashboardResult {
    data class Success(val dashboard: AdminDashboard) : AdminDashboardResult
    data object NotAuthenticated : AdminDashboardResult
    data object Unavailable : AdminDashboardResult
}

sealed interface AdminActionResult {
    data class Success(val message: String) : AdminActionResult
    data class Failure(val reason: AdminFailure) : AdminActionResult
}

enum class AdminFailure {
    InvalidInput,
    NotAuthenticated,
    Forbidden,
    PayloadTooLarge,
    ServiceUnavailable,
}

interface AdminRepository {
    suspend fun loadDashboard(): AdminDashboardResult
    suspend fun createArtist(name: String): AdminActionResult
    suspend fun createAlbum(artistId: String, title: String, releaseDate: String?): AdminActionResult
    suspend fun createTrack(
        albumId: String,
        title: String,
        durationMs: Int,
        discNumber: Int,
        trackNumber: Int,
        explicit: Boolean,
    ): AdminActionResult
    suspend fun uploadAudio(trackId: String, quality: String, bytes: ByteArray): AdminActionResult
    suspend fun publishAlbum(albumId: String): AdminActionResult
    suspend fun createModerationCase(
        subjectType: String,
        subjectId: String,
        reason: String,
        priority: Int,
    ): AdminActionResult
    suspend fun moderate(caseId: String, action: String, notes: String): AdminActionResult
    suspend fun assignStaff(email: String, role: StaffRole, active: Boolean): AdminActionResult
}

internal class AuthenticatedAdminRepository(
    configuration: RakyzuApiConfiguration,
    private val tokens: AccessTokenProvider,
) : AdminRepository {
    private val origin = configuration.normalizedOriginOrNull()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun loadDashboard(): AdminDashboardResult = withContext(Dispatchers.IO) {
        val contextResponse = request("GET", "/v1/admin/context")
        if (contextResponse.status == 401) return@withContext AdminDashboardResult.NotAuthenticated
        val context = contextResponse.body?.let(::parseContext)
            ?: return@withContext AdminDashboardResult.Unavailable
        if (!context.isStaff) {
            return@withContext AdminDashboardResult.Success(AdminDashboard(context))
        }
        val moderation = if (context.can(StaffPermission.ModerationView)) {
            val response = request("GET", "/v1/admin/moderation")
            if (response.status !in 200..299 || response.body == null) {
                return@withContext AdminDashboardResult.Unavailable
            }
            parseModeration(response.body)
        } else emptyList()
        val catalog = if (context.can(StaffPermission.CatalogDraft)) {
            val response = request("GET", "/v1/admin/catalog/drafts")
            if (response.status !in 200..299 || response.body == null) {
                return@withContext AdminDashboardResult.Unavailable
            }
            parseCatalog(response.body)
        } else emptyList()
        val staff = if (context.can(StaffPermission.StaffManage)) {
            val response = request("GET", "/v1/admin/staff")
            if (response.status !in 200..299 || response.body == null) {
                return@withContext AdminDashboardResult.Unavailable
            }
            parseStaff(response.body)
        } else emptyList()
        AdminDashboardResult.Success(AdminDashboard(context, moderation, catalog, staff))
    }

    override suspend fun createArtist(name: String): AdminActionResult = action(
        "POST", "/v1/admin/artists", buildJsonObject { put("name", name.trim()) },
        successMessage = "Artist draft created",
    )

    override suspend fun createAlbum(
        artistId: String,
        title: String,
        releaseDate: String?,
    ): AdminActionResult = action(
        "POST", "/v1/admin/albums", buildJsonObject {
            put("artistId", artistId.trim())
            put("title", title.trim())
            if (releaseDate.isNullOrBlank()) put("releaseDate", JsonNull)
            else put("releaseDate", releaseDate.trim())
        }, successMessage = "Album draft created",
    )

    override suspend fun createTrack(
        albumId: String,
        title: String,
        durationMs: Int,
        discNumber: Int,
        trackNumber: Int,
        explicit: Boolean,
    ): AdminActionResult = action(
        "POST", "/v1/admin/tracks", buildJsonObject {
            put("albumId", albumId.trim())
            put("title", title.trim())
            put("durationMs", durationMs)
            put("discNumber", discNumber)
            put("trackNumber", trackNumber)
            put("explicit", explicit)
        }, successMessage = "Track draft created",
    )

    override suspend fun uploadAudio(
        trackId: String,
        quality: String,
        bytes: ByteArray,
    ): AdminActionResult = withContext(Dispatchers.IO) {
        if (!UUID.matches(trackId) || quality !in AUDIO_QUALITIES ||
            bytes.size !in 4..MAX_AUDIO_BYTES || !looksLikeMp3(bytes)) {
            return@withContext AdminActionResult.Failure(AdminFailure.InvalidInput)
        }
        request(
            method = "PUT",
            path = "/v1/admin/tracks/${trackId.lowercase()}/audio/$quality",
            binary = bytes,
        ).toActionResult("Audio uploaded")
    }

    override suspend fun publishAlbum(albumId: String): AdminActionResult = action(
        "POST", "/v1/admin/albums/${albumId.trim().lowercase()}/publish",
        buildJsonObject {}, "Album published",
    )

    override suspend fun createModerationCase(
        subjectType: String,
        subjectId: String,
        reason: String,
        priority: Int,
    ): AdminActionResult = action(
        "POST", "/v1/admin/moderation", buildJsonObject {
            put("subjectType", subjectType)
            put("subjectId", subjectId.trim())
            put("reason", reason.trim())
            put("priority", priority)
        }, "Moderation case created",
    )

    override suspend fun moderate(
        caseId: String,
        action: String,
        notes: String,
    ): AdminActionResult = action(
        "PATCH", "/v1/admin/moderation/${caseId.trim().lowercase()}", buildJsonObject {
            put("action", action)
            put("notes", notes.trim())
        }, "Moderation case updated",
    )

    override suspend fun assignStaff(
        email: String,
        role: StaffRole,
        active: Boolean,
    ): AdminActionResult = action(
        "POST", "/v1/admin/staff", buildJsonObject {
            put("email", email.trim().lowercase())
            put("role", role.wireName)
            put("active", active)
        }, "Staff access updated",
    )

    private suspend fun action(
        method: String,
        path: String,
        body: JsonObject,
        successMessage: String,
    ): AdminActionResult = withContext(Dispatchers.IO) {
        request(method, path, body).toActionResult(successMessage)
    }

    private fun ApiResponse.toActionResult(message: String): AdminActionResult = when (status) {
        in 200..299 -> AdminActionResult.Success(message)
        400, 404, 409, 422 -> AdminActionResult.Failure(AdminFailure.InvalidInput)
        401 -> AdminActionResult.Failure(AdminFailure.NotAuthenticated)
        403 -> AdminActionResult.Failure(AdminFailure.Forbidden)
        413 -> AdminActionResult.Failure(AdminFailure.PayloadTooLarge)
        else -> AdminActionResult.Failure(AdminFailure.ServiceUnavailable)
    }

    private fun request(
        method: String,
        path: String,
        body: JsonObject? = null,
        binary: ByteArray? = null,
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
                connection.setRequestProperty(
                    "Content-Type",
                    if (binary == null) "application/json" else "audio/mpeg",
                )
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
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            ApiResponse(503, null)
        } finally {
            connection.disconnect()
        }
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

    private fun parseContext(element: JsonElement): StaffAccessContext? {
        val value = element as? JsonObject ?: return null
        val isStaff = value.boolean("isStaff") ?: return null
        if (!isStaff) return StaffAccessContext(false, null, null, false, emptySet())
        val role = StaffRole.fromWire(value.string("role")) ?: return null
        val permissions = value["permissions"]?.let { raw ->
            (raw as? JsonArray)?.mapNotNull { StaffPermission.fromWire(it.jsonPrimitive.content) }
                ?.toSet()
        } ?: return null
        return StaffAccessContext(
            isStaff = true,
            role = role,
            displayRole = value.string("displayRole") ?: role.displayName,
            fullAccess = value.boolean("fullAccess") == true,
            permissions = permissions,
        )
    }

    private fun parseModeration(element: JsonElement): List<ModerationCase> =
        (element as? JsonArray).orEmpty().mapNotNull { raw ->
            val item = raw as? JsonObject ?: return@mapNotNull null
            ModerationCase(
                id = item.string("id") ?: return@mapNotNull null,
                subjectType = item.string("subjectType") ?: return@mapNotNull null,
                subjectId = item.string("subjectId") ?: return@mapNotNull null,
                reason = item.string("reason") ?: return@mapNotNull null,
                status = item.string("status") ?: return@mapNotNull null,
                priority = item.int("priority") ?: return@mapNotNull null,
                assignedTo = item.string("assignedTo"),
            )
        }

    private fun parseCatalog(element: JsonElement): List<CatalogDraft> {
        val root = element as? JsonObject ?: return emptyList()
        fun items(name: String, kind: String): List<CatalogDraft> =
            (root[name] as? JsonArray).orEmpty().mapNotNull { raw ->
                val item = raw as? JsonObject ?: return@mapNotNull null
                CatalogDraft(
                    id = item.string("id") ?: return@mapNotNull null,
                    kind = kind,
                    title = item.string(if (kind == "artist") "name" else "title")
                        ?: return@mapNotNull null,
                    parentId = item.string(if (kind == "album") "artistId" else "albumId"),
                    published = item.boolean("published") ?: false,
                    hasStandardAudio = item.boolean("hasStandardAudio"),
                )
            }
        return items("artists", "artist") + items("albums", "album") + items("tracks", "track")
    }

    private fun parseStaff(element: JsonElement): List<StaffAssignment> =
        (element as? JsonArray).orEmpty().mapNotNull { raw ->
            val item = raw as? JsonObject ?: return@mapNotNull null
            StaffAssignment(
                userId = item.string("userId") ?: return@mapNotNull null,
                email = item.string("email") ?: return@mapNotNull null,
                displayName = item.string("displayName") ?: "Rakyzu Staff",
                role = StaffRole.fromWire(item.string("role")) ?: return@mapNotNull null,
                active = item.boolean("active") ?: false,
            )
        }

    private data class ApiResponse(val status: Int, val body: JsonElement?)

    private companion object {
        val UUID = Regex(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
            RegexOption.IGNORE_CASE,
        )
        val AUDIO_QUALITIES = setOf("low", "standard", "high")
        const val MAX_AUDIO_BYTES = 50 * 1024 * 1024
        const val MAX_JSON_BYTES = 1024 * 1024

        fun looksLikeMp3(bytes: ByteArray): Boolean = bytes.size >= 4 && (
            (bytes[0] == 0x49.toByte() && bytes[1] == 0x44.toByte() && bytes[2] == 0x33.toByte()) ||
                (bytes[0] == 0xff.toByte() && (bytes[1].toInt() and 0xe0) == 0xe0)
            )
    }
}

internal data object UnavailableAdminRepository : AdminRepository {
    override suspend fun loadDashboard() = AdminDashboardResult.Unavailable
    override suspend fun createArtist(name: String) = unavailable()
    override suspend fun createAlbum(artistId: String, title: String, releaseDate: String?) = unavailable()
    override suspend fun createTrack(
        albumId: String, title: String, durationMs: Int, discNumber: Int,
        trackNumber: Int, explicit: Boolean,
    ) = unavailable()
    override suspend fun uploadAudio(trackId: String, quality: String, bytes: ByteArray) = unavailable()
    override suspend fun publishAlbum(albumId: String) = unavailable()
    override suspend fun createModerationCase(
        subjectType: String, subjectId: String, reason: String, priority: Int,
    ) = unavailable()
    override suspend fun moderate(caseId: String, action: String, notes: String) = unavailable()
    override suspend fun assignStaff(email: String, role: StaffRole, active: Boolean) = unavailable()
    private fun unavailable() = AdminActionResult.Failure(AdminFailure.ServiceUnavailable)
}

private fun JsonObject.string(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull?.takeUnless { it == "null" }

private fun JsonObject.boolean(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull

private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
