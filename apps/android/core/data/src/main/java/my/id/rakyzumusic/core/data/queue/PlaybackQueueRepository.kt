package my.id.rakyzumusic.core.data.queue

import my.id.rakyzumusic.core.database.catalog.PlaybackQueueLocalDataSource
import my.id.rakyzumusic.core.model.PersistedPlaybackQueue
import my.id.rakyzumusic.core.model.PlaybackQueueItem

interface PlaybackQueueRepository {
    suspend fun read(userId: String): PersistedPlaybackQueue

    suspend fun replace(
        userId: String,
        items: List<PlaybackQueueItem>,
        currentIndex: Int,
    ): Boolean
}

class OfflineFirstPlaybackQueueRepository(
    private val localDataSource: PlaybackQueueLocalDataSource,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : PlaybackQueueRepository {
    override suspend fun read(userId: String): PersistedPlaybackQueue =
        if (validUserId(userId)) localDataSource.read(userId) else PersistedPlaybackQueue()

    override suspend fun replace(
        userId: String,
        items: List<PlaybackQueueItem>,
        currentIndex: Int,
    ): Boolean {
        if (!validQueue(userId, items, currentIndex)) return false
        localDataSource.replace(
            userId = userId,
            queue = PersistedPlaybackQueue(
                items = items.toList(),
                currentIndex = currentIndex,
                updatedAtEpochMillis = currentTimeMillis().coerceAtLeast(0L),
            ),
        )
        return true
    }

    private fun validQueue(
        userId: String,
        items: List<PlaybackQueueItem>,
        currentIndex: Int,
    ): Boolean = validUserId(userId) &&
        items.size <= MAXIMUM_QUEUE_SIZE &&
        validCurrentIndex(items, currentIndex) &&
        items.all(::validItem)

    private fun validCurrentIndex(items: List<PlaybackQueueItem>, currentIndex: Int): Boolean =
        if (items.isEmpty()) {
            currentIndex == PersistedPlaybackQueue.NO_ACTIVE_ITEM
        } else {
            currentIndex in items.indices
        }

    private fun validUserId(userId: String): Boolean =
        userId.isNotBlank() && userId.length <= MAXIMUM_IDENTIFIER_LENGTH && userId.none(Char::isISOControl)

    private fun validItem(item: PlaybackQueueItem): Boolean =
        item.mediaId.isValidText(MAXIMUM_IDENTIFIER_LENGTH) &&
            item.title.isValidText(MAXIMUM_LABEL_LENGTH) &&
            item.artist.isValidText(MAXIMUM_LABEL_LENGTH) &&
            item.albumTitle.isNullOrValidText(MAXIMUM_LABEL_LENGTH) &&
            item.artistId.isValidOptionalIdentifier() &&
            item.albumId.isValidOptionalIdentifier() &&
            item.durationMs in 0L..MAXIMUM_DURATION_MILLIS

    private fun String.isValidText(maximumLength: Int): Boolean =
        isNotBlank() && length <= maximumLength && none(Char::isISOControl)

    private fun String?.isNullOrValidText(maximumLength: Int): Boolean =
        this == null || isValidText(maximumLength)

    private fun String.isValidOptionalIdentifier(): Boolean =
        isEmpty() || isValidText(MAXIMUM_IDENTIFIER_LENGTH)

    private companion object {
        const val MAXIMUM_QUEUE_SIZE = 1_000
        const val MAXIMUM_IDENTIFIER_LENGTH = 256
        const val MAXIMUM_LABEL_LENGTH = 512
        const val MAXIMUM_DURATION_MILLIS = 24L * 60L * 60L * 1_000L
    }
}
