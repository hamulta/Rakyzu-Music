package my.id.rakyzumusic.core.database.catalog

import my.id.rakyzumusic.core.model.PersistedPlaybackQueue
import my.id.rakyzumusic.core.model.PlaybackQueueItem

interface PlaybackQueueLocalDataSource {
    suspend fun read(userId: String): PersistedPlaybackQueue

    suspend fun replace(userId: String, queue: PersistedPlaybackQueue)
}

internal class RoomPlaybackQueueLocalDataSource(
    database: RakyzuDatabase,
) : PlaybackQueueLocalDataSource {
    private val dao = database.catalogDao()

    override suspend fun read(userId: String): PersistedPlaybackQueue {
        val state = dao.getPlaybackQueueState(userId)
        val items = dao.getPlaybackQueueEntries(userId).map(PlaybackQueueEntryEntity::toDomain)
        return PersistedPlaybackQueue(
            items = items,
            currentIndex = state?.currentIndex ?: PersistedPlaybackQueue.NO_ACTIVE_ITEM,
            updatedAtEpochMillis = state?.updatedAtEpochMillis,
        )
    }

    override suspend fun replace(userId: String, queue: PersistedPlaybackQueue) {
        val state = queue.updatedAtEpochMillis?.let { updatedAt ->
            PlaybackQueueStateEntity(
                userId = userId,
                currentIndex = queue.currentIndex,
                updatedAtEpochMillis = updatedAt,
            )
        }
        dao.replacePlaybackQueue(
            userId = userId,
            entries = queue.items.mapIndexed { position, item ->
                item.toEntity(userId, position)
            },
            state = state,
        )
    }
}

private fun PlaybackQueueEntryEntity.toDomain() = PlaybackQueueItem(
    mediaId = mediaId,
    title = title,
    artist = artist,
    albumTitle = albumTitle,
    durationMs = durationMs,
    artistId = artistId,
    albumId = albumId,
)

private fun PlaybackQueueItem.toEntity(userId: String, position: Int) = PlaybackQueueEntryEntity(
    userId = userId,
    position = position,
    mediaId = mediaId,
    title = title,
    artist = artist,
    albumTitle = albumTitle,
    durationMs = durationMs,
    artistId = artistId,
    albumId = albumId,
)
