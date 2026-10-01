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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonalizationMigrationTest {
    @Test
    fun versionElevenMigratesHistoryAndCreatesAccountScopedControls() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "rakyzu-personalization-migration-${UUID.randomUUID()}.db"
        createVersionElevenDatabase(context, name)

        val database = Room.databaseBuilder<RakyzuDatabase>(context, name)
            .setDriver(AndroidSQLiteDriver())
            .build()
        try {
            val dao = database.catalogDao()
            assertEquals(1, dao.getListeningSignals("listener-a", 20).single().playCount)
            dao.upsertPersonalizationPreference(
                PersonalizationPreferenceEntity("listener-a", true, "explore", 2_000L),
            )
            dao.upsertRecommendationFeedback(
                RecommendationFeedbackEntity("listener-a", "track-1", true, false, 3_000L),
            )

            assertEquals("explore", dao.getPersonalizationPreference("listener-a")?.discoveryMode)
            assertTrue(dao.getRecommendationFeedback("listener-a").single().isHidden)
            assertTrue(dao.getRecommendationFeedback("listener-b").isEmpty())
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    private fun createVersionElevenDatabase(context: Context, name: String) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val schema = InstrumentationRegistry.getInstrumentation().context.assets.open(
            "my.id.rakyzumusic.core.database.catalog.RakyzuDatabase/11.json",
        ).bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
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
            old.execSQL("INSERT INTO artists(id, name) VALUES('artist-1', 'Artist')")
            old.execSQL(
                "INSERT INTO albums(id, artist_id, title, release_date) " +
                    "VALUES('album-1', 'artist-1', 'Album', NULL)",
            )
            old.execSQL(
                "INSERT INTO tracks(id, album_id, title, duration_ms, disc_number, " +
                    "track_number, is_explicit) VALUES('track-1', 'album-1', 'Track', " +
                    "180000, 1, 1, 0)",
            )
            old.execSQL(
                "INSERT INTO recently_played(user_id, track_id, played_at_epoch_ms) " +
                    "VALUES('listener-a', 'track-1', 1000)",
            )
            old.version = 11
        }
    }
}
