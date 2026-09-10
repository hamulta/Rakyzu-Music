package my.id.rakyzumusic.feature.playlist

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import my.id.rakyzumusic.core.designsystem.theme.RakyzuMusicTheme
import my.id.rakyzumusic.core.model.PlaylistDetail
import my.id.rakyzumusic.core.model.PlaylistItem
import my.id.rakyzumusic.core.model.PlaylistRole
import my.id.rakyzumusic.core.model.PlaylistSummary
import my.id.rakyzumusic.core.model.PlaylistVisibility
import my.id.rakyzumusic.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistDetailAccessibilityTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun orderedPlaybackAndAccessibleBoundaryControls() {
        var played = emptyList<Track>()
        var start = -1
        var removed: String? = null
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                PlaylistDetailScreen(
                    state = PlaylistDetailUiState(detail = DETAIL, verified = true),
                    onBack = {}, onPlay = { tracks, index -> played = tracks; start = index },
                    onRefresh = {}, onAdd = {}, onRemove = { removed = it }, onMove = { _, _ -> },
                    onEdit = {}, onCancelEdit = {}, onName = {}, onDescription = {}, onSave = {},
                    onChooseArtwork = {}, onRemoveArtwork = {}, onRetryArtwork = {},
                )
            }
        }
        val list = composeRule.onNodeWithTag("playlist-detail")
        list.performScrollToNode(hasText("Play playlist"))
        composeRule.onNodeWithText("Play playlist").assertHeightIsAtLeast(48.dp).performClick()
        composeRule.runOnIdle { assertEquals(listOf(TRACK), played); assertEquals(0, start) }
        list.performScrollToNode(hasContentDescription("Move Song up"))
        composeRule.onNodeWithContentDescription("Move Song up").assertIsNotEnabled().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Move Song down").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Remove Song from playlist").performClick()
        composeRule.runOnIdle { assertEquals(TRACK.id, removed) }
    }

    @Test fun viewerCannotEditTracksMetadataOrArtwork() {
        val viewerDetail = DETAIL.copy(
            playlist = DETAIL.playlist.copy(
                visibility = PlaylistVisibility.Private,
                accessRole = PlaylistRole.Viewer,
            ),
        )
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                PlaylistDetailScreen(
                    state = PlaylistDetailUiState(detail = viewerDetail, verified = true),
                    onBack = {}, onPlay = { _, _ -> }, onRefresh = {}, onAdd = {},
                    onRemove = {}, onMove = { _, _ -> }, onEdit = {}, onCancelEdit = {},
                    onName = {}, onDescription = {}, onSave = {}, onChooseArtwork = {},
                    onRemoveArtwork = {}, onRetryArtwork = {},
                )
            }
        }

        val list = composeRule.onNodeWithTag("playlist-detail")
        list.performScrollToNode(hasText("Choose cover"))
        composeRule.onNodeWithText("Choose cover").assertIsNotEnabled().assertHeightIsAtLeast(48.dp)
        list.performScrollToNode(hasText("Edit playlist details"))
        composeRule.onNodeWithText("Edit playlist details").assertIsNotEnabled()
        list.performScrollToNode(hasText("Leave playlist"))
        composeRule.onNodeWithText("Leave playlist").assertHeightIsAtLeast(48.dp)
        list.performScrollToNode(hasText("Add songs"))
        composeRule.onNodeWithText("Add songs").assertIsNotEnabled().assertHeightIsAtLeast(48.dp)
    }

    companion object {
        private val TRACK = Track("one", "Song", "Artist", 2000)
        private val DETAIL = PlaylistDetail(PlaylistSummary("playlist", "Mix", "", 1, 1, 1000, 1000),
            listOf(PlaylistItem(TRACK.id, TRACK)))
    }
}
