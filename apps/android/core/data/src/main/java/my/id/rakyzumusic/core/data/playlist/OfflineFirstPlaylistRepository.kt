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
import my.id.rakyzumusic.core.model.PlaylistInvite
import my.id.rakyzumusic.core.model.PlaylistRole
import my.id.rakyzumusic.core.model.PlaylistSnapshot
import my.id.rakyzumusic.core.model.PlaylistSummary
import my.id.rakyzumusic.core.model.PlaylistVisibility

internal interface PlaylistRemoteDataSource {
    suspend fun detail(id: String): PlaylistDetail
    suspend fun mutate(id: String, revision: Long, mutation: PlaylistMutation): PlaylistDetail
    suspend fun getMine(limit: Int): List<PlaylistSummary>

    suspend fun create(id: String, name: String, description: String): PlaylistSummary

    suspend fun detailPage(id: String, offset: Int, limit: Int): PlaylistDetail =
        if (offset == 0) detail(id) else throw InvalidPlaylistPayloadException()

    suspend fun mutateIdempotent(
        id: String,
        operationId: String,
        revision: Long,
        mutation: PlaylistMutation,
    ): PlaylistDetail = mutate(id, revision, mutation)

    suspend fun getPage(
        limit: Int,
        beforeUpdatedAtEpochMillis: Long?,
        beforeId: String?,
    ): PlaylistRemotePage = PlaylistRemotePage(getMine(limit), false)

    suspend fun createInvite(id: String, role: PlaylistRole): PlaylistInvite =
        throw UnsupportedOperationException()
    suspend fun acceptInvite(token: String): PlaylistDetail = throw UnsupportedOperationException()
    suspend fun removeMember(id: String, memberId: String): PlaylistDetail =
        throw UnsupportedOperationException()
    suspend fun leave(id: String): Unit = throw UnsupportedOperationException()
    suspend fun setVisibility(id: String, revision: Long, visibility: PlaylistVisibility): PlaylistDetail =
        throw UnsupportedOperationException()
    suspend fun setFollowing(id: String, following: Boolean): PlaylistDetail =
        throw UnsupportedOperationException()
}

internal data class PlaylistRemotePage(
    val playlists: List<PlaylistSummary>,
    val hasMore: Boolean,
)

internal class OfflineFirstPlaylistRepository(
    private val localDataSource: PlaylistLocalDataSource,
    private val remoteDataSource: PlaylistRemoteDataSource,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val operationIdFactory: () -> String = { UUID.randomUUID().toString() },
    private val activeUserId: (() -> String?)? = null,
    private val artworkRemote: PlaylistArtworkRemoteDataSource? = null,
) : PlaylistRepository {
    private val operationMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val listCursors = mutableMapOf<String, Pair<Long, String>?>()
    private val listHasMore = mutableMapOf<String, Boolean>()

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
            playlistRequest {
                saveDetail(userId, playlistId, remoteDataSource.detailPage(playlistId, 0, DETAIL_PAGE_SIZE))
            }
        }

    override suspend fun loadMoreDetail(userId: String, playlistId: String): PlaylistActionResult =
        operationMutex.withLock {
            if (!isCurrentUser(userId) || !playlistId.isUuid()) return invalidRequest()
            val saved = localDataSource.readDetailPayload(userId, playlistId)
                ?.let { runCatching { json.decodeFromString<PlaylistDetail>(it) }.getOrNull() }
                ?: return invalidRequest()
            val offset = saved.nextOffset ?: return PlaylistActionResult.Success(saved.playlist)
            playlistRequest {
                val page = remoteDataSource.detailPage(playlistId, offset, DETAIL_PAGE_SIZE)
                if (page.playlist.id != saved.playlist.id ||
                    page.playlist.revision != saved.playlist.revision ||
                    page.totalItems != saved.totalItems
                ) throw InvalidPlaylistPayloadException()
                val merged = page.copy(
                    items = saved.items + page.items,
                    members = page.members.ifEmpty { saved.members },
                )
                saveDetail(userId, playlistId, merged)
            }
        }

    override suspend fun mutate(
        userId: String, playlistId: String, revision: Long, mutation: PlaylistMutation,
    ): PlaylistActionResult = operationMutex.withLock {
        if (!isCurrentUser(userId) || !playlistId.isUuid() || revision < 1 || !mutation.isValid()) {
            return invalidRequest()
        }
        val operationId = operationIdFactory()
        if (!operationId.isUuid()) return invalidRequest()
        val result = try {
            val detail = remoteDataSource.mutateIdempotent(playlistId, operationId, revision, mutation)
            if (detail.playlist.revision !in revision..(revision + 1)) {
                throw InvalidPlaylistPayloadException()
            }
            saveDetail(userId, playlistId, detail)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (error.toPlaylistFailure() == PlaylistFailure.NetworkUnavailable) {
                localDataSource.enqueueMutation(
                    userId = userId,
                    playlistId = playlistId,
                    operationId = operationId,
                    expectedRevision = revision,
                    payload = json.encodeToString<PlaylistMutation>(mutation),
                    queuedAtEpochMillis = currentTimeMillis(),
                )
                PlaylistActionResult.Queued(localDataSource.pendingMutationCount(userId, playlistId))
            } else {
                PlaylistActionResult.Failure(error.toPlaylistFailure())
            }
        }
        if (result == PlaylistActionResult.Failure(PlaylistFailure.Conflict)) {
            // Never retry a destructive intent against a newer snapshot automatically.
            playlistRequest { saveDetail(userId, playlistId, remoteDataSource.detail(playlistId)) }
        }
        result
    }

    override suspend fun retryPending(userId: String, playlistId: String): PlaylistActionResult =
        operationMutex.withLock {
            if (!isCurrentUser(userId) || !playlistId.isUuid()) return invalidRequest()
            val pending = localDataSource.pendingMutations(userId, playlistId, OUTBOX_BATCH_SIZE)
            var latest: PlaylistSummary? = null
            for (entry in pending) {
                val mutation = runCatching {
                    json.decodeFromString<PlaylistMutation>(entry.payload)
                }.getOrNull()
                if (mutation == null || !mutation.isValid() || !entry.operationId.isUuid()) {
                    localDataSource.acknowledgeMutation(userId, entry.operationId)
                    return PlaylistActionResult.Failure(PlaylistFailure.InvalidPayload)
                }
                try {
                    localDataSource.recordMutationAttempt(userId, entry.operationId)
                    val detail = remoteDataSource.mutateIdempotent(
                        playlistId,
                        entry.operationId,
                        entry.expectedRevision,
                        mutation,
                    )
                    saveDetail(userId, playlistId, detail)
                    localDataSource.acknowledgeMutation(userId, entry.operationId)
                    latest = detail.playlist
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    val failure = error.toPlaylistFailure()
                    if (failure == PlaylistFailure.Conflict) {
                        playlistRequest {
                            saveDetail(
                                userId,
                                playlistId,
                                remoteDataSource.detailPage(playlistId, 0, DETAIL_PAGE_SIZE),
                            )
                        }
                    }
                    val count = localDataSource.pendingMutationCount(userId, playlistId)
                    return if (failure == PlaylistFailure.NetworkUnavailable) {
                        PlaylistActionResult.Queued(count)
                    } else {
                        PlaylistActionResult.Failure(failure)
                    }
                }
            }
            PlaylistActionResult.Success(latest)
        }

    override suspend fun createInvite(
        userId: String,
        playlistId: String,
        role: PlaylistRole,
    ): PlaylistInviteResult = operationMutex.withLock {
        if (!isCurrentUser(userId) || !playlistId.isUuid() || role !in setOf(PlaylistRole.Editor, PlaylistRole.Viewer)) {
            return PlaylistInviteResult.Failure(PlaylistFailure.InvalidRequest)
        }
        try {
            val invite = remoteDataSource.createInvite(playlistId, role)
            if (!invite.token.isUuid() || invite.role != role || invite.expiresAtEpochMillis <= currentTimeMillis()) {
                throw InvalidPlaylistPayloadException()
            }
            PlaylistInviteResult.Success(invite)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            PlaylistInviteResult.Failure(error.toPlaylistFailure())
        }
    }

    override suspend fun acceptInvite(userId: String, token: String): PlaylistActionResult =
        operationMutex.withLock {
            if (!isCurrentUser(userId) || !token.isUuid()) return invalidRequest()
            playlistRequest {
                val detail = remoteDataSource.acceptInvite(token)
                saveDetail(userId, detail.playlist.id, detail)
            }
        }

    override suspend fun removeMember(
        userId: String,
        playlistId: String,
        memberId: String,
    ): PlaylistActionResult = accessMutation(userId, playlistId) {
        remoteDataSource.removeMember(playlistId, memberId)
    }

    override suspend fun leave(userId: String, playlistId: String): PlaylistActionResult =
        operationMutex.withLock {
            if (!isCurrentUser(userId) || !playlistId.isUuid()) return invalidRequest()
            playlistRequest {
                remoteDataSource.leave(playlistId)
                val page = remoteDataSource.getPage(LIST_PAGE_SIZE, null, null)
                listHasMore[userId] = page.hasMore
                listCursors[userId] = page.playlists.lastOrNull()?.let { it.updatedAtEpochMillis to it.id }
                localDataSource.replacePlaylists(userId, page.playlists, currentTimeMillis())
                PlaylistActionResult.Success()
            }
        }

    override suspend fun setVisibility(
        userId: String,
        playlistId: String,
        revision: Long,
        visibility: PlaylistVisibility,
    ): PlaylistActionResult = accessMutation(userId, playlistId) {
        remoteDataSource.setVisibility(playlistId, revision, visibility)
    }

    override suspend fun setFollowing(
        userId: String,
        playlistId: String,
        following: Boolean,
    ): PlaylistActionResult = accessMutation(userId, playlistId) {
        remoteDataSource.setFollowing(playlistId, following)
    }

    private suspend fun accessMutation(
        userId: String,
        playlistId: String,
        remote: suspend () -> PlaylistDetail,
    ): PlaylistActionResult = operationMutex.withLock {
        if (!isCurrentUser(userId) || !playlistId.isUuid()) return invalidRequest()
        playlistRequest { saveDetail(userId, playlistId, remote()) }
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
        localDataSource.observePlaylists(userId).map {
            it.copy(hasMore = listHasMore[userId] == true)
        }

    override suspend fun refresh(userId: String): PlaylistActionResult = operationMutex.withLock {
        if (!isCurrentUser(userId)) return invalidRequest()
        playlistRequest {
            val page = remoteDataSource.getPage(LIST_PAGE_SIZE, null, null)
            if (!page.playlists.isValidCollection(LIST_PAGE_SIZE)) throw InvalidPlaylistPayloadException()
            if (!isCurrentUser(userId)) return@playlistRequest invalidRequest()
            listHasMore[userId] = page.hasMore
            listCursors[userId] = page.playlists.lastOrNull()?.let { it.updatedAtEpochMillis to it.id }
            localDataSource.replacePlaylists(userId, page.playlists, currentTimeMillis())
            PlaylistActionResult.Success()
        }
    }

    override suspend fun loadMore(userId: String): PlaylistActionResult = operationMutex.withLock {
        if (!isCurrentUser(userId) || listHasMore[userId] != true) return PlaylistActionResult.Success()
        val cursor = listCursors[userId] ?: return invalidRequest()
        playlistRequest {
            val page = remoteDataSource.getPage(LIST_PAGE_SIZE, cursor.first, cursor.second)
            if (!page.playlists.isValidCollection(LIST_PAGE_SIZE)) throw InvalidPlaylistPayloadException()
            listHasMore[userId] = page.hasMore
            listCursors[userId] = page.playlists.lastOrNull()?.let { it.updatedAtEpochMillis to it.id }
                ?: cursor
            localDataSource.appendPlaylists(userId, page.playlists)
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
        const val LIST_PAGE_SIZE = 30
        const val DETAIL_PAGE_SIZE = 100
        const val OUTBOX_BATCH_SIZE = 20
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

private fun List<PlaylistSummary>.isValidCollection(limit: Int = 100): Boolean =
    size <= limit && all(PlaylistSummary::isValid) && map(PlaylistSummary::id).distinct().size == size

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
    is java.io.IOException,
    -> PlaylistFailure.NetworkUnavailable
    is PostgrestRestException -> when (code) {
        "PT409", "40001" -> PlaylistFailure.Conflict
        "22023" -> PlaylistFailure.InvalidRequest
        else -> PlaylistFailure.ServiceUnavailable
    }
    else -> PlaylistFailure.ServiceUnavailable
}

internal class InvalidPlaylistPayloadException : IllegalStateException("Invalid playlist payload")

private fun invalidRequest() = PlaylistActionResult.Failure(PlaylistFailure.InvalidRequest)

private fun PlaylistDetail.isValidDetail(id: String): Boolean =
    playlist.id == id && playlist.isValid() && items.size <= 500 &&
        totalItems == playlist.trackCount && items.size <= totalItems &&
        (nextOffset == null || nextOffset in items.size..totalItems) &&
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
