package my.id.rakyzumusic

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import my.id.rakyzumusic.core.data.auth.parseAuthCallback
import my.id.rakyzumusic.core.designsystem.theme.RakyzuMusicTheme
import my.id.rakyzumusic.core.model.parseRakyzuTrackShareUri
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val pendingTrackLink = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val rakyzuApplication = application as RakyzuMusicApplication
        handleAuthCallback(intent, rakyzuApplication)
        handleTrackLink(intent)
        setContent {
            RakyzuMusicTheme(darkTheme = true) {
                RakyzuMusicApp(
                    versionName = BuildConfig.VERSION_NAME,
                    authRepository = rakyzuApplication.authRepository,
                    profileRepository = rakyzuApplication.profileRepository,
                    catalogRepository = rakyzuApplication.catalogRepository,
                    trackContextRepository = rakyzuApplication.trackContextRepository,
                    libraryRepository = rakyzuApplication.libraryRepository,
                    playlistRepository = rakyzuApplication.playlistRepository,
                    mediaDeliveryRepository = rakyzuApplication.mediaDeliveryRepository,
                    connectivityMonitor = rakyzuApplication.connectivityMonitor,
                    recentSearchRepository = rakyzuApplication.recentSearchRepository,
                    playbackControllerProvider = { rakyzuApplication.playbackController },
                    onSessionEnded = rakyzuApplication::stopPlaybackIfRunning,
                    playbackPreferences = rakyzuApplication.playbackPreferences,
                    adminRepository = rakyzuApplication.adminRepository,
                    artistWorkspaceRepository = rakyzuApplication.artistWorkspaceRepository,
                    offlineDownloadRepository = rakyzuApplication.executableOfflineDownloadRepository,
                    pendingTrackLink = pendingTrackLink,
                    onTrackLinkConsumed = { consumed ->
                        pendingTrackLink.compareAndSet(consumed, null)
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthCallback(intent, application as RakyzuMusicApplication)
        handleTrackLink(intent)
    }

    private fun handleAuthCallback(
        intent: Intent?,
        application: RakyzuMusicApplication,
    ) {
        val callback = parseAuthCallback(intent?.dataString) ?: return
        intent?.data = null
        application.authRepository.handleAuthCallback(callback)
    }

    private fun handleTrackLink(intent: Intent?) {
        val trackId = parseRakyzuTrackShareUri(intent?.dataString) ?: return
        pendingTrackLink.value = trackId
        intent?.data = null
    }
}
