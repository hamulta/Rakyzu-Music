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
import my.id.rakyzumusic.core.model.EditorialShelf
import my.id.rakyzumusic.core.model.LibraryItemKind
import my.id.rakyzumusic.core.model.PlaylistSummary
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
    private lateinit var libraryDataSource: LibraryLocalDataSource
    private lateinit var playlistDataSource: PlaylistLocalDataSource

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder<RakyzuDatabase>(
            context = context,
        )
            .setDriver(AndroidSQLiteDriver())
            .build()
        dataSource = RoomCatalogLocalDataSource(database)
        libraryDataSource = RoomLibraryLocalDataSource(database)
        playlistDataSource = RoomPlaylistLocalDataSource(database)
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
        assertEquals("Rakyzu Essentials", stored.editorialShelves.single().title)
        assertEquals("Midnight Signal", stored.editorialShelves.single().tracks.single().title)
        assertEquals(1234L, stored.lastSyncedAtEpochMillis)
        assertFalse(stored.isEmpty)
    }

    @Test
    fun recentlyPlayedIsValidatedAndIsolatedByUser() = runTest {
        dataSource.replaceCatalog(CATALOG, syncedAtEpochMillis = 1234L)

        assertEquals(
            true,
            dataSource.recordRecentlyPlayed("listener-1", "track-1", playedAtEpochMillis = 55L),
        )
        assertEquals(
            false,
            dataSource.recordRecentlyPlayed("listener-1", "missing", playedAtEpochMillis = 56L),
        )

        val listenerOne = dataSource.observeHomeFeed("listener-1").first {
            it.recentlyPlayed.isNotEmpty()
        }
        val listenerTwo = dataSource.observeHomeFeed("listener-2").first()

        assertEquals(listOf("track-1"), listenerOne.recentlyPlayed.map(Track::id))
        assertEquals(emptyList<Track>(), listenerTwo.recentlyPlayed)
    }

    @Test
    fun libraryCacheIsResolvedFromCatalogAndIsolatedByListener() = runTest {
        dataSource.replaceCatalog(CATALOG, syncedAtEpochMillis = 1234L)
        libraryDataSource.replaceLibrary(
            userId = "listener-1",
            selections = listOf(
                StoredLibrarySelection(LibraryItemKind.Track, "track-1", 30L),
                StoredLibrarySelection(LibraryItemKind.Album, "album-1", 20L),
                StoredLibrarySelection(LibraryItemKind.Artist, "artist-1", 10L),
                StoredLibrarySelection(LibraryItemKind.Track, "missing", 40L),
            ),
            syncedAtEpochMillis = 55L,
        )

        val listenerOne = libraryDataSource.observeLibrary("listener-1").first {
            it.lastSyncedAtEpochMillis == 55L
        }
        val listenerTwo = libraryDataSource.observeLibrary("listener-2").first()

        assertEquals(listOf("track-1"), listenerOne.likedTracks.map(Track::id))
        assertEquals(listOf("album-1"), listenerOne.savedAlbums.map { it.album.id })
        assertEquals(listOf("artist-1"), listenerOne.followedArtists.map { it.id })
        assertEquals(mapOf("track-1" to 30L), listenerOne.likedTrackSavedAtEpochMillis)
        assertEquals(mapOf("album-1" to 20L), listenerOne.savedAlbumSavedAtEpochMillis)
        assertEquals(mapOf("artist-1" to 10L), listenerOne.followedArtistSavedAtEpochMillis)
        assertEquals(true, listenerTwo.isEmpty)
    }

    @Test
    fun libraryOutboxSurvivesSnapshotReplacementAndRemoteDeleteAdvancesCursor() = runTest {
        dataSource.replaceCatalog(CATALOG, syncedAtEpochMillis = 1234L)
        val mutation = StoredLibraryMutation(
            kind = LibraryItemKind.Artist,
            itemId = "artist-1",
            saved = true,
            queuedAtEpochMillis = 70L,
        )

        libraryDataSource.enqueueLibraryMutation("listener-1", mutation)
        libraryDataSource.replaceLibrary("listener-1", emptyList(), syncedAtEpochMillis = 80L)

        val optimistic = libraryDataSource.observeLibrary("listener-1").first {
            it.pendingMutationCount == 1
        }
        assertEquals(listOf("artist-1"), optimistic.followedArtists.map { it.id })

        assertEquals(true, libraryDataSource.acknowledgeLibraryMutation("listener-1", mutation))
        libraryDataSource.applyRemoteLibraryChanges(
            "listener-1",
            listOf(
                StoredRemoteLibraryChange(
                    sequence = 9L,
                    kind = LibraryItemKind.Artist,
                    itemId = "artist-1",
                    saved = false,
                    savedAtEpochMillis = 90L,
                ),
            ),
        )

        val synchronized = libraryDataSource.observeLibrary("listener-1").first {
            it.pendingMutationCount == 0 && it.followedArtists.isEmpty()
        }
        assertEquals(true, synchronized.isEmpty)
        assertEquals(9L, libraryDataSource.getLibraryChangeCursor("listener-1"))
    }

    @Test
    fun playlistCacheIsTransactionalAndIsolatedByListener() = runTest {
        val playlist = PlaylistSummary(
            id = "90000000-0000-4000-8000-000000000001",
            name = "Road Trip",
            description = "Coast",
            trackCount = 0,
            revision = 1,
            createdAtEpochMillis = 1_000L,
            updatedAtEpochMillis = 1_000L,
        )

        playlistDataSource.replacePlaylists("listener-1", listOf(playlist), 2_000L)

        val listenerOne = playlistDataSource.observePlaylists("listener-1").first {
            it.lastSyncedAtEpochMillis == 2_000L
        }
        val listenerTwo = playlistDataSource.observePlaylists("listener-2").first()
        assertEquals(listOf(playlist), listenerOne.playlists)
        assertEquals(emptyList<PlaylistSummary>(), listenerTwo.playlists)
        assertEquals(null, listenerTwo.lastSyncedAtEpochMillis)
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
            editorialShelves = listOf(
                EditorialShelf(
                    id = "shelf-1",
                    title = "Rakyzu Essentials",
                    subtitle = "Selected by Rakyzu Music",
                    position = 0,
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
                ),
            ),
            lastSyncedAtEpochMillis = null,
        )
    }
}
