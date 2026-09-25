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
import my.id.rakyzumusic.core.model.DownloadCollectionKind
import my.id.rakyzumusic.core.model.OfflineDownloadItem
import my.id.rakyzumusic.core.model.OfflineDownloadStatus
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineDownloadMigrationTest {
    @Test
    fun versionEightMigratesToAccountScopedOfflineStorage() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "rakyzu-offline-migration-${UUID.randomUUID()}.db"
        createDatabaseFromSchema(context, name, version = 8)

        val database = Room.databaseBuilder<RakyzuDatabase>(context, name)
            .setDriver(AndroidSQLiteDriver())
            .build()
        try {
            val local = RoomOfflineDownloadLocalDataSource(database)
            local.upsert(listOf(download(userId = "account-a", trackId = "track-1")))
            local.setAllowMobileDownloads("account-a", allow = true)

            assertEquals("track-1", local.get("account-a", "track-1")?.item?.trackId)
            assertEquals(null, local.get("account-b", "track-1"))
            assertTrue(local.allowMobileDownloads("account-a"))
            assertFalse(local.allowMobileDownloads("account-b"))
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    private fun createDatabaseFromSchema(context: Context, name: String, version: Int) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val schema = InstrumentationRegistry.getInstrumentation().context.assets.open(
            "my.id.rakyzumusic.core.database.catalog.RakyzuDatabase/$version.json",
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
            old.version = version
        }
    }

    private fun download(userId: String, trackId: String) = StoredOfflineDownload(
        item = OfflineDownloadItem(
            userId = userId,
            trackId = trackId,
            title = "Migration Track",
            artist = "Rakyzu Music",
            collectionKind = DownloadCollectionKind.Album,
            collectionId = "album-1",
            collectionTitle = "Migration Album",
            status = OfflineDownloadStatus.Queued,
            downloadedBytes = 0L,
            totalBytes = null,
            updatedAtEpochMillis = 1_000L,
            failureCode = null,
        ),
        fileToken = null,
        contentType = null,
        licenseExpiresAtEpochMillis = null,
        attemptCount = 0,
        requestedAtEpochMillis = 1_000L,
    )
}
