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
