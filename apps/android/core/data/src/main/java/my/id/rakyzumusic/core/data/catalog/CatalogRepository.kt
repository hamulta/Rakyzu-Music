package my.id.rakyzumusic.core.data.catalog

import kotlinx.coroutines.flow.Flow
import my.id.rakyzumusic.core.model.Album
import my.id.rakyzumusic.core.model.Artist
import my.id.rakyzumusic.core.model.CatalogSnapshot
import my.id.rakyzumusic.core.model.HomeFeedSnapshot
import my.id.rakyzumusic.core.model.Track

interface CatalogRepository {
    fun observeCatalog(): Flow<CatalogSnapshot>

    fun observeHomeFeed(userId: String): Flow<HomeFeedSnapshot>

    suspend fun refresh(): CatalogRefreshResult

    suspend fun recordRecentlyPlayed(userId: String, trackId: String): Boolean

    suspend fun searchCatalog(
        query: String,
        offset: Int,
        limit: Int,
    ): CatalogSearchResult = CatalogSearchResult.Failure(CatalogSearchFailure.ServiceUnavailable)
}

data class CatalogSearchAlbum(
    val album: Album,
    val artistName: String,
)

data class CatalogSearchPage(
    val artists: List<Artist>,
    val albums: List<CatalogSearchAlbum>,
    val tracks: List<Track>,
    val totalCount: Int,
    val nextOffset: Int?,
)

sealed interface CatalogSearchResult {
    data class Success(val page: CatalogSearchPage) : CatalogSearchResult

    data class Failure(val reason: CatalogSearchFailure) : CatalogSearchResult
}

enum class CatalogSearchFailure {
    NetworkUnavailable,
    ServiceUnavailable,
    InvalidRequest,
    InvalidPayload,
}

sealed interface CatalogRefreshResult {
    data class Success(val syncedAtEpochMillis: Long) : CatalogRefreshResult

    data class Failure(val reason: CatalogRefreshFailure) : CatalogRefreshResult
}

enum class CatalogRefreshFailure {
    NetworkUnavailable,
    ServiceUnavailable,
    InvalidPayload,
}
