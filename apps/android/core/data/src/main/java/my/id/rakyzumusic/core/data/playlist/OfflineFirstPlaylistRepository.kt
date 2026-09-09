package my.id.rakyzumusic.core.data.playlist

import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import my.id.rakyzumusic.core.model.PlaylistDetail
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import my.id.rakyzumusic.core.database.catalog.PlaylistLocalDataSource
import my.id.rakyzumusic.core.model.PlaylistSnapshot
import my.id.rakyzumusic.core.model.PlaylistSummary

internal interface PlaylistRemoteDataSource {
    suspend fun detail(id: String): PlaylistDetail
    suspend fun mutate(id: String, revision: Long, mutation: PlaylistMutation): PlaylistDetail
    suspend fun getMine(limit: Int): List<PlaylistSummary>

    suspend fun create(id: String, name: String, description: String): PlaylistSummary
}

internal class OfflineFirstPlaylistRepository(
    private val localDataSource: PlaylistLocalDataSource,
    private val remoteDataSource: PlaylistRemoteDataSource,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val activeUserId: (() -> String?)? = null,
    private val artworkRemote: PlaylistArtworkRemoteDataSource? = null,
) : PlaylistRepository {
    private val operationMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun artwork(userId: String, playlistId: String): PlaylistArtworkResult {
        if (!isCurrentUser(userId) || !playlistId.isUuid()) return PlaylistArtworkResult.Failed
        val result = artworkRemote?.request(playlistId, "GET") ?: PlaylistArtworkResult.Failed
        return if (isCurrentUser(userId)) result else PlaylistArtworkResult.Failed
    }

    override suspend fun updateArtwork(userId: String, playlistId: String, png: ByteArray?): PlaylistArtworkResult {
        if (!isCurrentUser(userId) || !playlistId.isUuid() || (png != null && png.size !in 45..MAX_PLAYLIST_ARTWORK_BYTES)) {
            return PlaylistArtworkResult.Failed
        }
        val result = artworkRemote?.request(playlistId, if (png == null) "DELETE" else "PUT", png)
            ?: PlaylistArtworkResult.Failed
        return if (isCurrentUser(userId)) result else PlaylistArtworkResult.Failed
    }

    override fun observeDetail(userId: String, playlistId: String): Flow<PlaylistDetail?> =
        localDataSource.observeDetailPayload(userId, playlistId).map { payload ->
            payload?.let { runCatching { json.decodeFromString<PlaylistDetail>(it) }.getOrNull() }
                ?.takeIf { it.isValidDetail(playlistId) }
        }

    override suspend fun refreshDetail(userId: String, playlistId: String): PlaylistActionResult =
        operationMutex.withLock {
            if (!isCurrentUser(userId) || !playlistId.isUuid()) return invalidRequest()
            playlistRequest { saveDetail(userId, playlistId, remoteDataSource.detail(playlistId)) }
        }

    override suspend fun mutate(
        userId: String, playlistId: String, revision: Long, mutation: PlaylistMutation,
    ): PlaylistActionResult = operationMutex.withLock {
        if (!isCurrentUser(userId) || !playlistId.isUuid() || revision < 1 || !mutation.isValid()) {
            return invalidRequest()
        }
        val result = playlistRequest {
            val detail = remoteDataSource.mutate(playlistId, revision, mutation)
            if (detail.playlist.revision !in revision..(revision + 1)) throw InvalidPlaylistPayloadException()
            saveDetail(userId, playlistId, detail)
        }
        if (result == PlaylistActionResult.Failure(PlaylistFailure.Conflict)) {
            // Never retry a destructive intent against a newer snapshot automatically.
            playlistRequest { saveDetail(userId, playlistId, remoteDataSource.detail(playlistId)) }
        }
        result
    }

    private suspend fun saveDetail(userId: String, id: String, detail: PlaylistDetail): PlaylistActionResult {
        if (!detail.isValidDetail(id)) throw InvalidPlaylistPayloadException()
        if (!isCurrentUser(userId)) return invalidRequest()
        localDataSource.storeDetailPayload(userId, detail.playlist, json.encodeToString(detail))
        return PlaylistActionResult.Success(detail.playlist)
    }

    private fun isCurrentUser(userId: String) =
        userId.isNotBlank() && (activeUserId == null || activeUserId.invoke() == userId)

    override fun observePlaylists(userId: String): Flow<PlaylistSnapshot> =
        localDataSource.observePlaylists(userId)

    override suspend fun refresh(userId: String): PlaylistActionResult = operationMutex.withLock {
        if (!isCurrentUser(userId)) return invalidRequest()
        playlistRequest {
            val playlists = remoteDataSource.getMine(PLAYLIST_LIMIT)
            if (!playlists.isValidCollection()) throw InvalidPlaylistPayloadException()
            if (!isCurrentUser(userId)) return@playlistRequest invalidRequest()
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
            !isCurrentUser(userId) || normalizedName.isBlank() ||
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
            if (!isCurrentUser(userId)) return@playlistRequest invalidRequest()
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

private fun String.isUuid(): Boolean = runCatching {
    UUID.fromString(this).toString().equals(this, ignoreCase = true)
}.getOrDefault(false)

private fun Throwable.toPlaylistFailure(): PlaylistFailure = when (this) {
    is InvalidPlaylistPayloadException -> PlaylistFailure.InvalidPayload
    is HttpRequestTimeoutException,
    is HttpRequestException,
    -> PlaylistFailure.NetworkUnavailable
    is PostgrestRestException -> when (code) {
        "40001" -> PlaylistFailure.Conflict
        "22023" -> PlaylistFailure.InvalidRequest
        else -> PlaylistFailure.ServiceUnavailable
    }
    else -> PlaylistFailure.ServiceUnavailable
}

internal class InvalidPlaylistPayloadException : IllegalStateException("Invalid playlist payload")

private fun invalidRequest() = PlaylistActionResult.Failure(PlaylistFailure.InvalidRequest)

private fun PlaylistDetail.isValidDetail(id: String): Boolean =
    playlist.id == id && playlist.isValid() && items.size <= 500 && playlist.trackCount == items.size &&
        items.map { it.trackId }.distinct().size == items.size && items.all { item ->
            item.trackId.isUuid() && (item.track?.let {
                it.id == item.trackId && it.title.isNotBlank() && it.artist.isNotBlank() &&
                    it.albumId.isUuid() && it.artistId.isUuid() && it.durationMs in 1000L..86400000L &&
                    it.discNumber > 0 && it.trackNumber > 0
            } ?: true)
        }

private fun PlaylistMutation.isValid(): Boolean = when (this) {
    is PlaylistMutation.Add -> trackId.isUuid()
    is PlaylistMutation.Remove -> trackId.isUuid()
    is PlaylistMutation.Reorder -> trackIds.size <= 500 && trackIds.all { it.isUuid() } &&
        trackIds.distinct().size == trackIds.size
    is PlaylistMutation.Metadata -> name.isNotBlank() && name.length <= 100 && description.length <= 300
}
