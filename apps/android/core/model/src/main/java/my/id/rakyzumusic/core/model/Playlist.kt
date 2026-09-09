package my.id.rakyzumusic.core.model

data class PlaylistSummary(
    val id: String,
    val name: String,
    val description: String,
    val trackCount: Int,
    val revision: Long,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

data class PlaylistSnapshot(
    val playlists: List<PlaylistSummary>,
    val lastSyncedAtEpochMillis: Long?,
)
