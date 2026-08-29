package my.id.rakyzumusic.core.database.catalog

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction

internal data class CatalogEntitySnapshot(
    val artists: List<ArtistEntity>,
    val albums: List<AlbumEntity>,
    val tracks: List<TrackEntity>,
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

    @Query("SELECT last_successful_sync_epoch_ms FROM sync_metadata WHERE `key` = :key")
    suspend fun getLastSuccessfulSyncEpochMillis(key: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtists(artists: List<ArtistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbums(albums: List<AlbumEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncMetadata(metadata: SyncMetadataEntity)

    @Query("DELETE FROM tracks")
    suspend fun deleteTracks()

    @Query("DELETE FROM albums")
    suspend fun deleteAlbums()

    @Query("DELETE FROM artists")
    suspend fun deleteArtists()

    @Transaction
    suspend fun readSnapshot(): CatalogEntitySnapshot = CatalogEntitySnapshot(
        artists = getArtists(),
        albums = getAlbums(),
        tracks = getTracks(),
        lastSyncedAtEpochMillis = getLastSuccessfulSyncEpochMillis(CATALOG_SYNC_KEY),
    )

    @Transaction
    suspend fun replaceCatalog(
        artists: List<ArtistEntity>,
        albums: List<AlbumEntity>,
        tracks: List<TrackEntity>,
        syncedAtEpochMillis: Long,
    ) {
        deleteTracks()
        deleteAlbums()
        deleteArtists()
        insertArtists(artists)
        insertAlbums(albums)
        insertTracks(tracks)
        insertSyncMetadata(
            SyncMetadataEntity(
                key = CATALOG_SYNC_KEY,
                lastSuccessfulSyncEpochMs = syncedAtEpochMillis,
            ),
        )
    }

    companion object {
        const val CATALOG_SYNC_KEY = "catalog"
    }
}
