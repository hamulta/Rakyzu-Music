package my.id.rakyzumusic.core.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

@androidx.annotation.OptIn(UnstableApi::class)
class RakyzuPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val dependencies = applicationContext as? PlaybackDependencies
            ?: error("Rakyzu Music playback dependencies are unavailable")
        val requestResolver = AuthenticatedPlaybackRequestResolver(
            dependencies.playbackStreamRequestProvider,
        )
        val resolvingDataSourceFactory = ResolvingDataSource.Factory(
            DefaultHttpDataSource.Factory().setUserAgent(USER_AGENT),
        ) { dataSpec ->
            val request = requestResolver.resolve(dataSpec.uri.toString())
            dataSpec
                .withUri(request.url.toUri())
                .withRequestHeaders(request.headers)
        }
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(resolvingDataSourceFactory),
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(createSessionActivity())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        val isOwnApp = controllerInfo.packageName == packageName
        return mediaSession.takeIf { isOwnApp || controllerInfo.isTrusted }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun createSessionActivity(): PendingIntent {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent().setPackage(packageName)
        return PendingIntent.getActivity(
            this,
            SESSION_ACTIVITY_REQUEST_CODE,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val SESSION_ACTIVITY_REQUEST_CODE = 800
        const val USER_AGENT = "Rakyzu Music Android/0.1.10"
    }
}
