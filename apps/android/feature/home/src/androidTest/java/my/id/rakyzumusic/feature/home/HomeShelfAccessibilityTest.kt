package my.id.rakyzumusic.feature.home

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.test.ext.junit.runners.AndroidJUnit4
import my.id.rakyzumusic.core.designsystem.theme.RakyzuMusicTheme
import my.id.rakyzumusic.core.data.media.ArtworkRequestFailure
import my.id.rakyzumusic.core.data.media.ArtworkRequestResult
import my.id.rakyzumusic.core.model.CatalogSnapshot
import my.id.rakyzumusic.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeShelfAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun trackCardsExposeOrderedPlayActionsAndMinimumTouchTargets() {
        val tracks = listOf(
            track(id = "track-1", title = "Midnight Signal"),
            track(id = "track-2", title = "Afterglow Circuit"),
        )
        var playedIndex: Int? = null
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                TrackShelf(
                    title = "All tracks",
                    subtitle = null,
                    tracks = tracks,
                    onTrackPlay = { _, index -> playedIndex = index },
                )
            }
        }

        val playActions = composeRule.onAllNodes(hasPlayAction())
            .fetchSemanticsNodes()
        assertEquals(
            listOf("Play Midnight Signal", "Play Afterglow Circuit"),
            playActions.map { it.config[SemanticsActions.OnClick].label },
        )
        assertEquals(
            listOf(0f, 1f),
            playActions.map { it.config[SemanticsProperties.TraversalIndex] },
        )

        composeRule.onNode(hasPlayAction("Play Afterglow Circuit"))
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.runOnIdle { assertEquals(1, playedIndex) }
    }

    @Test
    fun interactiveHomeControlsMeetMinimumTouchTarget() {
        val track = track(id = "track-1", title = "Midnight Signal")
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                HomeScreen(
                    versionName = "test",
                    state = HomeUiState(
                        catalog = CatalogSnapshot(
                            artists = emptyList(),
                            albums = emptyList(),
                            tracks = listOf(track),
                            lastSyncedAtEpochMillis = 42L,
                        ),
                        isRefreshing = false,
                    ),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Profile")
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("Music")
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("New releases")
            .assertHeightIsAtLeast(48.dp)
        composeRule.onAllNodes(hasPlayAction("Play Midnight Signal"))[0]
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun staleSavedCatalogExposesFreshnessAndWarning() {
        val track = track(id = "track-1", title = "Midnight Signal")
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                HomeScreen(
                    versionName = "test",
                    state = HomeUiState(
                        catalog = CatalogSnapshot(
                            artists = emptyList(),
                            albums = emptyList(),
                            tracks = listOf(track),
                            lastSyncedAtEpochMillis = 42L,
                        ),
                        catalogFreshness = CatalogFreshness(ageMinutes = 1_500L),
                        isRefreshing = false,
                        refreshMessage = "You're offline. Check your connection and try again.",
                    ),
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            "Catalog freshness: Updated 1 day ago",
        ).assertExists()
        composeRule.onNodeWithText("Saved catalog may be out of date").assertExists()
    }

    @Test
    fun offlineRecoveryStatusKeepsManualRetryAccessible() {
        var retryCalls = 0
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                HomeScreen(
                    versionName = "test",
                    state = HomeUiState(
                        isRefreshing = false,
                        isWaitingForConnection = true,
                        refreshMessage =
                            "You're offline. Rakyzu Music will retry when your connection returns.",
                    ),
                    onRetryCatalog = { retryCalls += 1 },
                )
            }
        }

        composeRule.onNodeWithText("Waiting for connection").assertExists()
        composeRule.onNodeWithText("Retry")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.runOnIdle { assertEquals(1, retryCalls) }
    }

    @Test
    fun adaptiveShelfGivesTrackTextMoreHorizontalSpace() {
        val track = track(
            id = "track-1",
            title = "A deliberately long track title for large text",
        )
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                TrackShelf(
                    title = "All tracks",
                    subtitle = "Adaptive shelf",
                    tracks = listOf(track),
                    layoutSpec = resolveHomeLayoutSpec(
                        availableWidth = 320.dp,
                        fontScale = 2f,
                    ),
                    onTrackPlay = { _, _ -> },
                )
            }
        }

        composeRule.onNode(hasPlayAction(track.homePlayActionLabel()))
            .assertWidthIsAtLeast(220.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun missingArtworkUsesPlaceholderWithoutCallingDelivery() {
        var deliveryCalls = 0
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                AlbumArtwork(
                    albumId = "",
                    colors = listOf(Color.Black, Color.DarkGray),
                    artworkRequestProvider = {
                        deliveryCalls += 1
                        ArtworkRequestResult.Failure(ArtworkRequestFailure.InvalidAlbumId)
                    },
                    modifier = Modifier.size(128.dp),
                )
            }
        }

        composeRule.onNodeWithTag("album-artwork-placeholder-").assertExists()
        composeRule.runOnIdle { assertEquals(0, deliveryCalls) }
    }

    @Test
    fun unavailableArtworkUsesExplicitFailureVisual() {
        val albumId = "a2000000-0000-4000-8000-000000000001"
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                AlbumArtwork(
                    albumId = albumId,
                    colors = listOf(Color.Black, Color.DarkGray),
                    artworkRequestProvider = {
                        ArtworkRequestResult.Failure(ArtworkRequestFailure.NotAuthenticated)
                    },
                    modifier = Modifier.size(128.dp),
                )
            }
        }

        composeRule.onNodeWithTag("album-artwork-failed-$albumId").assertExists()
    }

    @Test
    fun trackMenuExposesLikeSaveAndFollowEntryPoints() {
        val track = track(id = "track-1", title = "Midnight Signal")
        val actions = mutableListOf<String>()
        composeRule.setContent {
            RakyzuMusicTheme(darkTheme = true) {
                TrackShelf(
                    title = "All tracks",
                    subtitle = null,
                    tracks = listOf(track),
                    onTrackPlay = { _, _ -> },
                    onTrackLikeChange = { _, saved -> actions += "track:$saved" },
                    onAlbumSaveChange = { _, saved -> actions += "album:$saved" },
                    onArtistFollowChange = { _, saved -> actions += "artist:$saved" },
                )
            }
        }

        fun selectAction(label: String) {
            composeRule.onNodeWithContentDescription("Library actions for Midnight Signal")
                .assertHeightIsAtLeast(48.dp)
                .performClick()
            composeRule.onNodeWithText(label).assertHasClickAction().performClick()
        }

        selectAction("Add to Liked Songs")
        selectAction("Save album")
        selectAction("Follow artist")

        composeRule.runOnIdle {
            assertEquals(listOf("track:true", "album:true", "artist:true"), actions)
        }
    }

    private fun hasPlayAction(expectedLabel: String? = null): SemanticsMatcher =
        SemanticsMatcher("has a labeled Home playback action") { node ->
            val label = if (node.config.contains(SemanticsActions.OnClick)) {
                node.config[SemanticsActions.OnClick].label
            } else {
                null
            }
            label != null && (expectedLabel == null || label == expectedLabel)
        }

    private fun track(id: String, title: String) = Track(
        id = id,
        title = title,
        artist = "Rakyzu Sessions",
        durationMs = 180_000L,
        artistId = "artist-1",
        albumId = "album-1",
        albumTitle = "Signal Zero",
    )
}
