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
    CatalogUploadArtwork("catalog.upload_artwork"),
    CatalogPublish("catalog.publish"),
    StaffManage("staff.manage"),
    ContentEnforce("content.enforce"),
    CatalogTeamManage("catalog.team_manage"),
    CatalogReview("catalog.review"),
    AuditView("audit.view"),
    AuditExport("audit.export"),
    GovernanceManage("governance.manage");

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

data class ContentEnforcement(
    val id: String,
    val subjectType: String,
    val subjectId: String,
    val action: String,
    val reason: String,
    val createdAt: String,
)

data class CatalogTeamMembership(
    val scopeType: String,
    val scopeId: String,
    val userId: String,
    val accessLevel: String,
    val active: Boolean,
)

data class CatalogLabel(
    val id: String,
    val name: String,
)

data class CatalogReview(
    val id: String,
    val reviewType: String,
    val targetType: String,
    val targetId: String,
    val status: String,
    val submissionNotes: String,
    val submittedAt: String,
)

data class ScheduledRelease(
    val albumId: String,
    val publishAt: String,
    val status: String,
)

data class AuditSummary(
    val events24h: Int,
    val deniedOrEnforced24h: Int,
    val highActivity: Boolean,
    val retentionDays: Int,
)

data class GovernanceDashboard(
    val enforcements: List<ContentEnforcement> = emptyList(),
    val labels: List<CatalogLabel> = emptyList(),
    val teams: List<CatalogTeamMembership> = emptyList(),
    val reviews: List<CatalogReview> = emptyList(),
    val schedules: List<ScheduledRelease> = emptyList(),
    val auditSummary: AuditSummary? = null,
)

data class AdminDashboard(
    val context: StaffAccessContext,
    val moderationCases: List<ModerationCase> = emptyList(),
    val catalog: List<CatalogDraft> = emptyList(),
    val staff: List<StaffAssignment> = emptyList(),
    val governance: GovernanceDashboard = GovernanceDashboard(),
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

sealed interface AdminAuditExportResult {
    data class Success(val csv: String) : AdminAuditExportResult
    data class Failure(val reason: AdminFailure) : AdminAuditExportResult
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
    suspend fun enforceContent(
        subjectType: String,
        subjectId: String,
        action: String,
        reason: String,
        caseId: String?,
    ): AdminActionResult
    suspend fun assignCatalogTeam(
        scopeType: String,
        scopeId: String,
        email: String,
        accessLevel: String,
        active: Boolean,
    ): AdminActionResult
    suspend fun createCatalogLabel(name: String): AdminActionResult
    suspend fun linkCatalogLabelArtist(labelId: String, artistId: String): AdminActionResult
    suspend fun uploadArtwork(albumId: String, bytes: ByteArray): AdminActionResult
    suspend fun submitReview(
        reviewType: String,
        targetId: String,
        notes: String,
    ): AdminActionResult
    suspend fun decideReview(reviewId: String, decision: String, notes: String): AdminActionResult
    suspend fun scheduleAlbum(albumId: String, publishAt: String): AdminActionResult
    suspend fun exportAudit(operation: String?, targetType: String?): AdminAuditExportResult
    suspend fun setAuditRetention(days: Int): AdminActionResult
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
        val governanceResponse = request("GET", "/v1/admin/governance")
        if (governanceResponse.status !in 200..299 || governanceResponse.body == null) {
            return@withContext AdminDashboardResult.Unavailable
        }
        AdminDashboardResult.Success(
            AdminDashboard(context, moderation, catalog, staff, parseGovernance(governanceResponse.body)),
        )
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

    override suspend fun enforceContent(
        subjectType: String,
        subjectId: String,
        action: String,
        reason: String,
        caseId: String?,
    ): AdminActionResult = action(
        "POST", "/v1/admin/enforcement", buildJsonObject {
            put("subjectType", subjectType)
            put("subjectId", subjectId.trim())
            put("action", action)
            put("reason", reason.trim())
            if (caseId.isNullOrBlank()) put("caseId", JsonNull) else put("caseId", caseId.trim())
        }, "Content enforcement recorded",
    )

    override suspend fun assignCatalogTeam(
        scopeType: String,
        scopeId: String,
        email: String,
        accessLevel: String,
        active: Boolean,
    ): AdminActionResult = action(
        "POST", "/v1/admin/catalog/teams", buildJsonObject {
            put("scopeType", scopeType)
            put("scopeId", scopeId.trim())
            put("email", email.trim().lowercase())
            put("accessLevel", accessLevel)
            put("active", active)
        }, "Catalog team access updated",
    )

    override suspend fun createCatalogLabel(name: String): AdminActionResult = action(
        "POST", "/v1/admin/catalog/labels", buildJsonObject { put("name", name.trim()) },
        "Catalog label created",
    )

    override suspend fun linkCatalogLabelArtist(
        labelId: String,
        artistId: String,
    ): AdminActionResult = action(
        "POST", "/v1/admin/catalog/labels/artists", buildJsonObject {
            put("labelId", labelId.trim())
            put("artistId", artistId.trim())
        }, "Artist linked to label",
    )

    override suspend fun uploadArtwork(albumId: String, bytes: ByteArray): AdminActionResult =
        withContext(Dispatchers.IO) {
            if (!UUID.matches(albumId) || bytes.size !in 12..MAX_ARTWORK_BYTES || !looksLikeWebp(bytes)) {
                return@withContext AdminActionResult.Failure(AdminFailure.InvalidInput)
            }
            request(
                method = "PUT",
                path = "/v1/admin/albums/${albumId.lowercase()}/artwork",
                binary = bytes,
                binaryContentType = "image/webp",
            ).toActionResult("Artwork uploaded for review")
        }

    override suspend fun submitReview(
        reviewType: String,
        targetId: String,
        notes: String,
    ): AdminActionResult = action(
        "POST", "/v1/admin/reviews", buildJsonObject {
            put("reviewType", reviewType)
            put("targetType", "album")
            put("targetId", targetId.trim())
            put("notes", notes.trim())
        }, "Catalog review submitted",
    )

    override suspend fun decideReview(
        reviewId: String,
        decision: String,
        notes: String,
    ): AdminActionResult = action(
        "PATCH", "/v1/admin/reviews/${reviewId.trim().lowercase()}", buildJsonObject {
            put("decision", decision)
            put("notes", notes.trim())
        }, "Catalog review decision recorded",
    )

    override suspend fun scheduleAlbum(albumId: String, publishAt: String): AdminActionResult = action(
        "POST", "/v1/admin/albums/${albumId.trim().lowercase()}/schedule", buildJsonObject {
            put("publishAt", publishAt.trim())
        }, "Album publication scheduled",
    )

    override suspend fun exportAudit(
        operation: String?,
        targetType: String?,
    ): AdminAuditExportResult = withContext(Dispatchers.IO) {
        val response = request("POST", "/v1/admin/audit/export", buildJsonObject {
            if (operation.isNullOrBlank()) put("operation", JsonNull) else put("operation", operation.trim())
            if (targetType.isNullOrBlank()) put("targetType", JsonNull) else put("targetType", targetType.trim())
            put("before", JsonNull)
            put("pageSize", 200)
        })
        if (response.status !in 200..299 || response.body !is JsonArray) {
            return@withContext AdminAuditExportResult.Failure(response.toFailure())
        }
        val lines = mutableListOf("id,operation,target_type,target_id,created_at")
        response.body.forEach { raw ->
            val item = raw as? JsonObject ?: return@forEach
            lines += listOf("id", "operation", "targetType", "targetId", "createdAt")
                .joinToString(",") { key -> csvCell(item.string(key).orEmpty()) }
        }
        AdminAuditExportResult.Success(lines.joinToString("\n"))
    }

    override suspend fun setAuditRetention(days: Int): AdminActionResult = action(
        "POST", "/v1/admin/audit/retention", buildJsonObject { put("retentionDays", days) },
        "Audit retention policy updated",
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
        binaryContentType: String = "audio/mpeg",
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
                    if (binary == null) "application/json" else binaryContentType,
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

    private fun parseGovernance(element: JsonElement): GovernanceDashboard {
        val root = element as? JsonObject ?: return GovernanceDashboard()
        val enforcements = root.array("enforcements").mapNotNull { raw ->
            val item = raw as? JsonObject ?: return@mapNotNull null
            ContentEnforcement(
                id = item.string("id") ?: return@mapNotNull null,
                subjectType = item.string("subjectType") ?: return@mapNotNull null,
                subjectId = item.string("subjectId") ?: return@mapNotNull null,
                action = item.string("action") ?: return@mapNotNull null,
                reason = item.string("reason") ?: return@mapNotNull null,
                createdAt = item.string("createdAt") ?: return@mapNotNull null,
            )
        }
        val teams = root.array("teams").mapNotNull { raw ->
            val item = raw as? JsonObject ?: return@mapNotNull null
            CatalogTeamMembership(
                scopeType = item.string("scopeType") ?: return@mapNotNull null,
                scopeId = item.string("scopeId") ?: return@mapNotNull null,
                userId = item.string("userId") ?: return@mapNotNull null,
                accessLevel = item.string("accessLevel") ?: return@mapNotNull null,
                active = item.boolean("active") ?: return@mapNotNull null,
            )
        }
        val labels = root.array("labels").mapNotNull { raw ->
            val item = raw as? JsonObject ?: return@mapNotNull null
            CatalogLabel(
                id = item.string("id") ?: return@mapNotNull null,
                name = item.string("name") ?: return@mapNotNull null,
            )
        }
        val reviews = root.array("reviews").mapNotNull { raw ->
            val item = raw as? JsonObject ?: return@mapNotNull null
            CatalogReview(
                id = item.string("id") ?: return@mapNotNull null,
                reviewType = item.string("reviewType") ?: return@mapNotNull null,
                targetType = item.string("targetType") ?: return@mapNotNull null,
                targetId = item.string("targetId") ?: return@mapNotNull null,
                status = item.string("status") ?: return@mapNotNull null,
                submissionNotes = item.string("submissionNotes").orEmpty(),
                submittedAt = item.string("submittedAt") ?: return@mapNotNull null,
            )
        }
        val schedules = root.array("schedules").mapNotNull { raw ->
            val item = raw as? JsonObject ?: return@mapNotNull null
            ScheduledRelease(
                albumId = item.string("albumId") ?: return@mapNotNull null,
                publishAt = item.string("publishAt") ?: return@mapNotNull null,
                status = item.string("status") ?: return@mapNotNull null,
            )
        }
        val audit = (root["auditSummary"] as? JsonObject)?.let { item ->
            AuditSummary(
                events24h = item.int("events24h") ?: return@let null,
                deniedOrEnforced24h = item.int("deniedOrEnforced24h") ?: return@let null,
                highActivity = item.boolean("highActivity") ?: return@let null,
                retentionDays = item.int("retentionDays") ?: return@let null,
            )
        }
        return GovernanceDashboard(enforcements, labels, teams, reviews, schedules, audit)
    }

    private fun ApiResponse.toFailure(): AdminFailure = when (status) {
        400, 404, 409, 422 -> AdminFailure.InvalidInput
        401 -> AdminFailure.NotAuthenticated
        403 -> AdminFailure.Forbidden
        413 -> AdminFailure.PayloadTooLarge
        else -> AdminFailure.ServiceUnavailable
    }

    private data class ApiResponse(val status: Int, val body: JsonElement?)

    private companion object {
        val UUID = Regex(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
            RegexOption.IGNORE_CASE,
        )
        val AUDIO_QUALITIES = setOf("low", "standard", "high")
        const val MAX_AUDIO_BYTES = 50 * 1024 * 1024
        const val MAX_ARTWORK_BYTES = 5 * 1024 * 1024
        const val MAX_JSON_BYTES = 1024 * 1024

        fun looksLikeMp3(bytes: ByteArray): Boolean = bytes.size >= 4 && (
            (bytes[0] == 0x49.toByte() && bytes[1] == 0x44.toByte() && bytes[2] == 0x33.toByte()) ||
                (bytes[0] == 0xff.toByte() && (bytes[1].toInt() and 0xe0) == 0xe0)
            )

        fun looksLikeWebp(bytes: ByteArray): Boolean = bytes.size >= 12 &&
            bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
            bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()

        fun csvCell(value: String): String = "\"${value.replace("\"", "\"\"")}\""
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
    override suspend fun enforceContent(
        subjectType: String, subjectId: String, action: String, reason: String, caseId: String?,
    ) = unavailable()
    override suspend fun assignCatalogTeam(
        scopeType: String, scopeId: String, email: String, accessLevel: String, active: Boolean,
    ) = unavailable()
    override suspend fun createCatalogLabel(name: String) = unavailable()
    override suspend fun linkCatalogLabelArtist(labelId: String, artistId: String) = unavailable()
    override suspend fun uploadArtwork(albumId: String, bytes: ByteArray) = unavailable()
    override suspend fun submitReview(reviewType: String, targetId: String, notes: String) = unavailable()
    override suspend fun decideReview(reviewId: String, decision: String, notes: String) = unavailable()
    override suspend fun scheduleAlbum(albumId: String, publishAt: String) = unavailable()
    override suspend fun exportAudit(operation: String?, targetType: String?) =
        AdminAuditExportResult.Failure(AdminFailure.ServiceUnavailable)
    override suspend fun setAuditRetention(days: Int) = unavailable()
    private fun unavailable() = AdminActionResult.Failure(AdminFailure.ServiceUnavailable)
}

private fun JsonObject.string(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull?.takeUnless { it == "null" }

private fun JsonObject.boolean(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull

private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

private fun JsonObject.array(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())
