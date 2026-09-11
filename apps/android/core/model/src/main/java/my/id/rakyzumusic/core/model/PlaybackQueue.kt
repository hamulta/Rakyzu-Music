package my.id.rakyzumusic.core.model

data class PlaybackQueueItem(
    val mediaId: String,
    val title: String,
    val artist: String,
    val albumTitle: String?,
    val durationMs: Long,
    val artistId: String = "",
    val albumId: String = "",
)

data class PersistedPlaybackQueue(
    val items: List<PlaybackQueueItem> = emptyList(),
    val currentIndex: Int = NO_ACTIVE_ITEM,
    val updatedAtEpochMillis: Long? = null,
) {
    val isEmpty: Boolean
        get() = items.isEmpty()

    companion object {
        const val NO_ACTIVE_ITEM = -1
    }
}
