package my.id.rakyzumusic.core.database.catalog

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import my.id.rakyzumusic.core.model.LibraryAlbum
import my.id.rakyzumusic.core.model.LibraryItemKind
import my.id.rakyzumusic.core.model.LibrarySnapshot
import my.id.rakyzumusic.core.model.Track

data class StoredLibrarySelection(
    val kind: LibraryItemKind,
    val itemId: String,
    val savedAtEpochMillis: Long,
)

interface LibraryLocalDataSource {
    fun observeLibrary(userId: String): Flow<LibrarySnapshot>

    suspend fun replaceLibrary(
        userId: String,
        selections: List<StoredLibrarySelection>,
        syncedAtEpochMillis: Long,
    )

    suspend fun setLibraryItem(
        userId: String,
        selection: StoredLibrarySelection,
        saved: Boolean,
    )
}

internal class RoomLibraryLocalDataSource(
    private val database: RakyzuDatabase,
) : LibraryLocalDataSource {
    private val dao = database.catalogDao()

    override fun observeLibrary(userId: String): Flow<LibrarySnapshot> = database.invalidationTracker
        .createFlow(
            "artists",
            "albums",
            "tracks",
            "library_liked_tracks",
            "library_saved_albums",
            "library_followed_artists",
            "sync_metadata",
        )
        .map {
            val catalog = dao.readSnapshot().toDomain()
            val tracksById = catalog.tracks.associateBy(Track::id)
            val albumsById = catalog.albums.associateBy { album -> album.id }
            val artistsById = catalog.artists.associateBy { artist -> artist.id }
            val likedTrackRows = dao.getLikedTracks(userId)
            val savedAlbumRows = dao.getSavedAlbums(userId)
            val followedArtistRows = dao.getFollowedArtists(userId)
            LibrarySnapshot(
                likedTracks = likedTrackRows.mapNotNull { tracksById[it.itemId] },
                savedAlbums = savedAlbumRows.mapNotNull { stored ->
                    val album = albumsById[stored.itemId] ?: return@mapNotNull null
                    LibraryAlbum(
                        album = album,
                        artistName = artistsById[album.artistId]?.name.orEmpty(),
                    )
                },
                followedArtists = followedArtistRows
                    .mapNotNull { artistsById[it.itemId] },
                lastSyncedAtEpochMillis = dao.getLastSuccessfulSyncEpochMillis(
                    CatalogDao.librarySyncKey(userId),
                ),
                likedTrackSavedAtEpochMillis = likedTrackRows
                    .filter { it.itemId in tracksById }
                    .associate { it.itemId to it.savedAtEpochMillis },
                savedAlbumSavedAtEpochMillis = savedAlbumRows
                    .filter { it.itemId in albumsById }
                    .associate { it.itemId to it.savedAtEpochMillis },
                followedArtistSavedAtEpochMillis = followedArtistRows
                    .filter { it.itemId in artistsById }
                    .associate { it.itemId to it.savedAtEpochMillis },
            )
        }

    override suspend fun replaceLibrary(
        userId: String,
        selections: List<StoredLibrarySelection>,
        syncedAtEpochMillis: Long,
    ) {
        require(userId.isNotBlank() && syncedAtEpochMillis >= 0L)
        dao.replaceLibrary(
            userId = userId,
            likedTracks = selections.filter { it.kind == LibraryItemKind.Track }.map {
                LibraryLikedTrackEntity(userId, it.itemId, it.savedAtEpochMillis)
            },
            savedAlbums = selections.filter { it.kind == LibraryItemKind.Album }.map {
                LibrarySavedAlbumEntity(userId, it.itemId, it.savedAtEpochMillis)
            },
            followedArtists = selections.filter { it.kind == LibraryItemKind.Artist }.map {
                LibraryFollowedArtistEntity(userId, it.itemId, it.savedAtEpochMillis)
            },
            syncedAtEpochMillis = syncedAtEpochMillis,
        )
    }

    override suspend fun setLibraryItem(
        userId: String,
        selection: StoredLibrarySelection,
        saved: Boolean,
    ) {
        require(userId.isNotBlank() && selection.itemId.isNotBlank())
        when (selection.kind) {
            LibraryItemKind.Track -> if (saved) {
                dao.insertLikedTrack(
                    LibraryLikedTrackEntity(userId, selection.itemId, selection.savedAtEpochMillis),
                )
            } else {
                dao.deleteLikedTrack(userId, selection.itemId)
            }
            LibraryItemKind.Album -> if (saved) {
                dao.insertSavedAlbum(
                    LibrarySavedAlbumEntity(userId, selection.itemId, selection.savedAtEpochMillis),
                )
            } else {
                dao.deleteSavedAlbum(userId, selection.itemId)
            }
            LibraryItemKind.Artist -> if (saved) {
                dao.insertFollowedArtist(
                    LibraryFollowedArtistEntity(userId, selection.itemId, selection.savedAtEpochMillis),
                )
            } else {
                dao.deleteFollowedArtist(userId, selection.itemId)
            }
        }
    }
}
