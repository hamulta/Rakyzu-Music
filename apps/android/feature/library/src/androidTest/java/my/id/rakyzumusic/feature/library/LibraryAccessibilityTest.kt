package my.id.rakyzumusic.feature.library

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
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
        composeRule.onNodeWithText("Browse music").assertHasClickAction()
    }

    @Test
    fun savedAlbumArtworkKeepsDetailNavigation() {
        var albumId: String? = null
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                LibraryScreen(
                    state = POPULATED,
                    onRefresh = {},
                    onTrackPlay = { _, _ -> },
                    onAlbumClick = { albumId = it.id },
                    onArtistClick = {},
                    onRemoveTrack = {},
                    onRemoveAlbum = {},
                    onUnfollowArtist = {},
                )
            }
        }

        composeRule.onNodeWithTag("library-album-artwork-failed-album-1")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Signal Zero").performClick()
        composeRule.runOnIdle { assertEquals("album-1", albumId) }
    }

    @Test
    fun followedArtistKeepsDetailNavigation() {
        var artistId: String? = null
        setLibraryContent(POPULATED.copy(filter = LibraryFilter.Artists)) {
            artistId = it.id
        }
        composeRule.onNodeWithText("Rakyzu Sessions").performClick()
        composeRule.runOnIdle { assertEquals("artist-1", artistId) }
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

    @Test
    fun controlsExposeSearchFilterAndSortCallbacks() {
        var query = ""
        var filter = LibraryFilter.All
        var sort = LibrarySort.RecentlyAdded
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                LibraryScreen(
                    state = POPULATED,
                    onRefresh = {},
                    onQueryChange = { query = it },
                    onFilterSelected = { filter = it },
                    onSortSelected = { sort = it },
                    onTrackPlay = { _, _ -> },
                    onAlbumClick = {},
                    onArtistClick = {},
                    onRemoveTrack = {},
                    onRemoveAlbum = {},
                    onUnfollowArtist = {},
                )
            }
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("signal")
        composeRule.onNodeWithText("Albums").performClick()
        composeRule.onNodeWithText("Oldest added").performClick()

        composeRule.runOnIdle {
            assertEquals("signal", query)
            assertEquals(LibraryFilter.Albums, filter)
            assertEquals(LibrarySort.OldestAdded, sort)
        }
    }

    @Test
    fun bulkAndRowPlaybackUseTheExactVisibleLikedSongsOrder() {
        var playedIds = emptyList<String>()
        var playIndex = -1
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                LibraryScreen(
                    state = ORDERED,
                    onRefresh = {},
                    onTrackPlay = { tracks, index ->
                        playedIds = tracks.map(Track::id)
                        playIndex = index
                    },
                    onAlbumClick = {},
                    onArtistClick = {},
                    onRemoveTrack = {},
                    onRemoveAlbum = {},
                    onUnfollowArtist = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            "Play 2 liked songs in Recently added order",
        ).performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("track-new", "track-old"), playedIds)
            assertEquals(0, playIndex)
        }

        composeRule.onNodeWithText("Alpha Signal").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("track-new", "track-old"), playedIds)
            assertEquals(1, playIndex)
        }
    }

    @Test
    fun albumAndArtistRowsExposeExplicitTalkBackNavigationActions() {
        setLibraryContent(POPULATED.copy(filter = LibraryFilter.Albums))

        composeRule.onNode(hasClickLabel("Open album Signal Zero"))
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)

        setLibraryContent(POPULATED.copy(filter = LibraryFilter.Artists))
        composeRule.onNode(hasClickLabel("Open artist Rakyzu Sessions"))
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun freshnessOfflineAndPendingStatesRemainExplicit() {
        setLibraryContent(
            POPULATED.copy(
                freshness = LibraryFreshness(ageMinutes = 1_440L),
                isOnline = false,
                message = "You're offline. Rakyzu Music will sync your Library when your connection returns.",
                pendingItems = setOf("Track:track-1"),
            ),
        )

        composeRule.onNodeWithText("Updated 1 day ago").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Retry Library sync when online")
            .assertHasClickAction()
        composeRule.onNodeWithContentDescription(
            "Updating Midnight Signal in Liked Songs",
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun largeTextPolicyProvidesExpandedNavigationRows() {
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                    LibraryScreen(
                        state = POPULATED.copy(filter = LibraryFilter.Albums),
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

        composeRule.onNode(hasClickLabel("Open album Signal Zero"))
            .assertHeightIsAtLeast(88.dp)
    }

    private fun setLibraryContent(
        state: LibraryUiState,
        onArtistClick: (Artist) -> Unit = {},
    ) {
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                LibraryScreen(
                    state = state,
                    onRefresh = {},
                    onTrackPlay = { _, _ -> },
                    onAlbumClick = {},
                    onArtistClick = onArtistClick,
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

    private fun hasClickLabel(label: String): SemanticsMatcher = SemanticsMatcher(
        "has click label '$label'",
    ) { node ->
        node.config.contains(SemanticsActions.OnClick) &&
            node.config[SemanticsActions.OnClick].label == label
    }

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
        val ORDERED = LibraryUiState(
            hasObservedLibrary = true,
            library = LibrarySnapshot(
                likedTracks = listOf(
                    Track("track-old", "Alpha Signal", "Rakyzu Sessions", 180_000L),
                    Track("track-new", "Beta Signal", "Rakyzu Sessions", 190_000L),
                ),
                savedAlbums = emptyList(),
                followedArtists = emptyList(),
                lastSyncedAtEpochMillis = 42L,
                likedTrackSavedAtEpochMillis = mapOf(
                    "track-old" to 10L,
                    "track-new" to 20L,
                ),
            ),
        )
    }
}
