package my.id.rakyzumusic.core.data.catalog

import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import my.id.rakyzumusic.core.database.catalog.CatalogLocalDataSource
import my.id.rakyzumusic.core.model.CatalogSnapshot
import my.id.rakyzumusic.core.model.DiscoveryMode
import my.id.rakyzumusic.core.model.HomeFeedSnapshot
import my.id.rakyzumusic.core.model.Track
import my.id.rakyzumusic.core.model.EditorialPlacement

internal interface CatalogRemoteDataSource {
    suspend fun fetchCatalog(): CatalogSnapshot

    suspend fun searchCatalog(query: String, offset: Int, limit: Int): CatalogSearchPage =
        throw UnsupportedOperationException("Remote catalog search is unavailable")

    suspend fun recordEditorialGroupOpen(shelfId: String): Boolean = false
}

internal class OfflineFirstCatalogRepository(
    private val localDataSource: CatalogLocalDataSource,
    private val remoteDataSource: CatalogRemoteDataSource,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : CatalogRepository {
    override fun observeCatalog(): Flow<CatalogSnapshot> = localDataSource.observeCatalog()

    override fun observeHomeFeed(userId: String): Flow<HomeFeedSnapshot> =
        localDataSource.observeHomeFeed(userId)

    override suspend fun recordRecentlyPlayed(userId: String, trackId: String): Boolean =
        localDataSource.recordRecentlyPlayed(
            userId = userId,
            trackId = trackId,
            playedAtEpochMillis = currentTimeMillis(),
        )

    override suspend fun recordEditorialGroupOpen(shelfId: String): Boolean = try {
        if (shelfId.isBlank()) false else remoteDataSource.recordEditorialGroupOpen(shelfId)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        false
    }

    override suspend fun setPersonalizationEnabled(userId: String, enabled: Boolean): Boolean =
        localDataSource.setPersonalizationEnabled(userId, enabled)

    override suspend fun setDiscoveryMode(userId: String, mode: DiscoveryMode): Boolean =
        localDataSource.setDiscoveryMode(userId, mode)

    override suspend fun setRecommendationHidden(
        userId: String,
        trackId: String,
        hidden: Boolean,
    ): Boolean = localDataSource.setRecommendationHidden(userId, trackId, hidden)

    override suspend fun setTasteSignalExcluded(
        userId: String,
        trackId: String,
        excluded: Boolean,
    ): Boolean = localDataSource.setTasteSignalExcluded(userId, trackId, excluded)

    override suspend fun clearPersonalizationData(userId: String): Boolean =
        localDataSource.clearPersonalizationData(userId)

    override suspend fun refresh(): CatalogRefreshResult = try {
        val snapshot = remoteDataSource.fetchCatalog()
        if (!snapshot.isValidCatalog()) {
            CatalogRefreshResult.Failure(CatalogRefreshFailure.InvalidPayload)
        } else {
            val syncedAt = currentTimeMillis()
            localDataSource.replaceCatalog(snapshot, syncedAt)
            CatalogRefreshResult.Success(syncedAt)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        CatalogRefreshResult.Failure(error.toCatalogRefreshFailure())
    }

    override suspend fun searchCatalog(
        query: String,
        offset: Int,
        limit: Int,
    ): CatalogSearchResult {
        val boundedQuery = query.trim().replace(WHITESPACE, " ")
        if (boundedQuery.length !in MIN_SEARCH_QUERY_LENGTH..MAX_SEARCH_QUERY_LENGTH ||
            offset !in 0..MAX_SEARCH_OFFSET ||
            limit !in 1..MAX_SEARCH_PAGE_SIZE
        ) {
            return CatalogSearchResult.Failure(CatalogSearchFailure.InvalidRequest)
        }

        return try {
            val page = remoteDataSource.searchCatalog(boundedQuery, offset, limit)
            if (page.isValid(offset, limit)) {
                CatalogSearchResult.Success(page)
            } else {
                CatalogSearchResult.Failure(CatalogSearchFailure.InvalidPayload)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            CatalogSearchResult.Failure(error.toCatalogSearchFailure())
        }
    }
}

private fun CatalogSearchPage.isValid(requestOffset: Int, requestLimit: Int): Boolean {
    val displayedCount = artists.size + albums.size + tracks.size
    if (displayedCount > requestLimit || totalCount < requestOffset + displayedCount) return false
    if (nextOffset != null && (nextOffset <= requestOffset || nextOffset > totalCount)) return false

    val ids = artists.map { "artist:${it.id}" } +
        albums.map { "album:${it.album.id}" } +
        tracks.map { "track:${it.id}" }
    return ids.none { it.substringAfter(':').isBlank() } && ids.size == ids.toSet().size
}

private fun CatalogSnapshot.isValidCatalog(): Boolean {
    if (artists.map { it.id }.hasDuplicates() ||
        albums.map { it.id }.hasDuplicates() ||
        tracks.map { it.id }.hasDuplicates()
    ) {
        return false
    }

    val artistIds = artists.mapTo(mutableSetOf()) { it.id }
    val albumsById = albums.associateBy { it.id }
    if (artists.any { it.id.isBlank() || it.name.isBlank() } ||
        albums.any {
            it.id.isBlank() || it.title.isBlank() || it.artistId !in artistIds
        }
    ) {
        return false
    }

    val positions = mutableSetOf<Triple<String, Int, Int>>()
    val tracksAreValid = tracks.all { track ->
        val album = albumsById[track.albumId]
        val position = Triple(track.albumId, track.discNumber, track.trackNumber)
        track.id.isNotBlank() &&
            track.title.isNotBlank() &&
            track.durationMs in 1_000L..86_400_000L &&
            track.discNumber > 0 &&
            track.trackNumber > 0 &&
            album != null &&
            track.artistId == album.artistId &&
            positions.add(position)
    }
    if (!tracksAreValid) return false

    val trackIds = tracks.mapTo(mutableSetOf(), Track::id)
    val shelfIds = mutableSetOf<String>()
    val shelfPositions = mutableSetOf<Int>()
    val placementCounts = editorialShelves.groupingBy { shelf ->
        EditorialPlacement.fromPosition(shelf.position)
    }.eachCount()
    if (
        placementCounts[null] != null ||
        EditorialPlacement.entries.any { placement ->
            (placementCounts[placement] ?: 0) > placement.maximumCards
        }
    ) return false
    return editorialShelves.all { shelf ->
        shelf.id.isNotBlank() &&
            shelf.title.trim().length in 1..80 &&
            (shelf.subtitle?.let { it.trim().length in 1..160 } ?: true) &&
            EditorialPlacement.fromPosition(shelf.position) != null &&
            (shelf.cardLabel?.let { it.trim().length in 1..40 } ?: true) &&
            COLOR_HEX.matches(shelf.colorHex) &&
            shelf.globalScore >= 0L &&
            shelf.tracks.size in 1..MAX_EDITORIAL_TRACKS &&
            shelfIds.add(shelf.id) &&
            shelfPositions.add(shelf.position) &&
            shelf.tracks.map { it.id }.let { ids ->
                ids.size == ids.toSet().size && ids.all(trackIds::contains)
            }
    }
}

private val COLOR_HEX = Regex("^#[0-9A-Fa-f]{6}$")
private const val MAX_EDITORIAL_TRACKS = 50

private fun <T> List<T>.hasDuplicates(): Boolean = size != toSet().size

private fun Throwable.toCatalogRefreshFailure(): CatalogRefreshFailure = when (this) {
    is InvalidCatalogPayloadException -> CatalogRefreshFailure.InvalidPayload
    is HttpRequestTimeoutException,
    is HttpRequestException,
    -> CatalogRefreshFailure.NetworkUnavailable
    is PostgrestRestException -> CatalogRefreshFailure.ServiceUnavailable
    else -> CatalogRefreshFailure.ServiceUnavailable
}

private fun Throwable.toCatalogSearchFailure(): CatalogSearchFailure = when (this) {
    is InvalidCatalogPayloadException -> CatalogSearchFailure.InvalidPayload
    is HttpRequestTimeoutException,
    is HttpRequestException,
    -> CatalogSearchFailure.NetworkUnavailable
    is PostgrestRestException -> CatalogSearchFailure.ServiceUnavailable
    else -> CatalogSearchFailure.ServiceUnavailable
}

internal class InvalidCatalogPayloadException : IllegalStateException("Invalid catalog relationship")

private val WHITESPACE = Regex("\\s+")
private const val MIN_SEARCH_QUERY_LENGTH = 2
private const val MAX_SEARCH_QUERY_LENGTH = 100
private const val MAX_SEARCH_PAGE_SIZE = 50
private const val MAX_SEARCH_OFFSET = 10_000
