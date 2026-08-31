package my.id.rakyzumusic.feature.home

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import my.id.rakyzumusic.core.designsystem.theme.RakyzuMusicTheme
import my.id.rakyzumusic.core.model.CatalogSnapshot
import my.id.rakyzumusic.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeStateAccessibilityRegressionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun initialLoadingIsPoliteAndSuppressesInactiveActions() {
        setHomeContent(HomeUiState())

        composeRule.onNodeWithText("Loading your Home feed").assertIsDisplayed()
        composeRule.onNode(hasPoliteLiveRegion()).assertExists()
        composeRule.onAllNodesWithText("Retry").assertCountEquals(0)
        composeRule.onAllNodesWithText("Refresh").assertCountEquals(0)
        composeRule.onAllNodesWithText("Music").assertCountEquals(0)
    }

    @Test
    fun successfulEmptyStateIsAHeadingWithOneAccessibleRefreshAction() {
        var refreshCalls = 0
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                HomeScreen(
                    versionName = "test",
                    state = HomeUiState(isRefreshing = false),
                    onRetryCatalog = { refreshCalls += 1 },
                )
            }
        }

        composeRule.onNodeWithText("Your Home feed is empty")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule.onNode(hasPoliteLiveRegion()).assertExists()
        composeRule.onNodeWithText("Refresh")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onAllNodesWithText("Retry").assertCountEquals(0)
        composeRule.onAllNodesWithText("Music").assertCountEquals(0)
        composeRule.runOnIdle { assertEquals(1, refreshCalls) }
    }

    @Test
    fun cachedCatalogRefreshAnnouncesUpdateWithoutExposingRetry() {
        val track = Track(
            id = "track-1",
            title = "Midnight Signal",
            artist = "Rakyzu Sessions",
            durationMs = 180_000L,
        )
        setHomeContent(
            HomeUiState(
                catalog = CatalogSnapshot(
                    artists = emptyList(),
                    albums = emptyList(),
                    tracks = listOf(track),
                    lastSyncedAtEpochMillis = 42L,
                ),
                isRefreshing = true,
            ),
        )

        composeRule.onNodeWithTag("home-list")
            .performScrollToNode(hasText("Updating your saved catalog"))
        composeRule.onNodeWithText("Updating your saved catalog").assertIsDisplayed()
        composeRule.onNode(hasPoliteLiveRegion()).assertExists()
        composeRule.onAllNodesWithText("Retry").assertCountEquals(0)
    }

    private fun setHomeContent(state: HomeUiState) {
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                HomeScreen(
                    versionName = "test",
                    state = state,
                )
            }
        }
    }

    private fun hasPoliteLiveRegion(): SemanticsMatcher = SemanticsMatcher.expectValue(
        SemanticsProperties.LiveRegion,
        LiveRegionMode.Polite,
    )
}
