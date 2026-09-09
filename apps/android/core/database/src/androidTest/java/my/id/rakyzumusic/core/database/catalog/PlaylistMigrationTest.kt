package my.id.rakyzumusic.core.database.catalog

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import my.id.rakyzumusic.core.model.PlaylistSummary
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistMigrationTest {
    @Test fun versionFiveCacheMigratesWithoutLosingOwnerMetadata() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "rakyzu-migration-${java.util.UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val schema = InstrumentationRegistry.getInstrumentation().context.assets.open(
            "my.id.rakyzumusic.core.database.catalog.RakyzuDatabase/5.json",
        ).bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (i in 0 until indices.length()) old.execSQL(indices.getJSONObject(i).getString("createSql")
                    .replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO playlists VALUES ('owner','playlist','Mix','',0,1,1000,1000)")
            old.version = 5
        }
        val database = Room.databaseBuilder<RakyzuDatabase>(context, name).setDriver(AndroidSQLiteDriver()).build()
        try {
            val local = RoomPlaylistLocalDataSource(database)
            val dao = database.catalogDao()
            assertEquals("Mix", dao.getPlaylists("owner").single().name)
            val summary = PlaylistSummary("playlist", "Updated", "", 0, 2, 1000, 2000)
            local.storeDetailPayload("owner", summary, "verified-payload")
            assertEquals("verified-payload", dao.getPlaylistDetail("owner", "playlist"))
            assertEquals(null, dao.getPlaylistDetail("other", "playlist"))
            assertEquals("Updated", dao.getPlaylists("owner").single().name)
            assertEquals(2L, dao.getPlaylists("owner").single().revision)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
