package my.id.rakyzumusic.core.data.playlist

import io.github.jan.supabase.postgrest.Postgrest
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import my.id.rakyzumusic.core.model.PlaylistSummary

internal class SupabasePlaylistRemoteDataSource(
    private val postgrest: Postgrest,
) : PlaylistRemoteDataSource {
    override suspend fun getMine(limit: Int): List<PlaylistSummary> = postgrest.rpc(
        function = GET_MY_PLAYLISTS_FUNCTION,
        parameters = buildJsonObject { put("page_size", limit) },
    ).decodeList<PlaylistRow>().map(PlaylistRow::toDomain)

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

    private companion object {
        const val GET_MY_PLAYLISTS_FUNCTION = "get_my_playlists"
        const val CREATE_PLAYLIST_FUNCTION = "create_playlist"
    }
}

@Serializable
private data class PlaylistRow(
    val id: String,
    val name: String,
    val description: String,
    @SerialName("track_count") val trackCount: Int,
    val revision: Long,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

private fun PlaylistRow.toDomain() = PlaylistSummary(
    id = id,
    name = name,
    description = description,
    trackCount = trackCount,
    revision = revision,
    createdAtEpochMillis = createdAt.toEpochMillis(),
    updatedAtEpochMillis = updatedAt.toEpochMillis(),
)

private fun String.toEpochMillis(): Long = runCatching {
    Instant.parse(this).toEpochMilli()
}.getOrElse { throw InvalidPlaylistPayloadException() }
