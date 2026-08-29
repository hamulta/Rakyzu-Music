package my.id.rakyzumusic.core.data.catalog

import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import my.id.rakyzumusic.core.database.catalog.CatalogLocalDataSource
import my.id.rakyzumusic.core.model.CatalogSnapshot

internal interface CatalogRemoteDataSource {
    suspend fun fetchCatalog(): CatalogSnapshot
}

internal class OfflineFirstCatalogRepository(
    private val localDataSource: CatalogLocalDataSource,
    private val remoteDataSource: CatalogRemoteDataSource,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : CatalogRepository {
    override fun observeCatalog(): Flow<CatalogSnapshot> = localDataSource.observeCatalog()

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
    return tracks.all { track ->
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
}

private fun <T> List<T>.hasDuplicates(): Boolean = size != toSet().size

private fun Throwable.toCatalogRefreshFailure(): CatalogRefreshFailure = when (this) {
    is InvalidCatalogPayloadException -> CatalogRefreshFailure.InvalidPayload
    is HttpRequestTimeoutException,
    is HttpRequestException,
    -> CatalogRefreshFailure.NetworkUnavailable
    is PostgrestRestException -> CatalogRefreshFailure.ServiceUnavailable
    else -> CatalogRefreshFailure.ServiceUnavailable
}

internal class InvalidCatalogPayloadException : IllegalStateException("Invalid catalog relationship")
