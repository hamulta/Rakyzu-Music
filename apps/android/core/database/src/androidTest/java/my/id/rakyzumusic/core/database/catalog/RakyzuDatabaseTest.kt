package my.id.rakyzumusic.core.database.catalog

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import my.id.rakyzumusic.core.model.Album
import my.id.rakyzumusic.core.model.Artist
import my.id.rakyzumusic.core.model.CatalogSnapshot
import my.id.rakyzumusic.core.model.Track
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RakyzuDatabaseTest {
    private lateinit var database: RakyzuDatabase
    private lateinit var dataSource: CatalogLocalDataSource

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder<RakyzuDatabase>(
            context = context,
        )
            .setDriver(AndroidSQLiteDriver())
            .build()
        dataSource = RoomCatalogLocalDataSource(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun replacementIsObservedAsOneConsistentCatalogSnapshot() = runTest {
        dataSource.replaceCatalog(CATALOG, syncedAtEpochMillis = 1234L)

        val stored = dataSource.observeCatalog().first { !it.isEmpty }

        assertEquals("Rakyzu Sessions", stored.tracks.single().artist)
        assertEquals("Signal Zero", stored.tracks.single().albumTitle)
        assertEquals(1234L, stored.lastSyncedAtEpochMillis)
        assertFalse(stored.isEmpty)
    }

    private companion object {
        val CATALOG = CatalogSnapshot(
            artists = listOf(Artist("artist-1", "Rakyzu Sessions")),
            albums = listOf(Album("album-1", "artist-1", "Signal Zero", "2026-08-29")),
            tracks = listOf(
                Track(
                    id = "track-1",
                    title = "Midnight Signal",
                    artist = "Rakyzu Sessions",
                    durationMs = 185900,
                    artistId = "artist-1",
                    albumId = "album-1",
                    albumTitle = "Signal Zero",
                ),
            ),
            lastSyncedAtEpochMillis = null,
        )
    }
}
