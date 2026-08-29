package my.id.rakyzumusic.core.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import my.id.rakyzumusic.core.model.Track

class RakyzuPlaybackController(context: Context) {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)
    private val controllerFuture = MediaController.Builder(
        appContext,
        SessionToken(appContext, ComponentName(appContext, RakyzuPlaybackService::class.java)),
    ).buildAsync()

    private val mutableSnapshot = MutableStateFlow(PlaybackSnapshot(status = PlaybackStatus.Connecting))
    val snapshot: StateFlow<PlaybackSnapshot> = mutableSnapshot.asStateFlow()

    init {
        controllerFuture.addListener(
            {
                runCatching { controllerFuture.get() }
                    .onSuccess { controller ->
                        controller.addListener(ControllerListener())
                        publishSnapshot(controller)
                    }
                    .onFailure {
                        mutableSnapshot.value = PlaybackSnapshot(
                            error = PlaybackError("CONTROLLER_CONNECTION_FAILED"),
                        )
                    }
            },
            mainExecutor,
        )
    }

    fun play(track: Track) {
        mutableSnapshot.value = PlaybackSnapshot(
            mediaId = track.id,
            title = track.title,
            artist = track.artist,
            status = PlaybackStatus.Buffering,
        )
        withController { controller ->
            controller.setMediaItem(track.toPlaybackMediaItem())
            controller.prepare()
            controller.play()
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

    fun stopAndClear() {
        withController { controller ->
            controller.stop()
            controller.clearMediaItems()
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

    private fun publishSnapshot(player: Player, error: PlaybackException? = null) {
        val mediaItem = player.currentMediaItem
        val metadata = mediaItem?.mediaMetadata
        val status = when {
            mediaItem == null -> PlaybackStatus.Idle
            player.playbackState == Player.STATE_BUFFERING -> PlaybackStatus.Buffering
            player.playbackState == Player.STATE_ENDED -> PlaybackStatus.Ended
            player.isPlaying -> PlaybackStatus.Playing
            else -> PlaybackStatus.Paused
        }
        mutableSnapshot.value = PlaybackSnapshot(
            mediaId = mediaItem?.mediaId,
            title = metadata?.title?.toString(),
            artist = metadata?.artist?.toString(),
            status = status,
            error = error?.let { PlaybackError(it.errorCodeName) },
        )
    }

    private inner class ControllerListener : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publishSnapshot(player)
        }

        override fun onPlayerError(error: PlaybackException) {
            controllerFuture.get().let { publishSnapshot(it, error) }
        }
    }
}
