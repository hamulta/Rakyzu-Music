package my.id.rakyzumusic.core.data.library

import io.github.jan.supabase.postgrest.Postgrest
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import my.id.rakyzumusic.core.model.LibraryItemKind

internal class SupabaseLibraryRemoteDataSource(
    private val postgrest: Postgrest,
) : LibraryRemoteDataSource {
    override suspend fun fetchLibrary(): List<RemoteLibrarySelection> =
        postgrest.rpc(GET_LIBRARY_FUNCTION)
            .decodeList<LibrarySelectionRow>()
            .map { row ->
                RemoteLibrarySelection(
                    kind = row.kind.toLibraryItemKind(),
                    itemId = row.itemId,
                    savedAtEpochMillis = runCatching {
                        Instant.parse(row.savedAt).toEpochMilli()
                    }.getOrElse { throw InvalidLibraryPayloadException() },
                )
            }

    override suspend fun setSaved(
        kind: LibraryItemKind,
        itemId: String,
        saved: Boolean,
    ): Boolean = postgrest.rpc(
        function = SET_LIBRARY_ITEM_FUNCTION,
        parameters = buildJsonObject {
            put("item_kind", kind.wireName())
            put("item_id", itemId)
            put("should_save", saved)
        },
    ).decodeSingle<LibraryMutationRow>().success

    private companion object {
        const val GET_LIBRARY_FUNCTION = "get_library_items"
        const val SET_LIBRARY_ITEM_FUNCTION = "set_library_item"
    }
}

@Serializable
private data class LibrarySelectionRow(
    val kind: String,
    @SerialName("item_id") val itemId: String,
    @SerialName("saved_at") val savedAt: String,
)

@Serializable
private data class LibraryMutationRow(
    val success: Boolean,
)

private fun String.toLibraryItemKind(): LibraryItemKind = when (this) {
    "track" -> LibraryItemKind.Track
    "album" -> LibraryItemKind.Album
    "artist" -> LibraryItemKind.Artist
    else -> throw InvalidLibraryPayloadException()
}

private fun LibraryItemKind.wireName(): String = when (this) {
    LibraryItemKind.Track -> "track"
    LibraryItemKind.Album -> "album"
    LibraryItemKind.Artist -> "artist"
}
