package my.id.rakyzumusic.core.data.catalog

import kotlinx.coroutines.flow.Flow
import my.id.rakyzumusic.core.model.CatalogSnapshot
import my.id.rakyzumusic.core.model.HomeFeedSnapshot

interface CatalogRepository {
    fun observeCatalog(): Flow<CatalogSnapshot>

    fun observeHomeFeed(userId: String): Flow<HomeFeedSnapshot>

    suspend fun refresh(): CatalogRefreshResult

    suspend fun recordRecentlyPlayed(userId: String, trackId: String): Boolean
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
