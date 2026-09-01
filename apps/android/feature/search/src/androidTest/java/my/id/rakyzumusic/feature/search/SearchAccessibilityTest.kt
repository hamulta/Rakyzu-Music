package my.id.rakyzumusic.feature.search

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import my.id.rakyzumusic.core.designsystem.theme.RakyzuMusicTheme
import my.id.rakyzumusic.core.model.Artist
import my.id.rakyzumusic.core.model.Album
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

    @Test
    fun artistResultExposesNamedNavigationActionAndMinimumTarget() {
        val artist = Artist("artist-1", "Rakyzu Sessions")
        var selectedArtist: Artist? = null
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                SearchScreen(
                    state = SearchUiState(
                        query = "rakyzu",
                        catalog = CATALOG.copy(artists = listOf(artist)),
                        results = SearchResults(
                            artists = listOf(artist),
                            totalArtistMatches = 1,
                        ),
                        hasObservedCatalog = true,
                    ),
                    onQueryChange = {},
                    onClearQuery = {},
                    onTrackPlay = { _, _ -> },
                    onArtistClick = { selectedArtist = it },
                )
            }
        }

        composeRule.onNode(hasPlayAction("Open Rakyzu Sessions artist"))
            .assertHasClickAction()
            .assertHeightIsAtLeast(64.dp)
            .performClick()
        composeRule.runOnIdle { assertEquals(artist, selectedArtist) }
    }

    @Test
    fun albumResultExposesNamedNavigationActionAndMinimumTarget() {
        val artist = Artist("artist-1", "Rakyzu Sessions")
        val album = Album("album-1", artist.id, "Signal Zero", "2026-08-31")
        var selectedAlbum: Album? = null
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                SearchScreen(
                    state = SearchUiState(
                        query = "signal",
                        catalog = CATALOG.copy(
                            artists = listOf(artist),
                            albums = listOf(album),
                        ),
                        results = SearchResults(
                            albums = listOf(SearchAlbumResult(album, artist.name)),
                            totalAlbumMatches = 1,
                        ),
                        hasObservedCatalog = true,
                    ),
                    onQueryChange = {},
                    onClearQuery = {},
                    onTrackPlay = { _, _ -> },
                    onAlbumClick = { selectedAlbum = it },
                )
            }
        }

        composeRule.onNode(hasPlayAction("Open Signal Zero album"))
            .assertHasClickAction()
            .assertHeightIsAtLeast(64.dp)
            .performClick()
        composeRule.runOnIdle { assertEquals(album, selectedAlbum) }
    }

    @Test
    fun searchFieldAutofocusesAndImeSearchClearsFocus() {
        var submitCalls = 0
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                SearchScreen(
                    state = SearchUiState(catalog = CATALOG, hasObservedCatalog = true),
                    onQueryChange = {},
                    onClearQuery = {},
                    onTrackPlay = { _, _ -> },
                    onSearchSubmit = { submitCalls += 1 },
                )
            }
        }

        composeRule.onNodeWithTag(SEARCH_FIELD_TAG).assertIsFocused()
        composeRule.onNodeWithTag(SEARCH_FIELD_TAG).performImeAction()
        composeRule.onNodeWithTag(SEARCH_FIELD_TAG).assertIsNotFocused()
        composeRule.runOnIdle { assertEquals(1, submitCalls) }
    }

    @Test
    fun clearActionReturnsFocusToSearchField() {
        var clearCalls = 0
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                SearchScreen(
                    state = SearchUiState(
                        query = "signal",
                        catalog = CATALOG,
                        hasObservedCatalog = true,
                    ),
                    onQueryChange = {},
                    onClearQuery = { clearCalls += 1 },
                    onTrackPlay = { _, _ -> },
                )
            }
        }
        composeRule.onNodeWithTag(SEARCH_FIELD_TAG).performImeAction()
        composeRule.onNodeWithTag(SEARCH_FIELD_TAG).assertIsNotFocused()

        composeRule.onNodeWithContentDescription("Clear search").performClick()

        composeRule.onNodeWithTag(SEARCH_FIELD_TAG).assertIsFocused()
        composeRule.runOnIdle { assertEquals(1, clearCalls) }
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
