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
    @Query(
        "SELECT * FROM track_contexts WHERE user_id = :userId AND track_id = :trackId",
    )
    suspend fun getTrackContext(userId: String, trackId: String): TrackContextEntity?

    @Query(
        "SELECT * FROM track_lyric_lines WHERE user_id = :userId AND track_id = :trackId " +
            "ORDER BY position",
    )
    suspend fun getTrackLyricLines(userId: String, trackId: String): List<TrackLyricLineEntity>

    @Query(
        "SELECT * FROM track_credits WHERE user_id = :userId AND track_id = :trackId " +
            "ORDER BY position",
    )
    suspend fun getTrackCredits(userId: String, trackId: String): List<TrackCreditEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackContext(context: TrackContextEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackLyricLines(lines: List<TrackLyricLineEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackCredits(credits: List<TrackCreditEntity>)

    @Query("DELETE FROM track_lyric_lines WHERE user_id = :userId AND track_id = :trackId")
    suspend fun deleteTrackLyricLines(userId: String, trackId: String)

    @Query("DELETE FROM track_credits WHERE user_id = :userId AND track_id = :trackId")
    suspend fun deleteTrackCredits(userId: String, trackId: String)

    @Query("DELETE FROM track_contexts WHERE user_id = :userId AND track_id NOT IN (:trackIds)")
    suspend fun deleteTrackContextsOutsideCatalog(userId: String, trackIds: List<String>): Int

    @Query(
        "DELETE FROM track_contexts WHERE user_id = :userId AND track_id NOT IN (" +
            "SELECT track_id FROM track_contexts WHERE user_id = :userId " +
            "ORDER BY cached_at_epoch_ms DESC, track_id LIMIT :limit)",
    )
    suspend fun pruneTrackContexts(userId: String, limit: Int): Int

    @Query("SELECT COUNT(*) FROM track_contexts WHERE user_id = :userId")
    suspend fun countTrackContexts(userId: String): Int

    @Query("SELECT COUNT(*) FROM track_lyric_lines WHERE user_id = :userId")
    suspend fun countTrackLyricLines(userId: String): Int

    @Query("SELECT COUNT(*) FROM track_credits WHERE user_id = :userId")
    suspend fun countTrackCredits(userId: String): Int

    @Transaction
    suspend fun replaceTrackContext(
        context: TrackContextEntity,
        lines: List<TrackLyricLineEntity>,
        credits: List<TrackCreditEntity>,
    ) {
        deleteTrackLyricLines(context.userId, context.trackId)
        deleteTrackCredits(context.userId, context.trackId)
        insertTrackContext(context)
        if (lines.isNotEmpty()) insertTrackLyricLines(lines)
        if (credits.isNotEmpty()) insertTrackCredits(credits)
        pruneTrackContexts(context.userId, 250)
    }

    @Query(
        "SELECT * FROM offline_downloads WHERE user_id = :userId " +
            "ORDER BY updated_at_epoch_ms DESC, track_id",
    )
    fun observeOfflineDownloads(
        userId: String,
    ): kotlinx.coroutines.flow.Flow<List<OfflineDownloadEntity>>

    @Query(
        "SELECT * FROM offline_download_collections WHERE user_id = :userId " +
            "ORDER BY collection_kind, collection_id, track_id",
    )
    fun observeOfflineDownloadCollections(
        userId: String,
    ): kotlinx.coroutines.flow.Flow<List<OfflineDownloadCollectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOfflineDownloadCollections(items: List<OfflineDownloadCollectionEntity>)

    @Query("SELECT * FROM offline_downloads WHERE user_id = :userId AND track_id = :trackId")
    suspend fun getOfflineDownload(userId: String, trackId: String): OfflineDownloadEntity?

    @Query("SELECT * FROM offline_downloads WHERE user_id = :userId")
    suspend fun getOfflineDownloads(userId: String): List<OfflineDownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOfflineDownloads(items: List<OfflineDownloadEntity>)

    @Query(
        "UPDATE offline_downloads SET status = :status, failure_code = :failureCode, " +
            "attempt_count = CASE WHEN :resetAttempts THEN 0 " +
            "ELSE attempt_count + :attemptIncrement END, updated_at_epoch_ms = :updatedAt " +
            "WHERE user_id = :userId AND track_id = :trackId",
    )
    suspend fun updateOfflineDownloadStatus(
        userId: String,
        trackId: String,
        status: String,
        failureCode: String?,
        attemptIncrement: Int,
        resetAttempts: Boolean,
        updatedAt: Long,
    ): Int

    @Query(
        "UPDATE offline_downloads SET status = :status, downloaded_bytes = :downloadedBytes, " +
            "total_bytes = :totalBytes, failure_code = NULL, updated_at_epoch_ms = :updatedAt " +
            "WHERE user_id = :userId AND track_id = :trackId",
    )
    suspend fun updateOfflineDownloadProgress(
        userId: String,
        trackId: String,
        status: String,
        downloadedBytes: Long,
        totalBytes: Long?,
        updatedAt: Long,
    ): Int

    @Query(
        "UPDATE offline_downloads SET status = :status, downloaded_bytes = :totalBytes, " +
            "total_bytes = :totalBytes, file_token = :fileToken, content_type = :contentType, " +
            "license_expires_at_epoch_ms = :licenseExpiresAt, content_revision = :contentRevision, " +
            "failure_code = NULL, " +
            "updated_at_epoch_ms = :updatedAt WHERE user_id = :userId AND track_id = :trackId",
    )
    suspend fun completeOfflineDownload(
        userId: String,
        trackId: String,
        status: String,
        totalBytes: Long,
        fileToken: String,
        contentType: String,
        licenseExpiresAt: Long,
        contentRevision: String?,
        updatedAt: Long,
    ): Int

    @Query(
        "DELETE FROM offline_downloads WHERE user_id = :userId AND status = :status",
    )
    suspend fun deleteOfflineDownloadsWithStatus(userId: String, status: String): Int

    @Query("DELETE FROM offline_downloads WHERE user_id = :userId")
    suspend fun deleteOfflineDownloads(userId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOfflineDownloadPreference(preference: OfflineDownloadPreferenceEntity)

    @Query("SELECT allow_mobile FROM offline_download_preferences WHERE user_id = :userId")
    suspend fun getAllowMobileDownloads(userId: String): Boolean?

    @Query("SELECT allow_mobile FROM offline_download_preferences WHERE user_id = :userId")
    fun observeAllowMobileDownloads(userId: String): kotlinx.coroutines.flow.Flow<Boolean?>

    @Query("SELECT keep_after_sign_out FROM offline_download_preferences WHERE user_id = :userId")
    suspend fun getKeepDownloadsAfterSignOut(userId: String): Boolean?

    @Query("SELECT keep_after_sign_out FROM offline_download_preferences WHERE user_id = :userId")
    fun observeKeepDownloadsAfterSignOut(userId: String): kotlinx.coroutines.flow.Flow<Boolean?>

    @Query(
        "SELECT * FROM playback_queue_entries WHERE user_id = :userId " +
            "ORDER BY position ASC",
    )
    suspend fun getPlaybackQueueEntries(userId: String): List<PlaybackQueueEntryEntity>

    @Query("SELECT * FROM playback_queue_states WHERE user_id = :userId")
    suspend fun getPlaybackQueueState(userId: String): PlaybackQueueStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaybackQueueEntries(entries: List<PlaybackQueueEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaybackQueueState(state: PlaybackQueueStateEntity)

    @Query("DELETE FROM playback_queue_entries WHERE user_id = :userId")
    suspend fun deletePlaybackQueueEntries(userId: String)

    @Query("DELETE FROM playback_queue_states WHERE user_id = :userId")
    suspend fun deletePlaybackQueueState(userId: String)

    @Query("SELECT payload FROM playlist_details WHERE user_id = :userId AND playlist_id = :playlistId")
    fun observePlaylistDetail(userId: String, playlistId: String): kotlinx.coroutines.flow.Flow<String?>

    @Query("SELECT payload FROM playlist_details WHERE user_id = :userId AND playlist_id = :playlistId")
    suspend fun getPlaylistDetail(userId: String, playlistId: String): String?

    @Query(
        "SELECT * FROM playlist_mutation_outbox WHERE user_id = :userId " +
            "AND playlist_id = :playlistId ORDER BY queued_at_epoch_ms, operation_id LIMIT :limit",
    )
    suspend fun getPendingPlaylistMutations(
        userId: String,
        playlistId: String,
        limit: Int,
    ): List<PlaylistMutationOutboxEntity>

    @Query(
        "SELECT COUNT(*) FROM playlist_mutation_outbox " +
            "WHERE user_id = :userId AND playlist_id = :playlistId",
    )
    suspend fun countPendingPlaylistMutations(userId: String, playlistId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistMutation(item: PlaylistMutationOutboxEntity)

    @Query(
        "DELETE FROM playlist_mutation_outbox WHERE user_id = :userId " +
            "AND operation_id = :operationId",
    )
    suspend fun acknowledgePlaylistMutation(userId: String, operationId: String): Int

    @Query(
        "UPDATE playlist_mutation_outbox SET attempt_count = attempt_count + 1 " +
            "WHERE user_id = :userId AND operation_id = :operationId",
    )
    suspend fun recordPlaylistMutationAttempt(userId: String, operationId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistDetail(detail: PlaylistDetailEntity)

    @Transaction
    suspend fun storePlaylistDetail(detail: PlaylistDetailEntity, playlist: PlaylistEntity) {
        insertPlaylistDetail(detail)
        insertPlaylist(playlist)
    }

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

    @Query(
        "SELECT track_id FROM library_liked_tracks WHERE user_id = :userId " +
            "ORDER BY saved_at_epoch_ms DESC, track_id LIMIT :limit",
    )
    suspend fun getLikedTrackIds(userId: String, limit: Int): List<String>

    @Query(
        "SELECT album_id FROM library_saved_albums WHERE user_id = :userId " +
            "ORDER BY saved_at_epoch_ms DESC, album_id LIMIT :limit",
    )
    suspend fun getSavedAlbumIds(userId: String, limit: Int): List<String>

    @Query(
        "SELECT artist_id FROM library_followed_artists WHERE user_id = :userId " +
            "ORDER BY saved_at_epoch_ms DESC, artist_id LIMIT :limit",
    )
    suspend fun getFollowedArtistIds(userId: String, limit: Int): List<String>

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

    @Query(
        "SELECT * FROM library_mutation_outbox WHERE user_id = :userId " +
            "ORDER BY queued_at_epoch_ms, item_kind, item_id LIMIT :limit",
    )
    suspend fun getPendingLibraryMutations(
        userId: String,
        limit: Int,
    ): List<LibraryMutationOutboxEntity>

    @Query(
        "SELECT COUNT(*) FROM library_mutation_outbox WHERE user_id = :userId",
    )
    suspend fun countPendingLibraryMutations(userId: String): Int

    @Query(
        "SELECT * FROM playlists WHERE user_id = :userId " +
            "ORDER BY updated_at_epoch_ms DESC, playlist_id",
    )
    suspend fun getPlaylists(userId: String): List<PlaylistEntity>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM library_mutation_outbox " +
            "WHERE user_id = :userId AND item_kind = :itemKind AND item_id = :itemId)",
    )
    suspend fun hasPendingLibraryMutation(
        userId: String,
        itemKind: String,
        itemId: String,
    ): Boolean

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLibraryMutation(item: LibraryMutationOutboxEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(items: List<PlaylistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(item: PlaylistEntity)

    @Query("DELETE FROM library_liked_tracks WHERE user_id = :userId")
    suspend fun deleteLikedTracks(userId: String)

    @Query("DELETE FROM library_saved_albums WHERE user_id = :userId")
    suspend fun deleteSavedAlbums(userId: String)

    @Query("DELETE FROM library_followed_artists WHERE user_id = :userId")
    suspend fun deleteFollowedArtists(userId: String)

    @Query("DELETE FROM playlists WHERE user_id = :userId")
    suspend fun deletePlaylists(userId: String)

    @Query("DELETE FROM library_liked_tracks WHERE user_id = :userId AND track_id = :itemId")
    suspend fun deleteLikedTrack(userId: String, itemId: String)

    @Query("DELETE FROM library_saved_albums WHERE user_id = :userId AND album_id = :itemId")
    suspend fun deleteSavedAlbum(userId: String, itemId: String)

    @Query("DELETE FROM library_followed_artists WHERE user_id = :userId AND artist_id = :itemId")
    suspend fun deleteFollowedArtist(userId: String, itemId: String)

    @Query(
        "DELETE FROM library_mutation_outbox WHERE user_id = :userId " +
            "AND item_kind = :itemKind AND item_id = :itemId " +
            "AND desired_saved = :desiredSaved AND queued_at_epoch_ms = :queuedAtEpochMillis",
    )
    suspend fun acknowledgeLibraryMutation(
        userId: String,
        itemKind: String,
        itemId: String,
        desiredSaved: Boolean,
        queuedAtEpochMillis: Long,
    ): Int

    @Query(
        "UPDATE library_mutation_outbox SET attempt_count = attempt_count + 1 " +
            "WHERE user_id = :userId AND item_kind = :itemKind AND item_id = :itemId " +
            "AND queued_at_epoch_ms = :queuedAtEpochMillis",
    )
    suspend fun recordLibraryMutationAttempt(
        userId: String,
        itemKind: String,
        itemId: String,
        queuedAtEpochMillis: Long,
    )

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
        val pending = getPendingLibraryMutations(userId, Int.MAX_VALUE)
        deleteLikedTracks(userId)
        deleteSavedAlbums(userId)
        deleteFollowedArtists(userId)
        insertLikedTracks(likedTracks)
        insertSavedAlbums(savedAlbums)
        insertFollowedArtists(followedArtists)
        pending.forEach { mutation ->
            applyLibraryItem(
                userId = userId,
                itemKind = mutation.itemKind,
                itemId = mutation.itemId,
                saved = mutation.desiredSaved,
                savedAtEpochMillis = mutation.queuedAtEpochMillis,
            )
        }
        insertSyncMetadata(
            SyncMetadataEntity(
                key = librarySyncKey(userId),
                lastSuccessfulSyncEpochMs = syncedAtEpochMillis,
            ),
        )
    }

    @Transaction
    suspend fun replacePlaylists(
        userId: String,
        playlists: List<PlaylistEntity>,
        syncedAtEpochMillis: Long,
    ) {
        deletePlaylists(userId)
        insertPlaylists(playlists)
        insertSyncMetadata(
            SyncMetadataEntity(
                key = playlistSyncKey(userId),
                lastSuccessfulSyncEpochMs = syncedAtEpochMillis,
            ),
        )
    }

    @Transaction
    suspend fun enqueueLibraryMutation(
        mutation: LibraryMutationOutboxEntity,
    ) {
        applyLibraryItem(
            userId = mutation.userId,
            itemKind = mutation.itemKind,
            itemId = mutation.itemId,
            saved = mutation.desiredSaved,
            savedAtEpochMillis = mutation.queuedAtEpochMillis,
        )
        insertLibraryMutation(mutation)
    }

    @Transaction
    suspend fun applyRemoteLibraryChange(
        userId: String,
        itemKind: String,
        itemId: String,
        saved: Boolean,
        savedAtEpochMillis: Long,
        sequence: Long,
    ) {
        if (!hasPendingLibraryMutation(userId, itemKind, itemId)) {
            applyLibraryItem(userId, itemKind, itemId, saved, savedAtEpochMillis)
        }
        insertSyncMetadata(
            SyncMetadataEntity(
                key = libraryChangeCursorKey(userId),
                lastSuccessfulSyncEpochMs = sequence,
            ),
        )
    }

    suspend fun applyLibraryItem(
        userId: String,
        itemKind: String,
        itemId: String,
        saved: Boolean,
        savedAtEpochMillis: Long,
    ) {
        when (itemKind) {
            "Track" -> if (saved) {
                insertLikedTrack(LibraryLikedTrackEntity(userId, itemId, savedAtEpochMillis))
            } else {
                deleteLikedTrack(userId, itemId)
            }
            "Album" -> if (saved) {
                insertSavedAlbum(LibrarySavedAlbumEntity(userId, itemId, savedAtEpochMillis))
            } else {
                deleteSavedAlbum(userId, itemId)
            }
            "Artist" -> if (saved) {
                insertFollowedArtist(LibraryFollowedArtistEntity(userId, itemId, savedAtEpochMillis))
            } else {
                deleteFollowedArtist(userId, itemId)
            }
            else -> error("Unsupported Library item kind")
        }
    }

    @Transaction
    suspend fun replacePlaybackQueue(
        userId: String,
        entries: List<PlaybackQueueEntryEntity>,
        state: PlaybackQueueStateEntity?,
    ) {
        deletePlaybackQueueEntries(userId)
        deletePlaybackQueueState(userId)
        if (entries.isNotEmpty()) insertPlaybackQueueEntries(entries)
        if (state != null) insertPlaybackQueueState(state)
    }

    companion object {
        const val CATALOG_SYNC_KEY = "catalog"
        fun librarySyncKey(userId: String) = "library:$userId"
        fun libraryChangeCursorKey(userId: String) = "library-change-cursor:$userId"
        fun playlistSyncKey(userId: String) = "playlists:$userId"
    }
}
