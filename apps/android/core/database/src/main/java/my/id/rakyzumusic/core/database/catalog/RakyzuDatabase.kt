package my.id.rakyzumusic.core.database.catalog

import android.content.Context
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
    ],
    version = 1,
    exportSchema = true,
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
