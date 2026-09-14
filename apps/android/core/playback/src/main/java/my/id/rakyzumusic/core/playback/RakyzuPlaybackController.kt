package my.id.rakyzumusic.core.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import my.id.rakyzumusic.core.model.PlaybackQueueItem
import my.id.rakyzumusic.core.model.PersistedPlaybackQueue
import my.id.rakyzumusic.core.model.Track

class RakyzuPlaybackController(
    context: Context,
    private val onMediaItemTransition: (String) -> Unit = {},
    private val onQueueStateChanged: (PlaybackSnapshot) -> Unit = {},
    private val diagnosticSink: PlaybackDiagnosticSink = NoOpPlaybackDiagnosticSink,
) {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controllerFuture = MediaController.Builder(
        appContext,
        SessionToken(appContext, ComponentName(appContext, RakyzuPlaybackService::class.java)),
    ).buildAsync()

    private val mutableSnapshot = MutableStateFlow(PlaybackSnapshot(status = PlaybackStatus.Connecting))
    val snapshot: StateFlow<PlaybackSnapshot> = mutableSnapshot.asStateFlow()
    private var progressSampler: PlaybackProgressSampler? = null
    private var playerError: PlaybackException? = null
    private var playbackRecovery = PlaybackRecoveryState()

    init {
        controllerFuture.addListener(
            {
                runCatching { controllerFuture.get() }
                    .onSuccess { controller ->
                        controller.addListener(ControllerListener())
                        publishSnapshot(controller, timelineChanged = true)
                        synchronizeProgressUpdates(controller)
                    }
                    .onFailure {
                        progressSampler?.stop()
                        mutableSnapshot.value = PlaybackSnapshot(
                            error = PlaybackError("CONTROLLER_CONNECTION_FAILED"),
                        )
                    }
            },
            mainExecutor,
        )
    }

    fun play(track: Track) = playQueue(listOf(track), startIndex = 0)

    fun playQueue(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty() || tracks.size > MAXIMUM_QUEUE_SIZE || startIndex !in tracks.indices) {
            mutableSnapshot.value = mutableSnapshot.value.copy(
                error = PlaybackError("INVALID_QUEUE"),
            )
            return
        }
        playerError = null
        playbackRecovery = PlaybackRecoveryState()
        val selectedTrack = tracks[startIndex]
        val pendingSnapshot = PlaybackSnapshot(
            mediaId = selectedTrack.id,
            title = selectedTrack.title,
            artist = selectedTrack.artist,
            albumTitle = selectedTrack.albumTitle.takeIf(String::isNotBlank),
            status = PlaybackStatus.Buffering,
            currentIndex = startIndex,
            queue = tracks.map(Track::toPlaybackQueueItem),
            canSkipPrevious = true,
            canSkipNext = startIndex < tracks.lastIndex,
        )
        mutableSnapshot.value = pendingSnapshot
        diagnosticSink.record(
            PlaybackDiagnosticEvent.QueueStarted(PlaybackQueueSize.from(tracks.size)),
        )
        withController { controller ->
            controller.setMediaItems(
                tracks.map(Track::toPlaybackMediaItem),
                startIndex,
                0L,
            )
            controller.prepare()
            controller.play()
        }
    }

    fun playNext(track: Track) {
        withController { controller ->
            val existingIndex = controller.indexOfMediaId(track.id)
            if (existingIndex >= 0) {
                playNextMoveDestination(
                    currentIndex = controller.currentMediaItemIndex,
                    itemCount = controller.mediaItemCount,
                    existingIndex = existingIndex,
                )?.let { controller.moveMediaItem(existingIndex, it) }
            } else if (controller.mediaItemCount < MAXIMUM_QUEUE_SIZE) {
                controller.addMediaItem(
                    nextQueueInsertionIndex(controller.currentMediaItemIndex, controller.mediaItemCount),
                    track.toPlaybackMediaItem(),
                )
            }
        }
    }

    fun addToQueue(track: Track) {
        withController { controller ->
            if (
                controller.mediaItemCount < MAXIMUM_QUEUE_SIZE &&
                controller.indexOfMediaId(track.id) < 0
            ) {
                controller.addMediaItem(track.toPlaybackMediaItem())
            }
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        withController { controller ->
            if (validQueueMove(fromIndex, toIndex, controller.mediaItemCount)) {
                controller.moveMediaItem(fromIndex, toIndex)
            }
        }
    }

    fun removeQueueItem(index: Int) {
        withController { controller ->
            if (index in 0 until controller.mediaItemCount) controller.removeMediaItem(index)
        }
    }

    fun togglePlayPause() {
        withController { controller ->
            if (controller.isPlaying || controller.playWhenReady) {
                controller.pause()
            } else if (controller.currentMediaItem != null) {
                controller.play()
            }
        }
    }

    fun skipToPrevious() {
        withController { controller ->
            if (controller.currentMediaItem != null) {
                controller.seekToPrevious()
            }
        }
    }

    fun skipToNext() {
        withController { controller ->
            if (controller.hasNextMediaItem()) {
                controller.seekToNext()
            }
        }
    }

    fun skipToQueueItem(index: Int) {
        withController { controller ->
            if (index in 0 until controller.mediaItemCount) {
                controller.seekToDefaultPosition(index)
                controller.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        withController { controller ->
            val duration = controller.duration.takeUnless { it == C.TIME_UNSET }
                ?.coerceAtLeast(0L)
            controller.seekTo(
                if (duration == null) positionMs.coerceAtLeast(0L)
                else positionMs.coerceIn(0L, duration),
            )
        }
    }

    /** Retries only a retained, retryable queue and never inserts unrelated media. */
    fun retryPlayback() {
        withController { controller ->
            val currentRecovery = playbackRecovery
            if (
                !currentRecovery.canRetry ||
                currentRecovery.attemptCount >= MAXIMUM_RECOVERY_ATTEMPTS
            ) {
                return@withController
            }
            if (controller.currentMediaItem == null || controller.mediaItemCount == 0) {
                playbackRecovery = PlaybackRecoveryState()
                mutableSnapshot.value = mutableSnapshot.value.copy(recovery = playbackRecovery)
                return@withController
            }
            val attempt = currentRecovery.attemptCount + 1
            playbackRecovery = currentRecovery.copy(
                canRetry = attempt < MAXIMUM_RECOVERY_ATTEMPTS,
                attemptCount = attempt,
                retainedQueueSize = controller.mediaItemCount,
            )
            playerError = null
            mutableSnapshot.value = mutableSnapshot.value.copy(
                status = PlaybackStatus.Buffering,
                recovery = playbackRecovery,
                error = null,
            )
            diagnosticSink.record(
                PlaybackDiagnosticEvent.RecoveryAttempted(
                    attempt = attempt,
                    queueSize = PlaybackQueueSize.from(controller.mediaItemCount),
                ),
            )
            controller.prepare()
            controller.play()
        }
    }

    fun stopAndClear() {
        progressSampler?.stop()
        withController { controller ->
            controller.stop()
            controller.clearMediaItems()
            playerError = null
            playbackRecovery = PlaybackRecoveryState()
            mutableSnapshot.value = PlaybackSnapshot()
        }
    }

    /** An explicit listener action; Rakyzu never clears an account queue on its own. */
    fun clearQueue() = stopAndClear()

    /** Restores an account-scoped Room queue without resuming playback after process death. */
    fun restoreQueue(queue: PersistedPlaybackQueue) {
        val restoration = sanitizeQueueForRestore(queue)
        if (restoration.items.isEmpty()) return
        playerError = null
        playbackRecovery = PlaybackRecoveryState()
        val selected = restoration.items[restoration.currentIndex]
        mutableSnapshot.value = PlaybackSnapshot(
            mediaId = selected.mediaId,
            title = selected.title,
            artist = selected.artist,
            albumTitle = selected.albumTitle,
            status = PlaybackStatus.Paused,
            currentIndex = restoration.currentIndex,
            queue = restoration.items,
            canSkipPrevious = restoration.currentIndex > 0,
            canSkipNext = restoration.currentIndex < restoration.items.lastIndex,
        )
        withController { controller ->
            // Do not overwrite a queue that a listener has already started in this process.
            if (controller.mediaItemCount != 0) return@withController
            controller.setMediaItems(
                restoration.items.map(PlaybackQueueItem::toPlaybackMediaItem),
                restoration.currentIndex,
                0L,
            )
            controller.prepare()
            controller.pause()
        }
    }

    private fun withController(action: (MediaController) -> Unit) {
        controllerFuture.addListener(
            {
                runCatching { controllerFuture.get() }
                    .onSuccess(action)
                    .onFailure {
                        mutableSnapshot.value = PlaybackSnapshot(
                            error = PlaybackError("CONTROLLER_CONNECTION_FAILED"),
                        )
                    }
            },
            mainExecutor,
        )
    }

    private fun MediaController.indexOfMediaId(mediaId: String): Int =
        (0 until mediaItemCount).firstOrNull { getMediaItemAt(it).mediaId == mediaId } ?: -1

    private fun publishSnapshot(
        player: Player,
        error: PlaybackException? = null,
        timelineChanged: Boolean = false,
    ) {
        val previousSnapshot = mutableSnapshot.value
        val mediaItem = player.currentMediaItem
        val metadata = mediaItem?.mediaMetadata
        val durationMs = player.duration
            .takeUnless { it == C.TIME_UNSET }
            ?.coerceAtLeast(0L)
            ?: metadata?.durationMs?.coerceAtLeast(0L)
            ?: 0L
        val positionMs = player.currentPosition.coerceAtLeast(0L)
        val queue = resolvePlaybackQueue(
            cachedQueue = mutableSnapshot.value.queue,
            mediaItemCount = player.mediaItemCount,
            timelineChanged = timelineChanged,
        ) {
            (0 until player.mediaItemCount).map { index ->
                player.getMediaItemAt(index).toPlaybackQueueItem()
            }
        }
        val status = when {
            mediaItem == null -> PlaybackStatus.Idle
            player.playbackState == Player.STATE_BUFFERING -> PlaybackStatus.Buffering
            player.playbackState == Player.STATE_ENDED -> PlaybackStatus.Ended
            player.isPlaying -> PlaybackStatus.Playing
            else -> PlaybackStatus.Paused
        }
        if (
            playerError == null &&
            (player.isPlaying || player.playbackState == Player.STATE_READY)
        ) {
            playbackRecovery = PlaybackRecoveryState()
        }
        val snapshot = PlaybackSnapshot(
            mediaId = mediaItem?.mediaId,
            title = metadata?.title?.toString(),
            artist = metadata?.artist?.toString(),
            albumTitle = metadata?.albumTitle?.toString(),
            status = status,
            positionMs = positionMs,
            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(positionMs),
            durationMs = durationMs,
            currentIndex = player.currentMediaItemIndex.takeIf { mediaItem != null } ?: -1,
            queue = queue,
            canSkipPrevious = mediaItem != null,
            canSkipNext = player.hasNextMediaItem(),
            recovery = playbackRecovery,
            error = (error ?: playerError)?.let { PlaybackError(it.errorCodeName) },
        )
        mutableSnapshot.value = snapshot
        if (shouldPersistQueueState(previousSnapshot, snapshot, timelineChanged)) {
            onQueueStateChanged(snapshot)
        }
    }

    private fun synchronizeProgressUpdates(controller: MediaController) {
        val sampler = progressSampler ?: PlaybackProgressSampler(
            scope = controllerScope,
            intervalMs = PROGRESS_UPDATE_INTERVAL_MS,
            isPlaybackActive = controller::isPlaying,
            sample = { publishSnapshot(controller) },
        ).also {
            progressSampler = it
        }
        sampler.synchronize()
    }

    private inner class ControllerListener : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.mediaId
                ?.takeIf(String::isNotBlank)
                ?.let(onMediaItemTransition)
        }

        override fun onEvents(player: Player, events: Player.Events) {
            publishSnapshot(
                player = player,
                timelineChanged = events.contains(Player.EVENT_TIMELINE_CHANGED),
            )
            (player as? MediaController)?.let(::synchronizeProgressUpdates)
        }

        override fun onPlayerErrorChanged(error: PlaybackException?) {
            playerError = error
            if (error != null) {
                val httpResponseCode = error.httpResponseCode()
                val retryable = isRetryablePlaybackError(error.errorCode, httpResponseCode)
                playbackRecovery = PlaybackRecoveryState(
                    canRetry = retryable &&
                        playbackRecovery.attemptCount < MAXIMUM_RECOVERY_ATTEMPTS,
                    attemptCount = playbackRecovery.attemptCount,
                    retainedQueueSize = mutableSnapshot.value.queue.size,
                )
                diagnosticSink.record(
                    PlaybackDiagnosticEvent.PlaybackFailed(
                        failure = playbackFailureKind(error.errorCode, httpResponseCode),
                        queueSize = PlaybackQueueSize.from(mutableSnapshot.value.queue.size),
                        retryable = playbackRecovery.canRetry,
                    ),
                )
            }
            controllerFuture.get().let {
                publishSnapshot(it, error)
                synchronizeProgressUpdates(it)
            }
        }
    }

    private companion object {
        const val PROGRESS_UPDATE_INTERVAL_MS = 500L
        const val MAXIMUM_QUEUE_SIZE = 1_000
        const val MAXIMUM_RECOVERY_ATTEMPTS = 3
    }
}

internal fun isRetryablePlaybackError(errorCode: Int, httpResponseCode: Int? = null): Boolean =
    when (errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        -> true
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            httpResponseCode == 408 || httpResponseCode == 429 || httpResponseCode in 500..599
        else -> false
    }

internal fun playbackFailureKind(
    errorCode: Int,
    httpResponseCode: Int? = null,
): PlaybackFailureKind = when {
    isRetryablePlaybackError(errorCode, httpResponseCode) -> PlaybackFailureKind.Network
    errorCode in PlaybackException.ERROR_CODE_IO_UNSPECIFIED..
        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> PlaybackFailureKind.Source
    else -> PlaybackFailureKind.Unexpected
}

private tailrec fun Throwable?.httpResponseCode(): Int? = when (this) {
    null -> null
    is HttpDataSource.InvalidResponseCodeException -> responseCode
    else -> cause.httpResponseCode()
}

internal fun nextQueueInsertionIndex(currentIndex: Int, itemCount: Int): Int =
    if (currentIndex in 0 until itemCount) currentIndex + 1 else itemCount

internal fun validQueueMove(fromIndex: Int, toIndex: Int, itemCount: Int): Boolean =
    fromIndex in 0 until itemCount && toIndex in 0 until itemCount && fromIndex != toIndex

/**
 * A duplicate requested as "play next" is moved immediately after the active item instead of
 * being inserted twice. A request for the active item itself is already satisfied.
 */
internal fun playNextMoveDestination(
    currentIndex: Int,
    itemCount: Int,
    existingIndex: Int,
): Int? {
    if (currentIndex !in 0 until itemCount || existingIndex !in 0 until itemCount) return null
    if (existingIndex == currentIndex || existingIndex == currentIndex + 1) return null
    return if (existingIndex < currentIndex) currentIndex else currentIndex + 1
}

internal data class QueueRestorePlan(
    val items: List<PlaybackQueueItem>,
    val currentIndex: Int,
    val droppedItemCount: Int,
)

/** Drops malformed or duplicate legacy entries before they reach Media3 after process death. */
internal fun sanitizeQueueForRestore(queue: PersistedPlaybackQueue): QueueRestorePlan {
    val activeMediaId = queue.items.getOrNull(queue.currentIndex)?.mediaId
    val knownMediaIds = mutableSetOf<String>()
    val items = queue.items.filter { item ->
        item.isRestorableQueueItem() && knownMediaIds.add(item.mediaId)
    }
    val restoredIndex = activeMediaId
        ?.let { mediaId -> items.indexOfFirst { it.mediaId == mediaId } }
        ?.takeIf { it >= 0 }
        ?: items.indices.firstOrNull()
        ?: PersistedPlaybackQueue.NO_ACTIVE_ITEM
    return QueueRestorePlan(
        items = items,
        currentIndex = restoredIndex,
        droppedItemCount = queue.items.size - items.size,
    )
}

private fun PlaybackQueueItem.isRestorableQueueItem(): Boolean =
    mediaId.isNotBlank() && mediaId.length <= MAXIMUM_QUEUE_IDENTIFIER_LENGTH &&
        title.isNotBlank() && title.length <= MAXIMUM_QUEUE_LABEL_LENGTH &&
        artist.isNotBlank() && artist.length <= MAXIMUM_QUEUE_LABEL_LENGTH &&
        albumTitle.orEmpty().length <= MAXIMUM_QUEUE_LABEL_LENGTH &&
        durationMs in 0L..MAXIMUM_QUEUE_DURATION_MILLIS &&
        listOf(mediaId, title, artist, albumTitle.orEmpty(), artistId, albumId).none {
            it.any(Char::isISOControl)
        }

private fun PlaybackQueueItem.toPlaybackMediaItem(): MediaItem = Track(
    id = mediaId,
    title = title,
    artist = artist,
    durationMs = durationMs,
    artistId = artistId,
    albumId = albumId,
    albumTitle = albumTitle.orEmpty(),
).toPlaybackMediaItem()

private const val MAXIMUM_QUEUE_IDENTIFIER_LENGTH = 256
private const val MAXIMUM_QUEUE_LABEL_LENGTH = 512
private const val MAXIMUM_QUEUE_DURATION_MILLIS = 24L * 60L * 60L * 1_000L

internal fun shouldPersistQueueState(
    previous: PlaybackSnapshot,
    current: PlaybackSnapshot,
    timelineChanged: Boolean,
): Boolean {
    val isInitialEmptyControllerSnapshot =
        previous.status == PlaybackStatus.Connecting && current.queue.isEmpty()
    return !isInitialEmptyControllerSnapshot &&
        (timelineChanged || previous.currentIndex != current.currentIndex)
}

internal fun Track.toPlaybackQueueItem(): PlaybackQueueItem = PlaybackQueueItem(
    mediaId = id,
    title = title,
    artist = artist,
    albumTitle = albumTitle.takeIf(String::isNotBlank),
    durationMs = durationMs.coerceAtLeast(0L),
    artistId = artistId,
    albumId = albumId,
)

private fun androidx.media3.common.MediaItem.toPlaybackQueueItem(): PlaybackQueueItem =
    PlaybackQueueItem(
        mediaId = mediaId,
        title = mediaMetadata.title?.toString() ?: "Rakyzu Music",
        artist = mediaMetadata.artist?.toString() ?: "Rakyzu Music",
        albumTitle = mediaMetadata.albumTitle?.toString(),
        durationMs = mediaMetadata.durationMs?.coerceAtLeast(0L) ?: 0L,
        artistId = mediaMetadata.extras?.getString(PLAYBACK_ARTIST_ID_KEY).orEmpty(),
        albumId = mediaMetadata.extras?.getString(PLAYBACK_ALBUM_ID_KEY).orEmpty(),
    )
