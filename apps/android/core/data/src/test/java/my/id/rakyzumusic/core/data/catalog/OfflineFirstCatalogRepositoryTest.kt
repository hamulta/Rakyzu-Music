package my.id.rakyzumusic.core.data.catalog

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import my.id.rakyzumusic.core.database.catalog.CatalogLocalDataSource
import my.id.rakyzumusic.core.model.Album
import my.id.rakyzumusic.core.model.Artist
import my.id.rakyzumusic.core.model.CatalogSnapshot
import my.id.rakyzumusic.core.model.EditorialShelf
import my.id.rakyzumusic.core.model.HomeFeedSnapshot
import my.id.rakyzumusic.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class OfflineFirstCatalogRepositoryTest {
    @Test
    fun successfulRefreshAtomicallyReplacesLocalCatalog() = runTest {
        val local = FakeLocalDataSource(EMPTY_CATALOG)
        val repository = OfflineFirstCatalogRepository(
            localDataSource = local,
            remoteDataSource = FakeRemoteDataSource { VALID_CATALOG },
            currentTimeMillis = { 42L },
        )

        val result = repository.refresh()

        assertEquals(CatalogRefreshResult.Success(42L), result)
        assertEquals(VALID_CATALOG.copy(lastSyncedAtEpochMillis = 42L), local.current())
    }

    @Test
    fun remoteFailureKeepsLastKnownCatalog() = runTest {
        val cached = VALID_CATALOG.copy(lastSyncedAtEpochMillis = 21L)
        val local = FakeLocalDataSource(cached)
        val repository = OfflineFirstCatalogRepository(
            localDataSource = local,
            remoteDataSource = FakeRemoteDataSource { error("service unavailable") },
        )

        val result = repository.refresh()

        assertEquals(
            CatalogRefreshResult.Failure(CatalogRefreshFailure.ServiceUnavailable),
            result,
        )
        assertSame(cached, local.current())
    }

    @Test
    fun invalidRelationshipsNeverOverwriteCache() = runTest {
        val cached = VALID_CATALOG.copy(lastSyncedAtEpochMillis = 21L)
        val local = FakeLocalDataSource(cached)
        val invalid = VALID_CATALOG.copy(
            albums = VALID_CATALOG.albums.map { it.copy(artistId = "missing") },
        )
        val repository = OfflineFirstCatalogRepository(
            localDataSource = local,
            remoteDataSource = FakeRemoteDataSource { invalid },
        )

        val result = repository.refresh()

        assertEquals(
            CatalogRefreshResult.Failure(CatalogRefreshFailure.InvalidPayload),
            result,
        )
        assertSame(cached, local.current())
    }

    @Test
    fun invalidEditorialTrackNeverOverwritesCache() = runTest {
        val cached = VALID_CATALOG.copy(lastSyncedAtEpochMillis = 21L)
        val local = FakeLocalDataSource(cached)
        val invalid = VALID_CATALOG.copy(
            editorialShelves = listOf(
                EditorialShelf(
                    id = "shelf-1",
                    title = "Broken shelf",
                    subtitle = null,
                    position = 0,
                    tracks = listOf(VALID_CATALOG.tracks.single().copy(id = "missing")),
                ),
            ),
        )
        val repository = OfflineFirstCatalogRepository(
            localDataSource = local,
            remoteDataSource = FakeRemoteDataSource { invalid },
        )

        assertEquals(
            CatalogRefreshResult.Failure(CatalogRefreshFailure.InvalidPayload),
            repository.refresh(),
        )
        assertSame(cached, local.current())
    }

    @Test
    fun recentlyPlayedDelegatesUserTrackAndTimestampToLocalStorage() = runTest {
        val local = FakeLocalDataSource(VALID_CATALOG)
        val repository = OfflineFirstCatalogRepository(
            localDataSource = local,
            remoteDataSource = FakeRemoteDataSource { VALID_CATALOG },
            currentTimeMillis = { 73L },
        )

        assertEquals(true, repository.recordRecentlyPlayed("listener-1", "track-1"))
        assertEquals(Triple("listener-1", "track-1", 73L), local.lastRecorded)
    }

    private class FakeLocalDataSource(initial: CatalogSnapshot) : CatalogLocalDataSource {
        private val catalog = MutableStateFlow(initial)
        var lastRecorded: Triple<String, String, Long>? = null

        override fun observeCatalog(): Flow<CatalogSnapshot> = catalog

        override fun observeHomeFeed(userId: String): Flow<HomeFeedSnapshot> =
            MutableStateFlow(HomeFeedSnapshot(catalog.value, emptyList()))

        override suspend fun replaceCatalog(snapshot: CatalogSnapshot, syncedAtEpochMillis: Long) {
            catalog.value = snapshot.copy(lastSyncedAtEpochMillis = syncedAtEpochMillis)
        }

        override suspend fun recordRecentlyPlayed(
            userId: String,
            trackId: String,
            playedAtEpochMillis: Long,
        ): Boolean {
            lastRecorded = Triple(userId, trackId, playedAtEpochMillis)
            return true
        }

        fun current(): CatalogSnapshot = catalog.value
    }

    private fun interface FakeRemoteDataSource : CatalogRemoteDataSource {
        override suspend fun fetchCatalog(): CatalogSnapshot
    }

    private companion object {
        val EMPTY_CATALOG = CatalogSnapshot(emptyList(), emptyList(), emptyList(), null)
        val VALID_CATALOG = CatalogSnapshot(
            artists = listOf(Artist("artist-1", "Rakyzu Sessions")),
            albums = listOf(Album("album-1", "artist-1", "Signal Zero", "2026-08-29")),
            tracks = listOf(
                Track(
                    id = "track-1",
                    title = "Midnight Signal",
                    artist = "Rakyzu Sessions",
                    durationMs = 185_900L,
                    artistId = "artist-1",
                    albumId = "album-1",
                    albumTitle = "Signal Zero",
                ),
            ),
            lastSyncedAtEpochMillis = null,
        )
    }
}
