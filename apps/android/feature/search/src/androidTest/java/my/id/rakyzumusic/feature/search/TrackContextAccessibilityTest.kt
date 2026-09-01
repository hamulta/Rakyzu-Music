package my.id.rakyzumusic.feature.search

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import my.id.rakyzumusic.core.designsystem.theme.RakyzuMusicTheme
import my.id.rakyzumusic.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackContextAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sheetShowsLocalMetadataAndAccessibleCloseAction() {
        var dismissCalls = 0
        setSheet(onDismiss = { dismissCalls += 1 })

        listOf(
            "Track details",
            TRACK.title,
            "Artist",
            TRACK.artist,
            "Album",
            TRACK.albumTitle,
            "Duration",
            "3:00",
            "Disc",
            "2",
            "Track",
            "4",
            "Explicit",
            "Yes",
        ).forEach { text -> composeRule.onNodeWithText(text).assertExists() }

        composeRule.onNodeWithContentDescription("Close track details")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.runOnIdle { assertEquals(1, dismissCalls) }
    }

    @Test
    fun playActionUsesNamedMinimumTouchTarget() {
        var playCalls = 0
        setSheet(onPlay = { playCalls += 1 })

        composeRule.onNode(hasClickAction("Play Midnight Signal"))
            .assertHasClickAction()
            .assertHeightIsAtLeast(56.dp)
            .performClick()
        composeRule.runOnIdle { assertEquals(1, playCalls) }
    }

    @Test
    fun canonicalAssociationsExposeInternalNavigationActions() {
        var artistCalls = 0
        var albumCalls = 0
        setSheet(
            onViewArtist = { artistCalls += 1 },
            onViewAlbum = { albumCalls += 1 },
        )

        composeRule.onNode(hasClickAction("View Rakyzu Sessions artist"))
            .assertHeightIsAtLeast(56.dp)
            .performClick()
        composeRule.onNode(hasClickAction("View Signal Zero album"))
            .assertHeightIsAtLeast(56.dp)
            .performClick()
        composeRule.runOnIdle {
            assertEquals(1, artistCalls)
            assertEquals(1, albumCalls)
        }
    }

    @Test
    fun blankAssociationsHideNavigationActions() {
        setSheet(
            track = TRACK.copy(artistId = "", albumId = "   "),
            onViewArtist = {},
            onViewAlbum = {},
        )

        composeRule.onAllNodes(hasActionLabelStartingWith("View ")).assertCountEquals(0)
        composeRule.onNode(hasClickAction("Play Midnight Signal")).assertExists()
    }

    private fun setSheet(
        track: Track = TRACK,
        onDismiss: () -> Unit = {},
        onPlay: (() -> Unit)? = {},
        onViewArtist: (() -> Unit)? = {},
        onViewAlbum: (() -> Unit)? = {},
    ) {
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                TrackContextSheet(
                    track = track,
                    onDismiss = onDismiss,
                    onPlay = onPlay,
                    onViewArtist = onViewArtist,
                    onViewAlbum = onViewAlbum,
                )
            }
        }
    }

    private fun hasClickAction(label: String): SemanticsMatcher =
        SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick) and
            SemanticsMatcher("OnClick action label is $label") { node ->
                node.config[SemanticsActions.OnClick].label == label
            }

    private fun hasActionLabelStartingWith(prefix: String): SemanticsMatcher =
        SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick) and
            SemanticsMatcher("OnClick action label starts with $prefix") { node ->
                node.config[SemanticsActions.OnClick].label?.startsWith(prefix) == true
            }

    private companion object {
        val TRACK = Track(
            id = "track-1",
            title = "Midnight Signal",
            artist = "Rakyzu Sessions",
            durationMs = 180_000L,
            artistId = "artist-1",
            albumId = "album-1",
            albumTitle = "Signal Zero",
            discNumber = 2,
            trackNumber = 4,
            isExplicit = true,
        )
    }
}
