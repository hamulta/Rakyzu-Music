package my.id.rakyzumusic.core.model

@kotlinx.serialization.Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val artistId: String = "",
    val albumId: String = "",
    val albumTitle: String = "",
    val discNumber: Int = 1,
    val trackNumber: Int = 1,
    val isExplicit: Boolean = false,
)

data class Artist(
    val id: String,
    val name: String,
)

data class Album(
    val id: String,
    val artistId: String,
    val title: String,
    val releaseDate: String?,
)

data class CatalogSnapshot(
    val artists: List<Artist>,
    val albums: List<Album>,
    val tracks: List<Track>,
    val lastSyncedAtEpochMillis: Long?,
    val editorialShelves: List<EditorialShelf> = emptyList(),
) {
    val isEmpty: Boolean
        get() = artists.isEmpty() && albums.isEmpty() && tracks.isEmpty()
}

data class EditorialShelf(
    val id: String,
    val title: String,
    val subtitle: String?,
    val position: Int,
    val tracks: List<Track>,
    val hasCustomArtwork: Boolean = false,
)

data class HomeFeedSnapshot(
    val catalog: CatalogSnapshot,
    val recentlyPlayed: List<Track>,
    val listeningHistory: List<ListeningHistoryItem> = emptyList(),
    val recommendations: List<PersonalizedTrack> = emptyList(),
    val mixes: List<PersonalizedCollection> = emptyList(),
    val radioStations: List<PersonalizedCollection> = emptyList(),
    val tasteProfile: TasteProfile = TasteProfile(),
)

data class ListeningHistoryItem(
    val track: Track,
    val lastPlayedAtEpochMillis: Long,
    val playCount: Int,
)

data class PersonalizedTrack(
    val track: Track,
    val reason: String,
)

data class PersonalizedCollection(
    val id: String,
    val title: String,
    val subtitle: String,
    val tracks: List<Track>,
)

enum class DiscoveryMode(val storageValue: String) {
    Familiar("familiar"),
    Balanced("balanced"),
    Explore("explore"),
    ;

    companion object {
        fun fromStorage(value: String?): DiscoveryMode = entries
            .firstOrNull { it.storageValue == value }
            ?: Balanced
    }
}

data class TasteProfile(
    val enabled: Boolean = true,
    val discoveryMode: DiscoveryMode = DiscoveryMode.Balanced,
    val signalCount: Int = 0,
    val excludedTrackIds: Set<String> = emptySet(),
    val hiddenTrackIds: Set<String> = emptySet(),
) {
    val isPersonalized: Boolean
        get() = enabled && signalCount > 0
}

enum class LibraryItemKind {
    Track,
    Album,
    Artist,
}

data class LibraryAlbum(
    val album: Album,
    val artistName: String,
)

data class LibrarySnapshot(
    val likedTracks: List<Track>,
    val savedAlbums: List<LibraryAlbum>,
    val followedArtists: List<Artist>,
    val lastSyncedAtEpochMillis: Long?,
    val likedTrackSavedAtEpochMillis: Map<String, Long> = emptyMap(),
    val savedAlbumSavedAtEpochMillis: Map<String, Long> = emptyMap(),
    val followedArtistSavedAtEpochMillis: Map<String, Long> = emptyMap(),
    val pendingMutationCount: Int = 0,
) {
    val isEmpty: Boolean
        get() = likedTracks.isEmpty() && savedAlbums.isEmpty() && followedArtists.isEmpty()
}

fun Track.formattedDuration(): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
