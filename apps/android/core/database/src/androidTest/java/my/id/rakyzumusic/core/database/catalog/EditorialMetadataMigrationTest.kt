package my.id.rakyzumusic.core.database.catalog

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorialMetadataMigrationTest {
    @Test
    fun versionTwelveAddsSmartEditorialMetadataWithoutLosingShelves() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "rakyzu-editorial-migration-${UUID.randomUUID()}.db"
        createVersionTwelveDatabase(context, name)

        val database = Room.databaseBuilder<RakyzuDatabase>(context, name)
            .setDriver(AndroidSQLiteDriver())
            .build()
        try {
            val shelf = database.catalogDao().getEditorialShelves().single()
            assertEquals("shelf-1", shelf.id)
            assertEquals(false, shelf.hasCustomArtwork)
            assertEquals(null, shelf.cardLabel)
            assertEquals("#4A558F", shelf.colorHex)
            assertEquals(0L, shelf.globalScore)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    private fun createVersionTwelveDatabase(context: Context, name: String) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val schema = InstrumentationRegistry.getInstrumentation().context.assets.open(
            "my.id.rakyzumusic.core.database.catalog.RakyzuDatabase/12.json",
        ).bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                old.execSQL(
                    entity.getString("createSql")
                        .replace("\${TABLE_NAME}", entity.getString("tableName")),
                )
                val indices = entity.optJSONArray("indices") ?: JSONArray()
                for (item in 0 until indices.length()) {
                    old.execSQL(
                        indices.getJSONObject(item).getString("createSql")
                            .replace("\${TABLE_NAME}", entity.getString("tableName")),
                    )
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) old.execSQL(setup.getString(index))
            old.execSQL(
                "INSERT INTO editorial_shelves(id, title, subtitle, position, has_custom_artwork) " +
                    "VALUES('shelf-1', 'Pop Mix', 'Global pop', 0, 0)",
            )
            old.version = 12
        }
    }
}
