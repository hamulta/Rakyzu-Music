package my.id.rakyzumusic.core.database.catalog

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "artists")
internal data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
)

@Entity(
    tableName = "albums",
    foreignKeys = [
        ForeignKey(
            entity = ArtistEntity::class,
            parentColumns = ["id"],
            childColumns = ["artist_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("artist_id")],
)
internal data class AlbumEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "artist_id") val artistId: String,
    val title: String,
    @ColumnInfo(name = "release_date") val releaseDate: String?,
)

@Entity(
    tableName = "tracks",
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["album_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("album_id"),
        Index(value = ["album_id", "disc_number", "track_number"], unique = true),
    ],
)
internal data class TrackEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "album_id") val albumId: String,
    val title: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "disc_number") val discNumber: Int,
    @ColumnInfo(name = "track_number") val trackNumber: Int,
    @ColumnInfo(name = "is_explicit") val isExplicit: Boolean,
)

@Entity(tableName = "sync_metadata")
internal data class SyncMetadataEntity(
    @PrimaryKey val key: String,
    @ColumnInfo(name = "last_successful_sync_epoch_ms") val lastSuccessfulSyncEpochMs: Long,
)

@Entity(
    tableName = "editorial_shelves",
    indices = [Index(value = ["position"], unique = true)],
)
internal data class EditorialShelfEntity(
    @PrimaryKey val id: String,
    val title: String,
    val subtitle: String?,
    val position: Int,
)

@Entity(
    tableName = "editorial_shelf_tracks",
    primaryKeys = ["shelf_id", "track_id"],
    foreignKeys = [
        ForeignKey(
            entity = EditorialShelfEntity::class,
            parentColumns = ["id"],
            childColumns = ["shelf_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("shelf_id"),
        Index("track_id"),
        Index(value = ["shelf_id", "position"], unique = true),
    ],
)
internal data class EditorialShelfTrackEntity(
    @ColumnInfo(name = "shelf_id") val shelfId: String,
    @ColumnInfo(name = "track_id") val trackId: String,
    val position: Int,
)

@Entity(
    tableName = "recently_played",
    primaryKeys = ["user_id", "track_id"],
    indices = [Index(value = ["user_id", "played_at_epoch_ms"])],
)
internal data class RecentlyPlayedEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "track_id") val trackId: String,
    @ColumnInfo(name = "played_at_epoch_ms") val playedAtEpochMs: Long,
)

@Entity(
    tableName = "library_liked_tracks",
    primaryKeys = ["user_id", "track_id"],
    indices = [Index(value = ["user_id", "saved_at_epoch_ms"])],
)
internal data class LibraryLikedTrackEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "track_id") val trackId: String,
    @ColumnInfo(name = "saved_at_epoch_ms") val savedAtEpochMillis: Long,
)

@Entity(
    tableName = "library_saved_albums",
    primaryKeys = ["user_id", "album_id"],
    indices = [Index(value = ["user_id", "saved_at_epoch_ms"])],
)
internal data class LibrarySavedAlbumEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "album_id") val albumId: String,
    @ColumnInfo(name = "saved_at_epoch_ms") val savedAtEpochMillis: Long,
)

@Entity(
    tableName = "library_followed_artists",
    primaryKeys = ["user_id", "artist_id"],
    indices = [Index(value = ["user_id", "saved_at_epoch_ms"])],
)
internal data class LibraryFollowedArtistEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "artist_id") val artistId: String,
    @ColumnInfo(name = "saved_at_epoch_ms") val savedAtEpochMillis: Long,
)

@Entity(
    tableName = "library_mutation_outbox",
    primaryKeys = ["user_id", "item_kind", "item_id"],
    indices = [Index(value = ["user_id", "queued_at_epoch_ms", "item_kind", "item_id"])],
)
internal data class LibraryMutationOutboxEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "item_kind") val itemKind: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "desired_saved") val desiredSaved: Boolean,
    @ColumnInfo(name = "queued_at_epoch_ms") val queuedAtEpochMillis: Long,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int,
)

@Entity(
    tableName = "playlists",
    primaryKeys = ["user_id", "playlist_id"],
    indices = [Index(value = ["user_id", "updated_at_epoch_ms", "playlist_id"])],
)
internal data class PlaylistEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "playlist_id") val playlistId: String,
    val name: String,
    val description: String,
    @ColumnInfo(name = "track_count") val trackCount: Int,
    val revision: Long,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_ms") val updatedAtEpochMillis: Long,
    @ColumnInfo(name = "owner_id", defaultValue = "''") val ownerId: String = "",
    @ColumnInfo(defaultValue = "'Private'") val visibility: String = "Private",
    @ColumnInfo(name = "access_role", defaultValue = "'Owner'") val accessRole: String = "Owner",
    @ColumnInfo(name = "is_following", defaultValue = "0") val isFollowing: Boolean = false,
)

/** Versioned, atomic detail snapshot; independent of the bounded Home catalog cache. */
@Entity(tableName = "playlist_details", primaryKeys = ["user_id", "playlist_id"])
internal data class PlaylistDetailEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "playlist_id") val playlistId: String,
    val payload: String,
)

@Entity(
    tableName = "playlist_mutation_outbox",
    primaryKeys = ["user_id", "operation_id"],
    indices = [Index(value = ["user_id", "playlist_id", "queued_at_epoch_ms"])],
)
internal data class PlaylistMutationOutboxEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "operation_id") val operationId: String,
    @ColumnInfo(name = "playlist_id") val playlistId: String,
    @ColumnInfo(name = "expected_revision") val expectedRevision: Long,
    @ColumnInfo(name = "mutation_payload") val mutationPayload: String,
    @ColumnInfo(name = "queued_at_epoch_ms") val queuedAtEpochMillis: Long,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int,
)

@Entity(
    tableName = "playback_queue_entries",
    primaryKeys = ["user_id", "position"],
    indices = [Index(value = ["user_id", "media_id"])],
)
internal data class PlaybackQueueEntryEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    val position: Int,
    @ColumnInfo(name = "media_id") val mediaId: String,
    val title: String,
    val artist: String,
    @ColumnInfo(name = "album_title") val albumTitle: String?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "artist_id") val artistId: String,
    @ColumnInfo(name = "album_id") val albumId: String,
)

@Entity(tableName = "playback_queue_states")
internal data class PlaybackQueueStateEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "current_index") val currentIndex: Int,
    @ColumnInfo(name = "updated_at_epoch_ms") val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "offline_downloads",
    primaryKeys = ["user_id", "track_id"],
    indices = [
        Index(value = ["user_id", "collection_kind", "collection_id"]),
        Index(value = ["user_id", "status", "updated_at_epoch_ms"]),
    ],
)
internal data class OfflineDownloadEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "track_id") val trackId: String,
    val title: String,
    val artist: String,
    @ColumnInfo(name = "collection_kind") val collectionKind: String,
    @ColumnInfo(name = "collection_id") val collectionId: String,
    @ColumnInfo(name = "collection_title") val collectionTitle: String,
    val status: String,
    @ColumnInfo(name = "downloaded_bytes") val downloadedBytes: Long,
    @ColumnInfo(name = "total_bytes") val totalBytes: Long?,
    @ColumnInfo(name = "file_token") val fileToken: String?,
    @ColumnInfo(name = "content_type") val contentType: String?,
    @ColumnInfo(name = "license_expires_at_epoch_ms") val licenseExpiresAtEpochMillis: Long?,
    @ColumnInfo(name = "content_revision") val contentRevision: String?,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int,
    @ColumnInfo(name = "failure_code") val failureCode: String?,
    @ColumnInfo(name = "requested_at_epoch_ms") val requestedAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_ms") val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "offline_download_collections",
    primaryKeys = ["user_id", "collection_kind", "collection_id", "track_id"],
    foreignKeys = [
        ForeignKey(
            entity = OfflineDownloadEntity::class,
            parentColumns = ["user_id", "track_id"],
            childColumns = ["user_id", "track_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["user_id", "track_id"])],
)
internal data class OfflineDownloadCollectionEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "collection_kind") val collectionKind: String,
    @ColumnInfo(name = "collection_id") val collectionId: String,
    @ColumnInfo(name = "track_id") val trackId: String,
    @ColumnInfo(name = "collection_title") val collectionTitle: String,
)

@Entity(tableName = "offline_download_preferences")
internal data class OfflineDownloadPreferenceEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "allow_mobile") val allowMobile: Boolean,
    @ColumnInfo(name = "keep_after_sign_out") val keepAfterSignOut: Boolean? = null,
)

@Entity(
    tableName = "track_contexts",
    primaryKeys = ["user_id", "track_id"],
    indices = [Index(value = ["user_id", "cached_at_epoch_ms"])],
)
internal data class TrackContextEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "track_id") val trackId: String,
    @ColumnInfo(name = "lyrics_kind") val lyricsKind: String,
    @ColumnInfo(name = "provider_name") val providerName: String?,
    @ColumnInfo(name = "provider_notice") val providerNotice: String?,
    @ColumnInfo(name = "catalog_revision") val catalogRevision: String,
    @ColumnInfo(name = "cached_at_epoch_ms") val cachedAtEpochMillis: Long,
    @ColumnInfo(name = "expires_at_epoch_ms") val expiresAtEpochMillis: Long,
)

@Entity(
    tableName = "track_lyric_lines",
    primaryKeys = ["user_id", "track_id", "position"],
    foreignKeys = [
        ForeignKey(
            entity = TrackContextEntity::class,
            parentColumns = ["user_id", "track_id"],
            childColumns = ["user_id", "track_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["user_id", "track_id"])],
)
internal data class TrackLyricLineEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "track_id") val trackId: String,
    val position: Int,
    val text: String,
    @ColumnInfo(name = "start_time_ms") val startTimeMs: Long?,
)

@Entity(
    tableName = "track_credits",
    primaryKeys = ["user_id", "track_id", "position"],
    foreignKeys = [
        ForeignKey(
            entity = TrackContextEntity::class,
            parentColumns = ["user_id", "track_id"],
            childColumns = ["user_id", "track_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["user_id", "track_id"])],
)
internal data class TrackCreditEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "track_id") val trackId: String,
    val position: Int,
    @ColumnInfo(name = "display_name") val displayName: String,
    val role: String,
    @ColumnInfo(name = "source_name") val sourceName: String?,
)
