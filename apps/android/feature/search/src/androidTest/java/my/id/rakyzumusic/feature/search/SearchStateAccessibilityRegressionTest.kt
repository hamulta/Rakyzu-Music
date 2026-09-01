package my.id.rakyzumusic.feature.search

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class SearchStateAccessibilityRegressionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun initialLoadingIsPoliteAndSuppressesInactiveResultActions() {
        setSearchContent(SearchUiState())

        composeRule.onNodeWithText("Loading your searchable catalog").assertIsDisplayed()
        composeRule.onNode(hasPoliteLiveRegion()).assertExists()
        composeRule.onAllNodesWithText("Retry full search").assertCountEquals(0)
        composeRule.onAllNodesWithText("Load more results").assertCountEquals(0)
    }

    @Test
    fun failedRemoteSearchKeepsSavedResultAndExposesOneAccessibleRetry() {
        var retryCalls = 0
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                SearchScreen(
                    state = stateWithSavedResult(RemoteSearchStatus.Failed),
                    onQueryChange = {},
                    onClearQuery = {},
                    onTrackPlay = { _, _ -> },
                    onRetrySearch = { retryCalls += 1 },
                )
            }
        }

        composeRule.onNodeWithText("Full search is temporarily unavailable").assertIsDisplayed()
        composeRule.onNode(hasPoliteLiveRegion()).assertExists()
        composeRule.onNodeWithText("Midnight Signal").assertIsDisplayed()
        composeRule.onNodeWithText("Retry full search")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.runOnIdle { assertEquals(1, retryCalls) }
    }

    @Test
    fun loadingNextPageIsAnnouncedAndCannotBeTriggeredTwice() {
        setSearchContent(
            stateWithSavedResult(RemoteSearchStatus.Loaded).copy(
                nextRemoteOffset = 30,
                isLoadingMore = true,
            ),
        )

        composeRule.onNodeWithText("Loading more results")
            .assertIsDisplayed()
            .assertIsNotEnabled()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNode(hasPoliteLiveRegion()).assertExists()
    }

    private fun setSearchContent(state: SearchUiState) {
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                SearchScreen(
                    state = state,
                    onQueryChange = {},
                    onClearQuery = {},
                    onTrackPlay = { _, _ -> },
                )
            }
        }
    }

    private fun stateWithSavedResult(status: RemoteSearchStatus): SearchUiState {
        val result = Track(
            id = "track-1",
            title = "Midnight Signal",
            artist = "Rakyzu Sessions",
            durationMs = 180_000L,
        )
        val catalog = CatalogSnapshot(
            artists = emptyList(),
            albums = emptyList(),
            tracks = listOf(result),
            lastSyncedAtEpochMillis = 42L,
        )
        return SearchUiState(
            query = "signal",
            catalog = catalog,
            results = SearchResults(tracks = listOf(result), totalTrackMatches = 1),
            hasObservedCatalog = true,
            remoteSearchStatus = status,
        )
    }

    private fun hasPoliteLiveRegion(): SemanticsMatcher = SemanticsMatcher.expectValue(
        SemanticsProperties.LiveRegion,
        LiveRegionMode.Polite,
    )
}
