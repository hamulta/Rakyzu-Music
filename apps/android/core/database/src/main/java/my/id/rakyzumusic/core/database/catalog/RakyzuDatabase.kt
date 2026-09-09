package my.id.rakyzumusic.core.database.catalog

import android.content.Context
import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver

@Database(
    entities = [
        ArtistEntity::class,
        AlbumEntity::class,
        TrackEntity::class,
        SyncMetadataEntity::class,
        EditorialShelfEntity::class,
        EditorialShelfTrackEntity::class,
        RecentlyPlayedEntity::class,
        LibraryLikedTrackEntity::class,
        LibrarySavedAlbumEntity::class,
        LibraryFollowedArtistEntity::class,
        LibraryMutationOutboxEntity::class,
        PlaylistEntity::class,
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
    ],
)
internal abstract class RakyzuDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
}

object RakyzuDatabaseFactory {
    private const val DATABASE_NAME = "rakyzu_music.db"

    internal fun create(context: Context): RakyzuDatabase = Room.databaseBuilder<RakyzuDatabase>(
        context = context.applicationContext,
        name = DATABASE_NAME,
    )
        .setDriver(AndroidSQLiteDriver())
        .build()
}
