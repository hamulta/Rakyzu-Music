package my.id.rakyzumusic.core.database.catalog

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction

internal data class StoredLibraryItem(
    val itemId: String,
    val savedAtEpochMillis: Long,
)

internal data class CatalogEntitySnapshot(
    val artists: List<ArtistEntity>,
    val albums: List<AlbumEntity>,
    val tracks: List<TrackEntity>,
    val editorialShelves: List<EditorialShelfEntity>,
    val editorialShelfTracks: List<EditorialShelfTrackEntity>,
    val lastSyncedAtEpochMillis: Long?,
)

@Dao
internal interface CatalogDao {
    @Query("SELECT * FROM artists ORDER BY name COLLATE NOCASE, id")
    suspend fun getArtists(): List<ArtistEntity>

    @Query("SELECT * FROM albums ORDER BY release_date DESC, title COLLATE NOCASE, id")
    suspend fun getAlbums(): List<AlbumEntity>

    @Query("SELECT * FROM tracks ORDER BY album_id, disc_number, track_number, id")
    suspend fun getTracks(): List<TrackEntity>

    @Query("SELECT * FROM editorial_shelves ORDER BY position, id")
    suspend fun getEditorialShelves(): List<EditorialShelfEntity>

    @Query("SELECT * FROM editorial_shelf_tracks ORDER BY shelf_id, position, track_id")
    suspend fun getEditorialShelfTracks(): List<EditorialShelfTrackEntity>

    @Query(
        """
        SELECT track_id FROM recently_played
        WHERE user_id = :userId
        ORDER BY played_at_epoch_ms DESC, track_id
        LIMIT :limit
        """,
    )
    suspend fun getRecentlyPlayedTrackIds(userId: String, limit: Int): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM tracks WHERE id = :trackId)")
    suspend fun containsTrack(trackId: String): Boolean

    @Query("SELECT last_successful_sync_epoch_ms FROM sync_metadata WHERE `key` = :key")
    suspend fun getLastSuccessfulSyncEpochMillis(key: String): Long?

    @Query(
        "SELECT track_id AS itemId, saved_at_epoch_ms AS savedAtEpochMillis " +
            "FROM library_liked_tracks WHERE user_id = :userId " +
            "ORDER BY saved_at_epoch_ms DESC, track_id",
    )
    suspend fun getLikedTracks(userId: String): List<StoredLibraryItem>

    @Query(
        "SELECT album_id AS itemId, saved_at_epoch_ms AS savedAtEpochMillis " +
            "FROM library_saved_albums WHERE user_id = :userId " +
            "ORDER BY saved_at_epoch_ms DESC, album_id",
    )
    suspend fun getSavedAlbums(userId: String): List<StoredLibraryItem>

    @Query(
        "SELECT artist_id AS itemId, saved_at_epoch_ms AS savedAtEpochMillis " +
            "FROM library_followed_artists WHERE user_id = :userId " +
            "ORDER BY saved_at_epoch_ms DESC, artist_id",
    )
    suspend fun getFollowedArtists(userId: String): List<StoredLibraryItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtists(artists: List<ArtistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbums(albums: List<AlbumEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncMetadata(metadata: SyncMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEditorialShelves(shelves: List<EditorialShelfEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEditorialShelfTracks(tracks: List<EditorialShelfTrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentlyPlayed(item: RecentlyPlayedEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLikedTracks(items: List<LibraryLikedTrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedAlbums(items: List<LibrarySavedAlbumEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFollowedArtists(items: List<LibraryFollowedArtistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLikedTrack(item: LibraryLikedTrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedAlbum(item: LibrarySavedAlbumEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFollowedArtist(item: LibraryFollowedArtistEntity)

    @Query("DELETE FROM library_liked_tracks WHERE user_id = :userId")
    suspend fun deleteLikedTracks(userId: String)

    @Query("DELETE FROM library_saved_albums WHERE user_id = :userId")
    suspend fun deleteSavedAlbums(userId: String)

    @Query("DELETE FROM library_followed_artists WHERE user_id = :userId")
    suspend fun deleteFollowedArtists(userId: String)

    @Query("DELETE FROM library_liked_tracks WHERE user_id = :userId AND track_id = :itemId")
    suspend fun deleteLikedTrack(userId: String, itemId: String)

    @Query("DELETE FROM library_saved_albums WHERE user_id = :userId AND album_id = :itemId")
    suspend fun deleteSavedAlbum(userId: String, itemId: String)

    @Query("DELETE FROM library_followed_artists WHERE user_id = :userId AND artist_id = :itemId")
    suspend fun deleteFollowedArtist(userId: String, itemId: String)

    @Query(
        """
        DELETE FROM recently_played
        WHERE user_id = :userId
          AND track_id NOT IN (
            SELECT track_id FROM recently_played
            WHERE user_id = :userId
            ORDER BY played_at_epoch_ms DESC, track_id
            LIMIT :limit
          )
        """,
    )
    suspend fun pruneRecentlyPlayed(userId: String, limit: Int)

    @Query("DELETE FROM tracks")
    suspend fun deleteTracks()

    @Query("DELETE FROM editorial_shelf_tracks")
    suspend fun deleteEditorialShelfTracks()

    @Query("DELETE FROM editorial_shelves")
    suspend fun deleteEditorialShelves()

    @Query("DELETE FROM albums")
    suspend fun deleteAlbums()

    @Query("DELETE FROM artists")
    suspend fun deleteArtists()

    @Transaction
    suspend fun readSnapshot(): CatalogEntitySnapshot = CatalogEntitySnapshot(
        artists = getArtists(),
        albums = getAlbums(),
        tracks = getTracks(),
        editorialShelves = getEditorialShelves(),
        editorialShelfTracks = getEditorialShelfTracks(),
        lastSyncedAtEpochMillis = getLastSuccessfulSyncEpochMillis(CATALOG_SYNC_KEY),
    )

    @Transaction
    suspend fun replaceCatalog(
        artists: List<ArtistEntity>,
        albums: List<AlbumEntity>,
        tracks: List<TrackEntity>,
        editorialShelves: List<EditorialShelfEntity>,
        editorialShelfTracks: List<EditorialShelfTrackEntity>,
        syncedAtEpochMillis: Long,
    ) {
        deleteEditorialShelfTracks()
        deleteEditorialShelves()
        deleteTracks()
        deleteAlbums()
        deleteArtists()
        insertArtists(artists)
        insertAlbums(albums)
        insertTracks(tracks)
        insertEditorialShelves(editorialShelves)
        insertEditorialShelfTracks(editorialShelfTracks)
        insertSyncMetadata(
            SyncMetadataEntity(
                key = CATALOG_SYNC_KEY,
                lastSuccessfulSyncEpochMs = syncedAtEpochMillis,
            ),
        )
    }

    @Transaction
    suspend fun recordRecentlyPlayed(
        userId: String,
        trackId: String,
        playedAtEpochMillis: Long,
        limit: Int,
    ): Boolean {
        if (!containsTrack(trackId)) return false
        insertRecentlyPlayed(
            RecentlyPlayedEntity(
                userId = userId,
                trackId = trackId,
                playedAtEpochMs = playedAtEpochMillis,
            ),
        )
        pruneRecentlyPlayed(userId, limit)
        return true
    }

    @Transaction
    suspend fun replaceLibrary(
        userId: String,
        likedTracks: List<LibraryLikedTrackEntity>,
        savedAlbums: List<LibrarySavedAlbumEntity>,
        followedArtists: List<LibraryFollowedArtistEntity>,
        syncedAtEpochMillis: Long,
    ) {
        deleteLikedTracks(userId)
        deleteSavedAlbums(userId)
        deleteFollowedArtists(userId)
        insertLikedTracks(likedTracks)
        insertSavedAlbums(savedAlbums)
        insertFollowedArtists(followedArtists)
        insertSyncMetadata(
            SyncMetadataEntity(
                key = librarySyncKey(userId),
                lastSuccessfulSyncEpochMs = syncedAtEpochMillis,
            ),
        )
    }

    companion object {
        const val CATALOG_SYNC_KEY = "catalog"
        fun librarySyncKey(userId: String) = "library:$userId"
    }
}
