package my.id.rakyzumusic

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import my.id.rakyzumusic.core.designsystem.theme.RakyzuMusicTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val rakyzuApplication = application as RakyzuMusicApplication
        handleAuthCallback(intent, rakyzuApplication)
        setContent {
            RakyzuMusicTheme(darkTheme = true) {
                RakyzuMusicApp(
                    versionName = BuildConfig.VERSION_NAME,
                    authRepository = rakyzuApplication.authRepository,
                    profileRepository = rakyzuApplication.profileRepository,
                    catalogRepository = rakyzuApplication.catalogRepository,
                    mediaDeliveryRepository = rakyzuApplication.mediaDeliveryRepository,
                    connectivityMonitor = rakyzuApplication.connectivityMonitor,
                    recentSearchRepository = rakyzuApplication.recentSearchRepository,
                    playbackController = rakyzuApplication.playbackController,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthCallback(intent, application as RakyzuMusicApplication)
    }

    private fun handleAuthCallback(
        intent: Intent?,
        application: RakyzuMusicApplication,
    ) {
        if (isPasswordRecoveryCallback(intent?.dataString)) {
            application.authRepository.markPasswordRecoveryCallback()
        }
    }
}
