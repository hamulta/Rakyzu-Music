package my.id.rakyzumusic

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
        if (
            intent?.data?.path == PASSWORD_RECOVERY_PATH &&
            !intent?.data?.getQueryParameter(AUTH_CODE_PARAMETER).isNullOrBlank()
        ) {
            rakyzuApplication.authRepository.markPasswordRecoveryCallback()
        }
        setContent {
            RakyzuMusicTheme(darkTheme = true) {
                RakyzuMusicApp(
                    versionName = BuildConfig.VERSION_NAME,
                    authRepository = rakyzuApplication.authRepository,
                )
            }
        }
    }

    private companion object {
        const val AUTH_CODE_PARAMETER = "code"
        const val PASSWORD_RECOVERY_PATH = "/recovery"
    }
}
