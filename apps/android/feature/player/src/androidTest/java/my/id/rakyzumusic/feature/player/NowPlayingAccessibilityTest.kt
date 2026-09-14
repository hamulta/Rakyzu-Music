package my.id.rakyzumusic.feature.player

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import my.id.rakyzumusic.core.designsystem.theme.RakyzuMusicTheme
import my.id.rakyzumusic.core.model.PlaybackQueueItem
import my.id.rakyzumusic.core.playback.PlaybackError
import my.id.rakyzumusic.core.playback.PlaybackRecoveryState
import my.id.rakyzumusic.core.playback.PlaybackSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NowPlayingAccessibilityTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun largeTextQueuePreservesStateAndFortyEightDpActions() {
        setContent(fontScale = 1.5f)
        val list = composeRule.onNodeWithTag("now-playing")
        list.performScrollToNode(hasContentDescription("Move Song earlier in queue"))

        composeRule.onNode(hasStateDescription("Current track")).assertExists()
        composeRule.onNodeWithContentDescription("Move Song earlier in queue")
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Move Song later in queue")
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Remove Song from queue")
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun retryableFailureExposesAccessibleBoundedAction() {
        setContent(fontScale = 1f)
        val list = composeRule.onNodeWithTag("now-playing")
        list.performScrollToNode(androidx.compose.ui.test.hasText("Retry playback"))

        composeRule.onNodeWithText("Retry playback").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("Playback was interrupted. Your 1-track queue is kept on this device.")
            .assertExists()
    }

    private fun setContent(fontScale: Float) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = fontScale),
            ) {
                RakyzuMusicTheme(darkTheme = true) {
                    NowPlayingScreen(
                        snapshot = SNAPSHOT,
                        onDismiss = {},
                        onTogglePlayPause = {},
                        onPrevious = {},
                        onNext = {},
                        onSeek = {},
                        onQueueItemClick = {},
                    )
                }
            }
        }
    }

    private companion object {
        val SNAPSHOT = PlaybackSnapshot(
            mediaId = "track",
            title = "Song",
            artist = "Artist",
            currentIndex = 0,
            queue = listOf(PlaybackQueueItem("track", "Song", "Artist", null, 2_000L)),
            recovery = PlaybackRecoveryState(canRetry = true, retainedQueueSize = 1),
            error = PlaybackError("IO_NETWORK_CONNECTION_FAILED"),
        )
    }
}
