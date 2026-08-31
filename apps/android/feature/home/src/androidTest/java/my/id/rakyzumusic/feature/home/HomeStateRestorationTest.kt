package my.id.rakyzumusic.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import my.id.rakyzumusic.core.designsystem.theme.RakyzuMusicTheme
import my.id.rakyzumusic.core.model.CatalogSnapshot
import my.id.rakyzumusic.core.model.EditorialShelf
import my.id.rakyzumusic.core.model.Track
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeStateRestorationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectedFilterSurvivesSavedInstanceStateRestore() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                HomeScreen(
                    versionName = "test",
                    state = populatedHomeState(),
                )
            }
        }

        composeRule.onNodeWithText("New releases")
            .performClick()
            .assertIsSelected()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("New releases").assertIsSelected()
    }

    @Test
    fun verticalHomePositionSurvivesSavedInstanceStateRestore() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                HomeScreen(
                    versionName = "test",
                    state = populatedHomeState(editorialShelfCount = 8),
                )
            }
        }

        composeRule.onNodeWithTag("home-list").performScrollToIndex(9)
        composeRule.onNodeWithText("Shelf 6").assertIsDisplayed()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Shelf 6").assertIsDisplayed()
    }

    @Test
    fun horizontalShelfPositionSurvivesSavedInstanceStateRestore() {
        val restorationTester = StateRestorationTester(composeRule)
        val tracks = List(10) { index -> track(index + 1) }
        restorationTester.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                TrackShelf(
                    title = "Restored shelf",
                    subtitle = null,
                    tracks = tracks,
                    onTrackPlay = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithTag("track-shelf-list-Restored shelf")
            .performScrollToIndex(6)
        composeRule.onNodeWithText("Track 7").assertIsDisplayed()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Track 7").assertIsDisplayed()
    }

    private fun populatedHomeState(editorialShelfCount: Int = 1): HomeUiState {
        val tracks = List(editorialShelfCount.coerceAtLeast(1)) { index -> track(index + 1) }
        val shelves = List(editorialShelfCount) { index ->
            EditorialShelf(
                id = "shelf-${index + 1}",
                title = "Shelf ${index + 1}",
                subtitle = null,
                position = index,
                tracks = listOf(tracks[index]),
            )
        }
        return HomeUiState(
            catalog = CatalogSnapshot(
                artists = emptyList(),
                albums = emptyList(),
                tracks = tracks,
                lastSyncedAtEpochMillis = 42L,
                editorialShelves = shelves,
            ),
            isRefreshing = false,
        )
    }

    private fun track(index: Int) = Track(
        id = "track-$index",
        title = "Track $index",
        artist = "Rakyzu Sessions",
        durationMs = 180_000L,
    )
}
