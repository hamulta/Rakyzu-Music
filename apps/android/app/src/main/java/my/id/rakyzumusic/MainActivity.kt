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
        setContent {
            RakyzuMusicTheme(darkTheme = true) {
                RakyzuMusicApp(versionName = BuildConfig.VERSION_NAME)
            }
        }
    }
}
