package my.id.rakyzumusic.feature.search

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
class SearchAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun browseLandingHasHeadingAndNoInactiveClearAction() {
        setSearchContent(
            SearchUiState(catalog = CATALOG, hasObservedCatalog = true),
        )

        composeRule.onNodeWithText("Search")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule.onNodeWithText("Browse your catalog")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule.onAllNodesWithContentDescription("Clear search").assertCountEquals(0)
    }

    @Test
    fun noResultsUsesPoliteStatusAndAccessibleClearAction() {
        var clearCalls = 0
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                SearchScreen(
                    state = SearchUiState(
                        query = "unknown",
                        catalog = CATALOG,
                        hasObservedCatalog = true,
                    ),
                    onQueryChange = {},
                    onClearQuery = { clearCalls += 1 },
                    onTrackPlay = { _, _ -> },
                )
            }
        }

        composeRule.onNode(hasPoliteLiveRegion()).assertExists()
        composeRule.onNodeWithText("No results for “unknown”")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule.onNodeWithContentDescription("Clear search")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.runOnIdle { assertEquals(1, clearCalls) }
    }

    @Test
    fun trackResultsExposeOrderedPlayActionsAndMinimumTargets() {
        val tracks = listOf(
            track("track-1", "Midnight Signal"),
            track("track-2", "Signal Bloom"),
        )
        var playedIndex: Int? = null
        setSearchContent(
            SearchUiState(
                query = "signal",
                catalog = CATALOG.copy(tracks = tracks),
                results = SearchResults(
                    tracks = tracks,
                    totalTrackMatches = tracks.size,
                ),
                hasObservedCatalog = true,
            ),
            onTrackPlay = { _, index -> playedIndex = index },
        )

        val playNodes = composeRule.onAllNodes(hasPlayAction()).fetchSemanticsNodes()
        assertEquals(
            listOf("Play Midnight Signal", "Play Signal Bloom"),
            playNodes.map { it.config[SemanticsActions.OnClick].label },
        )
        composeRule.onNode(hasPlayAction("Play Signal Bloom"))
            .assertHasClickAction()
            .assertHeightIsAtLeast(64.dp)
            .performClick()
        composeRule.runOnIdle { assertEquals(1, playedIndex) }
    }

    private fun setSearchContent(
        state: SearchUiState,
        onTrackPlay: (List<Track>, Int) -> Unit = { _, _ -> },
    ) {
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                SearchScreen(
                    state = state,
                    onQueryChange = {},
                    onClearQuery = {},
                    onTrackPlay = onTrackPlay,
                )
            }
        }
    }

    private fun hasPoliteLiveRegion(): SemanticsMatcher = SemanticsMatcher.expectValue(
        SemanticsProperties.LiveRegion,
        LiveRegionMode.Polite,
    )

    private fun hasPlayAction(label: String? = null): SemanticsMatcher =
        SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick).let { hasClick ->
            if (label == null) {
                hasClick
            } else {
                hasClick and SemanticsMatcher("OnClick action label is $label") { node ->
                    node.config[SemanticsActions.OnClick].label == label
                }
            }
        }

    private companion object {
        val CATALOG = CatalogSnapshot(
            artists = emptyList(),
            albums = emptyList(),
            tracks = listOf(track("track-1", "Midnight Signal")),
            lastSyncedAtEpochMillis = 42L,
        )

        fun track(id: String, title: String) = Track(
            id = id,
            title = title,
            artist = "Rakyzu Sessions",
            durationMs = 180_000L,
        )
    }
}
