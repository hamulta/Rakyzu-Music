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
class AlbumDetailAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readyAlbumHasHeadingAndAccessibleBackAction() {
        var backCalls = 0
        setAlbumContent(onBack = { backCalls += 1 })

        composeRule.onNodeWithText(ALBUM.title)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule.onNodeWithContentDescription("Back")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.runOnIdle { assertEquals(1, backCalls) }
    }

    @Test
    fun playAlbumStartsCompleteOrderedQueueAtBeginning() {
        var playedIds = emptyList<String>()
        var playedIndex: Int? = null
        setAlbumContent { tracks, index ->
            playedIds = tracks.map(Track::id)
            playedIndex = index
        }

        composeRule.onNodeWithText("Play album")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        composeRule.runOnIdle {
            assertEquals(TRACKS.map(Track::id), playedIds)
            assertEquals(0, playedIndex)
        }
    }

    @Test
    fun multiDiscHeadingsAndTrackActionsAreAccessible() {
        var playedIndex: Int? = null
        setAlbumContent { _, index -> playedIndex = index }

        composeRule.onNodeWithText("Disc 1")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule.onNodeWithText("Disc 2")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule.onNode(hasClickAction("Play Second Disc Signal"))
            .assertHasClickAction()
            .assertHeightIsAtLeast(64.dp)
            .performClick()
        composeRule.runOnIdle { assertEquals(2, playedIndex) }
    }

    @Test
    fun unavailableAlbumUsesPoliteStatusAndKeepsBackAction() {
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                AlbumDetailScreen(
                    state = AlbumDetailUiState(
                        albumId = "missing",
                        hasObservedCatalog = true,
                    ),
                    onBack = {},
                    onTrackPlay = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Album unavailable")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule.onNode(hasPoliteLiveRegion()).assertExists()
        composeRule.onNodeWithContentDescription("Back").assertHasClickAction()
    }

    @Test
    fun trackMoreActionOpensTrackDetails() {
        setAlbumContent()

        composeRule.onNodeWithContentDescription("More options for Midnight Signal")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        composeRule.onNodeWithText("Track details")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    private fun setAlbumContent(
        onBack: () -> Unit = {},
        onTrackPlay: (List<Track>, Int) -> Unit = { _, _ -> },
    ) {
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                AlbumDetailScreen(
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

    private fun hasClickAction(label: String): SemanticsMatcher =
        SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick) and
            SemanticsMatcher("OnClick action label is $label") { node ->
                node.config[SemanticsActions.OnClick].label == label
            }

    private companion object {
        val ARTIST = Artist("artist-1", "Rakyzu Sessions")
        val ALBUM = Album("album-1", ARTIST.id, "Signal Zero", "2026-08-31")
        val TRACKS = listOf(
            track("track-1", "Midnight Signal", disc = 1, number = 1),
            track("track-2", "Signal Bloom", disc = 1, number = 2),
            track("track-3", "Second Disc Signal", disc = 2, number = 1),
        )
        val READY_STATE = AlbumDetailUiState(
            albumId = ALBUM.id,
            hasObservedCatalog = true,
            album = ALBUM,
            artist = ARTIST,
            tracks = TRACKS,
        )

        fun track(
            id: String,
            title: String,
            disc: Int,
            number: Int,
        ) = Track(
            id = id,
            title = title,
            artist = ARTIST.name,
            durationMs = 180_000L,
            artistId = ARTIST.id,
            albumId = ALBUM.id,
            albumTitle = ALBUM.title,
            discNumber = disc,
            trackNumber = number,
        )
    }
}
