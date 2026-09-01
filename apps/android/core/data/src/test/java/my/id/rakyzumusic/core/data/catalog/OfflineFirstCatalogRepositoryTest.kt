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

    @Test
    fun authenticatedSearchReturnsValidatedBoundedPage() = runTest {
        val expected = CatalogSearchPage(
            artists = VALID_CATALOG.artists,
            albums = emptyList(),
            tracks = emptyList(),
            totalCount = 2,
            nextOffset = 1,
        )
        val remote = SearchRemoteDataSource(expected)
        val repository = OfflineFirstCatalogRepository(
            localDataSource = FakeLocalDataSource(VALID_CATALOG),
            remoteDataSource = remote,
        )

        assertEquals(CatalogSearchResult.Success(expected), repository.searchCatalog("signal", 0, 1))
        assertEquals(Triple("signal", 0, 1), remote.lastRequest)
    }

    @Test
    fun invalidSearchRequestFailsBeforeRemoteBoundary() = runTest {
        val remote = SearchRemoteDataSource(
            CatalogSearchPage(emptyList(), emptyList(), emptyList(), 0, null),
        )
        val repository = OfflineFirstCatalogRepository(
            localDataSource = FakeLocalDataSource(VALID_CATALOG),
            remoteDataSource = remote,
        )

        assertEquals(
            CatalogSearchResult.Failure(CatalogSearchFailure.InvalidRequest),
            repository.searchCatalog("x", 0, 30),
        )
        assertEquals(null, remote.lastRequest)
    }

    @Test
    fun malformedSearchPageFailsClosed() = runTest {
        val malformed = CatalogSearchPage(
            artists = VALID_CATALOG.artists,
            albums = emptyList(),
            tracks = emptyList(),
            totalCount = 1,
            nextOffset = 99,
        )
        val repository = OfflineFirstCatalogRepository(
            localDataSource = FakeLocalDataSource(VALID_CATALOG),
            remoteDataSource = SearchRemoteDataSource(malformed),
        )

        assertEquals(
            CatalogSearchResult.Failure(CatalogSearchFailure.InvalidPayload),
            repository.searchCatalog("signal", 0, 1),
        )
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

    private class SearchRemoteDataSource(
        private val page: CatalogSearchPage,
    ) : CatalogRemoteDataSource {
        var lastRequest: Triple<String, Int, Int>? = null

        override suspend fun fetchCatalog() = VALID_CATALOG

        override suspend fun searchCatalog(
            query: String,
            offset: Int,
            limit: Int,
        ): CatalogSearchPage {
            lastRequest = Triple(query, offset, limit)
            return page
        }
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
