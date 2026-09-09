package my.id.rakyzumusic.core.data.playlist

import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import my.id.rakyzumusic.core.database.catalog.PlaylistLocalDataSource
import my.id.rakyzumusic.core.model.PlaylistSnapshot
import my.id.rakyzumusic.core.model.PlaylistSummary

internal interface PlaylistRemoteDataSource {
    suspend fun getMine(limit: Int): List<PlaylistSummary>

    suspend fun create(id: String, name: String, description: String): PlaylistSummary
}

internal class OfflineFirstPlaylistRepository(
    private val localDataSource: PlaylistLocalDataSource,
    private val remoteDataSource: PlaylistRemoteDataSource,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) : PlaylistRepository {
    private val operationMutex = Mutex()

    override fun observePlaylists(userId: String): Flow<PlaylistSnapshot> =
        localDataSource.observePlaylists(userId)

    override suspend fun refresh(userId: String): PlaylistActionResult = operationMutex.withLock {
        if (userId.isBlank()) return PlaylistActionResult.Failure(PlaylistFailure.InvalidRequest)
        playlistRequest {
            val playlists = remoteDataSource.getMine(PLAYLIST_LIMIT)
            if (!playlists.isValidCollection()) throw InvalidPlaylistPayloadException()
            localDataSource.replacePlaylists(userId, playlists, currentTimeMillis())
            PlaylistActionResult.Success()
        }
    }

    override suspend fun create(
        userId: String,
        name: String,
        description: String,
    ): PlaylistActionResult = operationMutex.withLock {
        val normalizedName = name.trim().replace(WHITESPACE, " ")
        val normalizedDescription = description.trim()
        if (
            userId.isBlank() || normalizedName.isBlank() ||
            normalizedName.length > MAX_NAME_LENGTH ||
            normalizedDescription.length > MAX_DESCRIPTION_LENGTH
        ) {
            return PlaylistActionResult.Failure(PlaylistFailure.InvalidRequest)
        }
        playlistRequest {
            val requestedId = idFactory()
            if (!requestedId.isUuid()) throw InvalidPlaylistPayloadException()
            val playlist = remoteDataSource.create(
                id = requestedId,
                name = normalizedName,
                description = normalizedDescription,
            )
            if (!playlist.isValid() || playlist.id != requestedId) {
                throw InvalidPlaylistPayloadException()
            }
            localDataSource.upsertPlaylist(userId, playlist)
            PlaylistActionResult.Success(playlist)
        }
    }

    private companion object {
        const val PLAYLIST_LIMIT = 100
        const val MAX_NAME_LENGTH = 100
        const val MAX_DESCRIPTION_LENGTH = 300
        val WHITESPACE = Regex("\\s+")
    }
}

private suspend fun playlistRequest(
    block: suspend () -> PlaylistActionResult,
): PlaylistActionResult = try {
    block()
} catch (error: CancellationException) {
    throw error
} catch (error: Throwable) {
    PlaylistActionResult.Failure(error.toPlaylistFailure())
}

private fun List<PlaylistSummary>.isValidCollection(): Boolean =
    size <= 100 && all(PlaylistSummary::isValid) && map(PlaylistSummary::id).distinct().size == size

private fun PlaylistSummary.isValid(): Boolean =
    id.isUuid() && name.isNotBlank() && name.length <= 100 && description.length <= 300 &&
        trackCount >= 0 && revision >= 1L && createdAtEpochMillis >= 0L &&
        updatedAtEpochMillis >= createdAtEpochMillis

private fun String.isUuid(): Boolean = runCatching { UUID.fromString(this) }.isSuccess

private fun Throwable.toPlaylistFailure(): PlaylistFailure = when (this) {
    is InvalidPlaylistPayloadException -> PlaylistFailure.InvalidPayload
    is HttpRequestTimeoutException,
    is HttpRequestException,
    -> PlaylistFailure.NetworkUnavailable
    is PostgrestRestException -> PlaylistFailure.ServiceUnavailable
    else -> PlaylistFailure.ServiceUnavailable
}

internal class InvalidPlaylistPayloadException : IllegalStateException("Invalid playlist payload")
