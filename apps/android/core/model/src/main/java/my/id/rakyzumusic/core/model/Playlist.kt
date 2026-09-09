package my.id.rakyzumusic.core.model

@kotlinx.serialization.Serializable
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

@kotlinx.serialization.Serializable
data class PlaylistItem(val trackId: String, val track: Track?)

@kotlinx.serialization.Serializable
data class PlaylistDetail(val playlist: PlaylistSummary, val items: List<PlaylistItem>) {
    val playableTracks: List<Track> get() = items.mapNotNull { it.track }
}
