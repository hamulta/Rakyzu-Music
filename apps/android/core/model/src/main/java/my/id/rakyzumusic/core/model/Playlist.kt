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
    val ownerId: String = "",
    val visibility: PlaylistVisibility = PlaylistVisibility.Private,
    val accessRole: PlaylistRole = PlaylistRole.Owner,
    val isFollowing: Boolean = false,
)

data class PlaylistSnapshot(
    val playlists: List<PlaylistSummary>,
    val lastSyncedAtEpochMillis: Long?,
    val hasMore: Boolean = false,
)

@kotlinx.serialization.Serializable
data class PlaylistItem(val trackId: String, val track: Track?)

@kotlinx.serialization.Serializable
data class PlaylistDetail(
    val playlist: PlaylistSummary,
    val items: List<PlaylistItem>,
    val members: List<PlaylistMember> = emptyList(),
    val totalItems: Int = playlist.trackCount,
    val nextOffset: Int? = null,
) {
    val playableTracks: List<Track> get() = items.mapNotNull { it.track }
}

@kotlinx.serialization.Serializable
enum class PlaylistVisibility { Private, Public }

@kotlinx.serialization.Serializable
enum class PlaylistRole { Owner, Editor, Viewer, Follower }

@kotlinx.serialization.Serializable
data class PlaylistMember(
    val userId: String,
    val displayName: String,
    val role: PlaylistRole,
    val isCurrentUser: Boolean,
)

@kotlinx.serialization.Serializable
data class PlaylistInvite(
    val token: String,
    val role: PlaylistRole,
    val expiresAtEpochMillis: Long,
) {
    val shareLink: String get() = "my.id.rakyzumusic://playlist-invite/$token"
}
