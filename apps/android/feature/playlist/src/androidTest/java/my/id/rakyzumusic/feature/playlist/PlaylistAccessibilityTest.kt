package my.id.rakyzumusic.feature.playlist

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import my.id.rakyzumusic.core.designsystem.theme.RakyzuMusicTheme
import my.id.rakyzumusic.core.model.PlaylistSummary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyAndFailureStatesStayExplicitAndActionable() {
        setPlaylistContent(
            PlaylistUiState(
                hasObservedPlaylists = true,
                message = "Playlists are temporarily unavailable. Try again.",
                messageIsError = true,
            ),
        )

        composeRule.onNodeWithText("No playlists yet. Create your first one above.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Playlists are temporarily unavailable. Try again.")
            .assertIsDisplayed()
        composeRule.onNode(hasPoliteLiveRegion()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Refresh playlists")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun createActionUsesBoundedDraftCallbacks() {
        var creates = 0
        setPlaylistContent(
            PlaylistUiState(name = "Road Trip"),
            onCreate = { creates += 1 },
        )

        composeRule.onNodeWithText("Create playlist")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.runOnIdle { assertEquals(1, creates) }
    }

    @Test
    fun cachedCollectionRemainsVisibleWithTrackCount() {
        setPlaylistContent(
            PlaylistUiState(
                hasObservedPlaylists = true,
                playlists = listOf(
                    PlaylistSummary(
                        id = "90000000-0000-4000-8000-000000000001",
                        name = "Road Trip",
                        description = "Coast",
                        trackCount = 12,
                        revision = 1,
                        createdAtEpochMillis = 1_000L,
                        updatedAtEpochMillis = 1_000L,
                    ),
                ),
            ),
        )

        composeRule.onNodeWithText("Road Trip").assertIsDisplayed()
        composeRule.onNodeWithText("12 songs").assertIsDisplayed()
    }

    private fun setPlaylistContent(
        state: PlaylistUiState,
        onCreate: () -> Unit = {},
    ) {
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                PlaylistScreen(
                    state = state,
                    onNameChange = {},
                    onDescriptionChange = {},
                    onCreate = onCreate,
                    onRefresh = {},
                )
            }
        }
    }

    private fun hasPoliteLiveRegion() = SemanticsMatcher.expectValue(
        SemanticsProperties.LiveRegion,
        LiveRegionMode.Polite,
    )
}
