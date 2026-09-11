package my.id.rakyzumusic.core.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import my.id.rakyzumusic.core.model.PlaybackQueueItem
import my.id.rakyzumusic.core.model.Track

class RakyzuPlaybackController(
    context: Context,
    private val onMediaItemTransition: (String) -> Unit = {},
    private val onQueueStateChanged: (PlaybackSnapshot) -> Unit = {},
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
            if (controller.mediaItemCount >= MAXIMUM_QUEUE_SIZE) return@withController
            controller.addMediaItem(
                nextQueueInsertionIndex(controller.currentMediaItemIndex, controller.mediaItemCount),
                track.toPlaybackMediaItem(),
            )
        }
    }

    fun addToQueue(track: Track) {
        withController { controller ->
            if (controller.mediaItemCount < MAXIMUM_QUEUE_SIZE) {
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

    fun stopAndClear() {
        progressSampler?.stop()
        withController { controller ->
            controller.stop()
            controller.clearMediaItems()
            playerError = null
            mutableSnapshot.value = PlaybackSnapshot()
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
            controllerFuture.get().let {
                publishSnapshot(it, error)
                synchronizeProgressUpdates(it)
            }
        }
    }

    private companion object {
        const val PROGRESS_UPDATE_INTERVAL_MS = 500L
        const val MAXIMUM_QUEUE_SIZE = 1_000
    }
}

internal fun nextQueueInsertionIndex(currentIndex: Int, itemCount: Int): Int =
    if (currentIndex in 0 until itemCount) currentIndex + 1 else itemCount

internal fun validQueueMove(fromIndex: Int, toIndex: Int, itemCount: Int): Boolean =
    fromIndex in 0 until itemCount && toIndex in 0 until itemCount && fromIndex != toIndex

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
