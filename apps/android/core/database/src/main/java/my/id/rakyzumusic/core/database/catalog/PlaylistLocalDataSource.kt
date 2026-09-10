package my.id.rakyzumusic.core.database.catalog

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import my.id.rakyzumusic.core.model.PlaylistSnapshot
import my.id.rakyzumusic.core.model.PlaylistSummary
import my.id.rakyzumusic.core.model.PlaylistRole
import my.id.rakyzumusic.core.model.PlaylistVisibility

interface PlaylistLocalDataSource {
    fun observeDetailPayload(userId: String, playlistId: String): Flow<String?>

    suspend fun storeDetailPayload(userId: String, playlist: PlaylistSummary, payload: String)

    suspend fun readDetailPayload(userId: String, playlistId: String): String? = null

    fun observePlaylists(userId: String): Flow<PlaylistSnapshot>

    suspend fun replacePlaylists(
        userId: String,
        playlists: List<PlaylistSummary>,
        syncedAtEpochMillis: Long,
    )

    suspend fun upsertPlaylist(userId: String, playlist: PlaylistSummary)

    suspend fun appendPlaylists(userId: String, playlists: List<PlaylistSummary>) {
        playlists.forEach { upsertPlaylist(userId, it) }
    }

    suspend fun enqueueMutation(
        userId: String,
        playlistId: String,
        operationId: String,
        expectedRevision: Long,
        payload: String,
        queuedAtEpochMillis: Long,
    ) = Unit

    suspend fun pendingMutations(
        userId: String,
        playlistId: String,
        limit: Int,
    ): List<StoredPlaylistMutation> = emptyList()

    suspend fun pendingMutationCount(userId: String, playlistId: String): Int = 0

    suspend fun acknowledgeMutation(userId: String, operationId: String) = Unit

    suspend fun recordMutationAttempt(userId: String, operationId: String) = Unit
}

data class StoredPlaylistMutation(
    val operationId: String,
    val expectedRevision: Long,
    val payload: String,
    val queuedAtEpochMillis: Long,
    val attemptCount: Int,
)

internal class RoomPlaylistLocalDataSource(
    private val database: RakyzuDatabase,
) : PlaylistLocalDataSource {
    private val dao = database.catalogDao()

    override fun observeDetailPayload(userId: String, playlistId: String) =
        dao.observePlaylistDetail(userId, playlistId)

    override suspend fun storeDetailPayload(userId: String, playlist: PlaylistSummary, payload: String) {
        require(userId.isNotBlank())
        dao.storePlaylistDetail(PlaylistDetailEntity(userId, playlist.id, payload), playlist.toEntity(userId))
    }

    override suspend fun readDetailPayload(userId: String, playlistId: String) =
        dao.getPlaylistDetail(userId, playlistId)

    override fun observePlaylists(userId: String): Flow<PlaylistSnapshot> =
        database.invalidationTracker
            .createFlow("playlists", "sync_metadata")
            .map {
                PlaylistSnapshot(
                    playlists = dao.getPlaylists(userId).map(PlaylistEntity::toDomain),
                    lastSyncedAtEpochMillis = dao.getLastSuccessfulSyncEpochMillis(
                        CatalogDao.playlistSyncKey(userId),
                    ),
                )
            }

    override suspend fun replacePlaylists(
        userId: String,
        playlists: List<PlaylistSummary>,
        syncedAtEpochMillis: Long,
    ) {
        require(userId.isNotBlank() && syncedAtEpochMillis >= 0L)
        dao.replacePlaylists(
            userId = userId,
            playlists = playlists.map { it.toEntity(userId) },
            syncedAtEpochMillis = syncedAtEpochMillis,
        )
    }

    override suspend fun upsertPlaylist(userId: String, playlist: PlaylistSummary) {
        require(userId.isNotBlank())
        dao.insertPlaylist(playlist.toEntity(userId))
    }

    override suspend fun appendPlaylists(userId: String, playlists: List<PlaylistSummary>) {
        require(userId.isNotBlank())
        dao.insertPlaylists(playlists.map { it.toEntity(userId) })
    }

    override suspend fun enqueueMutation(
        userId: String,
        playlistId: String,
        operationId: String,
        expectedRevision: Long,
        payload: String,
        queuedAtEpochMillis: Long,
    ) {
        dao.insertPlaylistMutation(
            PlaylistMutationOutboxEntity(
                userId = userId,
                operationId = operationId,
                playlistId = playlistId,
                expectedRevision = expectedRevision,
                mutationPayload = payload,
                queuedAtEpochMillis = queuedAtEpochMillis,
                attemptCount = 0,
            ),
        )
    }

    override suspend fun pendingMutations(userId: String, playlistId: String, limit: Int) =
        dao.getPendingPlaylistMutations(userId, playlistId, limit).map {
            StoredPlaylistMutation(
                operationId = it.operationId,
                expectedRevision = it.expectedRevision,
                payload = it.mutationPayload,
                queuedAtEpochMillis = it.queuedAtEpochMillis,
                attemptCount = it.attemptCount,
            )
        }

    override suspend fun pendingMutationCount(userId: String, playlistId: String) =
        dao.countPendingPlaylistMutations(userId, playlistId)

    override suspend fun acknowledgeMutation(userId: String, operationId: String) {
        dao.acknowledgePlaylistMutation(userId, operationId)
    }

    override suspend fun recordMutationAttempt(userId: String, operationId: String) {
        dao.recordPlaylistMutationAttempt(userId, operationId)
    }
}

private fun PlaylistEntity.toDomain() = PlaylistSummary(
    id = playlistId,
    name = name,
    description = description,
    trackCount = trackCount,
    revision = revision,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    ownerId = ownerId,
    visibility = runCatching { PlaylistVisibility.valueOf(visibility) }.getOrDefault(PlaylistVisibility.Private),
    accessRole = runCatching { PlaylistRole.valueOf(accessRole) }.getOrDefault(PlaylistRole.Owner),
    isFollowing = isFollowing,
)

private fun PlaylistSummary.toEntity(userId: String) = PlaylistEntity(
    userId = userId,
    playlistId = id,
    name = name,
    description = description,
    trackCount = trackCount,
    revision = revision,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    ownerId = ownerId,
    visibility = visibility.name,
    accessRole = accessRole.name,
    isFollowing = isFollowing,
)
