package my.id.rakyzumusic.feature.library

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
import my.id.rakyzumusic.core.model.Album
import my.id.rakyzumusic.core.model.Artist
import my.id.rakyzumusic.core.model.LibraryAlbum
import my.id.rakyzumusic.core.model.LibrarySnapshot
import my.id.rakyzumusic.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingAndEmptyStatesArePoliteAndExplicit() {
        setLibraryContent(LibraryUiState())
        composeRule.onNodeWithText("Loading your Library").assertIsDisplayed()
        composeRule.onNode(hasPoliteLiveRegion()).assertExists()

        setLibraryContent(LibraryUiState(hasObservedLibrary = true, library = EMPTY))
        composeRule.onNodeWithText("Your Library is ready").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Like tracks, save albums, or follow artists from Search to keep them here.",
        ).assertIsDisplayed()
    }

    @Test
    fun likedSongHasAccessiblePlaybackAndRemovalTargets() {
        var playIndex = -1
        var removals = 0
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                LibraryScreen(
                    state = POPULATED,
                    onRefresh = {},
                    onTrackPlay = { _, index -> playIndex = index },
                    onAlbumClick = {},
                    onArtistClick = {},
                    onRemoveTrack = { removals += 1 },
                    onRemoveAlbum = {},
                    onUnfollowArtist = {},
                )
            }
        }

        composeRule.onNodeWithText("Midnight Signal")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.runOnIdle { assertEquals(0, playIndex) }

        composeRule.onNodeWithContentDescription("Remove Midnight Signal from Liked Songs")
            .assertHasClickAction()
            .performClick()
        composeRule.runOnIdle { assertEquals(1, removals) }
    }

    @Test
    fun refreshFailureIsAnnouncedWithoutHidingCachedMusic() {
        setLibraryContent(
            POPULATED.copy(
                message = "Library sync needs an internet connection.",
                messageIsError = true,
            ),
        )

        composeRule.onNodeWithText("Library sync needs an internet connection.").assertIsDisplayed()
        composeRule.onNodeWithText("Midnight Signal").assertIsDisplayed()
        composeRule.onNode(hasPoliteLiveRegion()).assertExists()
    }

    private fun setLibraryContent(state: LibraryUiState) {
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                LibraryScreen(
                    state = state,
                    onRefresh = {},
                    onTrackPlay = { _, _ -> },
                    onAlbumClick = {},
                    onArtistClick = {},
                    onRemoveTrack = {},
                    onRemoveAlbum = {},
                    onUnfollowArtist = {},
                )
            }
        }
    }

    private fun hasPoliteLiveRegion(): SemanticsMatcher = SemanticsMatcher.expectValue(
        SemanticsProperties.LiveRegion,
        LiveRegionMode.Polite,
    )

    private companion object {
        val TRACK = Track(
            id = "track-1",
            title = "Midnight Signal",
            artist = "Rakyzu Sessions",
            durationMs = 185_900L,
            artistId = "artist-1",
            albumId = "album-1",
            albumTitle = "Signal Zero",
        )
        val ARTIST = Artist("artist-1", "Rakyzu Sessions")
        val ALBUM = Album("album-1", "artist-1", "Signal Zero", "2026-08-29")
        val EMPTY = LibrarySnapshot(emptyList(), emptyList(), emptyList(), 42L)
        val POPULATED = LibraryUiState(
            hasObservedLibrary = true,
            library = LibrarySnapshot(
                likedTracks = listOf(TRACK),
                savedAlbums = listOf(LibraryAlbum(ALBUM, ARTIST.name)),
                followedArtists = listOf(ARTIST),
                lastSyncedAtEpochMillis = 42L,
            ),
        )
    }
}
