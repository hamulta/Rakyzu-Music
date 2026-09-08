package my.id.rakyzumusic.core.data.library

import io.github.jan.supabase.postgrest.Postgrest
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import my.id.rakyzumusic.core.model.LibraryItemKind

internal class SupabaseLibraryRemoteDataSource(
    private val postgrest: Postgrest,
) : LibraryRemoteDataSource {
    override suspend fun fetchSyncAnchor(): Long =
        postgrest.rpc(GET_LIBRARY_SYNC_ANCHOR_FUNCTION)
            .decodeSingle<LibrarySyncAnchorRow>()
            .sequence

    override suspend fun fetchLibraryPage(
        cursor: RemoteLibraryCursor?,
        limit: Int,
    ): RemoteLibraryPage {
        val rows = postgrest.rpc(
            function = GET_LIBRARY_PAGE_FUNCTION,
            parameters = buildJsonObject {
                put("page_size", limit)
                if (cursor == null) {
                    put("cursor_saved_at", JsonNull)
                    put("cursor_kind", JsonNull)
                    put("cursor_item_id", JsonNull)
                } else {
                    put("cursor_saved_at", Instant.ofEpochMilli(cursor.savedAtEpochMillis).toString())
                    put("cursor_kind", cursor.kind.wireName())
                    put("cursor_item_id", cursor.itemId)
                }
            },
        )
            .decodeList<LibrarySelectionRow>()
        val selections = rows.map(LibrarySelectionRow::toRemoteSelection)
        val last = selections.lastOrNull()
        return RemoteLibraryPage(
            selections = selections,
            nextCursor = last?.takeIf { selections.size == limit }?.let {
                RemoteLibraryCursor(it.savedAtEpochMillis, it.kind, it.itemId)
            },
        )
    }

    override suspend fun fetchLibraryChanges(
        afterSequence: Long,
        limit: Int,
    ): RemoteLibraryChangePage {
        val rows = postgrest.rpc(
            function = GET_LIBRARY_CHANGES_FUNCTION,
            parameters = buildJsonObject {
                put("after_sequence", afterSequence)
                put("page_size", limit)
            },
        ).decodeList<LibraryChangeRow>()
        return RemoteLibraryChangePage(
            changes = rows.map { row ->
                RemoteLibraryChange(
                    sequence = row.sequence,
                    kind = row.kind.toLibraryItemKind(),
                    itemId = row.itemId,
                    saved = row.saved,
                    savedAtEpochMillis = row.savedAt.toEpochMillis(),
                )
            },
            hasMore = rows.size == limit,
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
        const val GET_LIBRARY_SYNC_ANCHOR_FUNCTION = "get_library_sync_anchor"
        const val GET_LIBRARY_PAGE_FUNCTION = "get_library_items_page"
        const val GET_LIBRARY_CHANGES_FUNCTION = "get_library_changes"
        const val SET_LIBRARY_ITEM_FUNCTION = "set_library_item"
    }
}

@Serializable
private data class LibrarySyncAnchorRow(val sequence: Long)

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

@Serializable
private data class LibraryChangeRow(
    val sequence: Long,
    val kind: String,
    @SerialName("item_id") val itemId: String,
    val saved: Boolean,
    @SerialName("saved_at") val savedAt: String,
)

private fun LibrarySelectionRow.toRemoteSelection() = RemoteLibrarySelection(
    kind = kind.toLibraryItemKind(),
    itemId = itemId,
    savedAtEpochMillis = savedAt.toEpochMillis(),
)

private fun String.toEpochMillis(): Long = runCatching {
    Instant.parse(this).toEpochMilli()
}.getOrElse { throw InvalidLibraryPayloadException() }

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
