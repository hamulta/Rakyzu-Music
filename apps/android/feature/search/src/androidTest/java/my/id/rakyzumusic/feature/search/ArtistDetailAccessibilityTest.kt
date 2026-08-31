package my.id.rakyzumusic.feature.search

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import my.id.rakyzumusic.core.designsystem.theme.RakyzuMusicTheme
import my.id.rakyzumusic.core.model.Album
import my.id.rakyzumusic.core.model.Artist
import my.id.rakyzumusic.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArtistDetailAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readyArtistHasHeadingAndAccessibleBackAction() {
        var backCalls = 0
        setArtistContent(onBack = { backCalls += 1 })

        composeRule.onNodeWithText(ARTIST.name)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule.onNodeWithContentDescription("Back to search")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.runOnIdle { assertEquals(1, backCalls) }
    }

    @Test
    fun playAllStartsTheCompleteArtistQueueAtTheBeginning() {
        var playedIds = emptyList<String>()
        var playedIndex: Int? = null
        setArtistContent { tracks, index ->
            playedIds = tracks.map(Track::id)
            playedIndex = index
        }

        composeRule.onNodeWithText("Play all")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        composeRule.runOnIdle {
            assertEquals(TRACKS.map(Track::id), playedIds)
            assertEquals(0, playedIndex)
        }
    }

    @Test
    fun trackRowsExposeNamedPlayActionsAndMinimumTargets() {
        var playedIndex: Int? = null
        setArtistContent { _, index -> playedIndex = index }

        composeRule.onNode(hasPlayAction("Play Signal Bloom by ${ARTIST.name}"))
            .assertHasClickAction()
            .assertHeightIsAtLeast(64.dp)
            .performClick()

        composeRule.runOnIdle { assertEquals(1, playedIndex) }
    }

    @Test
    fun unavailableArtistUsesPoliteStatusAndKeepsBackAction() {
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                ArtistDetailScreen(
                    state = ArtistDetailUiState(
                        artistId = "missing",
                        hasObservedCatalog = true,
                    ),
                    onBack = {},
                    onTrackPlay = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Artist unavailable")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule.onNode(hasPoliteLiveRegion()).assertExists()
        composeRule.onNodeWithContentDescription("Back to search").assertHasClickAction()
    }

    private fun setArtistContent(
        onBack: () -> Unit = {},
        onTrackPlay: (List<Track>, Int) -> Unit = { _, _ -> },
    ) {
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                ArtistDetailScreen(
                    state = READY_STATE,
                    onBack = onBack,
                    onTrackPlay = onTrackPlay,
                )
            }
        }
    }

    private fun hasPoliteLiveRegion(): SemanticsMatcher = SemanticsMatcher.expectValue(
        SemanticsProperties.LiveRegion,
        LiveRegionMode.Polite,
    )

    private fun hasPlayAction(label: String): SemanticsMatcher =
        SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick) and
            SemanticsMatcher("OnClick action label is $label") { node ->
                node.config[SemanticsActions.OnClick].label == label
            }

    private companion object {
        val ARTIST = Artist("artist-1", "Rakyzu Sessions")
        val ALBUM = Album("album-1", ARTIST.id, "Signal Zero", "2026-08-31")
        val TRACKS = listOf(
            track("track-1", "Midnight Signal", 1),
            track("track-2", "Signal Bloom", 2),
        )
        val READY_STATE = ArtistDetailUiState(
            artistId = ARTIST.id,
            hasObservedCatalog = true,
            artist = ARTIST,
            releases = listOf(ArtistRelease(ALBUM, TRACKS.size)),
            tracks = TRACKS,
        )

        fun track(id: String, title: String, number: Int) = Track(
            id = id,
            title = title,
            artist = ARTIST.name,
            durationMs = 180_000L,
            artistId = ARTIST.id,
            albumId = ALBUM.id,
            albumTitle = ALBUM.title,
            trackNumber = number,
        )
    }
}
