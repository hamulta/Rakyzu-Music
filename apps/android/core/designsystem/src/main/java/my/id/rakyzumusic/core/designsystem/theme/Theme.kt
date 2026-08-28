package my.id.rakyzumusic.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = RakyzuAqua,
    onPrimary = RakyzuBlack,
    secondary = RakyzuPurple,
    onSecondary = RakyzuBlack,
    background = RakyzuBlack,
    onBackground = RakyzuOnSurface,
    surface = RakyzuSurface,
    onSurface = RakyzuOnSurface,
    surfaceVariant = RakyzuSurfaceRaised,
    onSurfaceVariant = RakyzuOnSurfaceMuted,
    error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006A62),
    onPrimary = Color.White,
    secondary = Color(0xFF5F42B3),
    onSecondary = Color.White,
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1C1B20),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1C1B20),
)

@Composable
fun RakyzuMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = RakyzuTypography,
        content = content,
    )
}
