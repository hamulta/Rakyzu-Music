package my.id.rakyzumusic.core.database.catalog

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import my.id.rakyzumusic.core.model.PlaylistSnapshot
import my.id.rakyzumusic.core.model.PlaylistSummary

interface PlaylistLocalDataSource {
    fun observePlaylists(userId: String): Flow<PlaylistSnapshot>

    suspend fun replacePlaylists(
        userId: String,
        playlists: List<PlaylistSummary>,
        syncedAtEpochMillis: Long,
    )

    suspend fun upsertPlaylist(userId: String, playlist: PlaylistSummary)
}

internal class RoomPlaylistLocalDataSource(
    private val database: RakyzuDatabase,
) : PlaylistLocalDataSource {
    private val dao = database.catalogDao()

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
}

private fun PlaylistEntity.toDomain() = PlaylistSummary(
    id = playlistId,
    name = name,
    description = description,
    trackCount = trackCount,
    revision = revision,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
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
)
