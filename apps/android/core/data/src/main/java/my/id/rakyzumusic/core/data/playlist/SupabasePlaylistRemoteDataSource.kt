package my.id.rakyzumusic.core.data.playlist

import io.github.jan.supabase.postgrest.Postgrest
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import my.id.rakyzumusic.core.model.PlaylistDetail
import my.id.rakyzumusic.core.model.PlaylistSummary
import my.id.rakyzumusic.core.model.PlaylistInvite
import my.id.rakyzumusic.core.model.PlaylistRole
import my.id.rakyzumusic.core.model.PlaylistVisibility

internal class SupabasePlaylistRemoteDataSource(
    private val postgrest: Postgrest,
) : PlaylistRemoteDataSource {
    override suspend fun detail(id: String): PlaylistDetail = detailPage(id, 0, PAGE_SIZE)

    override suspend fun detailPage(id: String, offset: Int, limit: Int): PlaylistDetail = postgrest.rpc(
        "get_playlist_detail_page", buildJsonObject {
            put("playlist_id", id)
            put("page_offset", offset)
            put("page_size", limit)
        },
    ).decodeAs<PlaylistDetail>()

    override suspend fun mutate(id: String, revision: Long, mutation: PlaylistMutation): PlaylistDetail =
        mutateIdempotent(id, java.util.UUID.randomUUID().toString(), revision, mutation)

    override suspend fun mutateIdempotent(
        id: String,
        operationId: String,
        revision: Long,
        mutation: PlaylistMutation,
    ): PlaylistDetail = postgrest.rpc(
        "mutate_playlist_v2", mutationParameters(id, operationId, revision, mutation),
    ).decodeAs<PlaylistDetail>()

    private fun mutationParameters(
        id: String,
        operationId: String,
        revision: Long,
        mutation: PlaylistMutation,
    ) = buildJsonObject {
            put("playlist_id", id)
            put("operation_id", operationId)
            put("expected_revision", revision)
            when (mutation) {
                is PlaylistMutation.Add -> { put("action", "add"); put("track_id", mutation.trackId) }
                is PlaylistMutation.Remove -> { put("action", "remove"); put("track_id", mutation.trackId) }
                is PlaylistMutation.Reorder -> {
                    put("action", "reorder")
                    put("ordered_ids", JsonArray(mutation.trackIds.map(::JsonPrimitive)))
                }
                is PlaylistMutation.Metadata -> {
                    put("action", "metadata")
                    put("playlist_name", mutation.name)
                    put("playlist_description", mutation.description)
                }
            }
        }

    override suspend fun getMine(limit: Int): List<PlaylistSummary> = postgrest.rpc(
        function = GET_ACCESSIBLE_PLAYLISTS_FUNCTION,
        parameters = buildJsonObject { put("page_size", limit) },
    ).decodeList<PlaylistRow>().map(PlaylistRow::toDomain)

    override suspend fun getPage(
        limit: Int,
        beforeUpdatedAtEpochMillis: Long?,
        beforeId: String?,
    ): PlaylistRemotePage {
        val rows = postgrest.rpc(
            function = GET_ACCESSIBLE_PLAYLISTS_FUNCTION,
            parameters = buildJsonObject {
                put("page_size", limit)
                if (beforeUpdatedAtEpochMillis != null && beforeId != null) {
                    put("before_updated_at", Instant.ofEpochMilli(beforeUpdatedAtEpochMillis).toString())
                    put("before_id", beforeId)
                }
            },
        ).decodeList<PlaylistRow>()
        return PlaylistRemotePage(rows.map(PlaylistRow::toDomain), rows.firstOrNull()?.hasMore == true)
    }

    override suspend fun create(
        id: String,
        name: String,
        description: String,
    ): PlaylistSummary = postgrest.rpc(
        function = CREATE_PLAYLIST_FUNCTION,
        parameters = buildJsonObject {
            put("playlist_id", id)
            put("playlist_name", name)
            put("playlist_description", description)
        },
    ).decodeSingle<PlaylistRow>().toDomain()

    override suspend fun createInvite(id: String, role: PlaylistRole): PlaylistInvite = postgrest.rpc(
        "create_playlist_invite", buildJsonObject {
            put("playlist_id", id)
            put("invite_role", role.name.lowercase())
            put("valid_hours", 168)
        },
    ).decodeAs()

    override suspend fun acceptInvite(token: String): PlaylistDetail = postgrest.rpc(
        "accept_playlist_invite", buildJsonObject { put("invite_token", token) },
    ).decodeAs()

    override suspend fun removeMember(id: String, memberId: String): PlaylistDetail = postgrest.rpc(
        "remove_playlist_member", buildJsonObject {
            put("playlist_id", id)
            put("member_id", memberId)
        },
    ).decodeAs()

    override suspend fun leave(id: String) {
        postgrest.rpc("leave_playlist", buildJsonObject { put("playlist_id", id) })
    }

    override suspend fun setVisibility(
        id: String,
        revision: Long,
        visibility: PlaylistVisibility,
    ): PlaylistDetail = postgrest.rpc(
        "set_playlist_visibility", buildJsonObject {
            put("playlist_id", id)
            put("expected_revision", revision)
            put("requested_visibility", visibility.name.lowercase())
        },
    ).decodeAs()

    override suspend fun setFollowing(id: String, following: Boolean): PlaylistDetail = postgrest.rpc(
        "set_playlist_following", buildJsonObject {
            put("playlist_id", id)
            put("following", following)
        },
    ).decodeAs()

    private companion object {
        const val GET_ACCESSIBLE_PLAYLISTS_FUNCTION = "get_accessible_playlists"
        const val CREATE_PLAYLIST_FUNCTION = "create_playlist"
        const val PAGE_SIZE = 100
    }
}

@Serializable
private data class PlaylistRow(
    val id: String,
    @SerialName("owner_id") val ownerId: String = "",
    val name: String,
    val description: String,
    @SerialName("track_count") val trackCount: Int,
    val revision: Long,
    val visibility: String = "private",
    @SerialName("access_role") val accessRole: String = "Owner",
    @SerialName("is_following") val isFollowing: Boolean = false,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("has_more") val hasMore: Boolean = false,
)

private fun PlaylistRow.toDomain() = PlaylistSummary(
    id = id,
    name = name,
    description = description,
    trackCount = trackCount,
    revision = revision,
    createdAtEpochMillis = createdAt.toEpochMillis(),
    updatedAtEpochMillis = updatedAt.toEpochMillis(),
    ownerId = ownerId,
    visibility = if (visibility == "public") PlaylistVisibility.Public else PlaylistVisibility.Private,
    accessRole = runCatching { PlaylistRole.valueOf(accessRole) }.getOrDefault(PlaylistRole.Owner),
    isFollowing = isFollowing,
)

private fun String.toEpochMillis(): Long = runCatching {
    Instant.parse(this).toEpochMilli()
}.getOrElse { throw InvalidPlaylistPayloadException() }
