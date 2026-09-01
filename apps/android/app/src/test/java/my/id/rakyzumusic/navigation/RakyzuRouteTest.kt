package my.id.rakyzumusic.navigation

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class RakyzuRouteTest {
    @Test
    fun selectingTopLevelRouteReplacesPreviousRoute() {
        val backStack = mutableListOf<NavKey>(RakyzuRoute.Home)

        selectTopLevelRoute(backStack, RakyzuRoute.Search)

        assertEquals(listOf(RakyzuRoute.Search), backStack)
    }

    @Test
    fun selectingCurrentRouteDoesNotDuplicateIt() {
        val backStack = mutableListOf<NavKey>(RakyzuRoute.Library)

        selectTopLevelRoute(backStack, RakyzuRoute.Library)

        assertEquals(listOf(RakyzuRoute.Library), backStack)
    }

    @Test
    fun nowPlayingIsAddedOnceAndDismissedToPreviousDestination() {
        val backStack = mutableListOf<NavKey>(RakyzuRoute.Home)

        openNowPlaying(backStack)
        openNowPlaying(backStack)

        assertEquals(listOf(RakyzuRoute.Home, RakyzuRoute.NowPlaying), backStack)

        dismissNowPlaying(backStack)

        assertEquals(listOf(RakyzuRoute.Home), backStack)
    }

    @Test
    fun dismissDoesNotRemoveTheOnlyDestination() {
        val backStack = mutableListOf<NavKey>(RakyzuRoute.NowPlaying)

        dismissNowPlaying(backStack)

        assertEquals(listOf(RakyzuRoute.NowPlaying), backStack)
    }

    @Test
    fun artistDetailIsAddedOnceAndDismissedBackToSearch() {
        val backStack = mutableListOf<NavKey>(RakyzuRoute.Search)

        openArtistDetail(backStack, "artist-1")
        openArtistDetail(backStack, "artist-1")

        assertEquals(
            listOf(RakyzuRoute.Search, RakyzuRoute.ArtistDetail("artist-1")),
            backStack,
        )

        dismissArtistDetail(backStack)

        assertEquals(listOf(RakyzuRoute.Search), backStack)
    }

    @Test
    fun blankArtistIdIsRejectedWithoutChangingNavigation() {
        val backStack = mutableListOf<NavKey>(RakyzuRoute.Search)

        openArtistDetail(backStack, "  ")

        assertEquals(listOf(RakyzuRoute.Search), backStack)
    }

    @Test
    fun albumDetailIsAddedOnceAndDismissedToPreviousDestination() {
        val artist = RakyzuRoute.ArtistDetail("artist-1")
        val backStack = mutableListOf<NavKey>(RakyzuRoute.Search, artist)

        openAlbumDetail(backStack, "album-1")
        openAlbumDetail(backStack, "album-1")

        assertEquals(
            listOf(RakyzuRoute.Search, artist, RakyzuRoute.AlbumDetail("album-1")),
            backStack,
        )

        dismissAlbumDetail(backStack)

        assertEquals(listOf(RakyzuRoute.Search, artist), backStack)
    }

    @Test
    fun blankAlbumIdIsRejectedWithoutChangingNavigation() {
        val backStack = mutableListOf<NavKey>(RakyzuRoute.Search)

        openAlbumDetail(backStack, "")

        assertEquals(listOf(RakyzuRoute.Search), backStack)
    }

    @Test
    fun metadataNavigationReturnsToExistingArtistWithoutDuplicatingIt() {
        val artist = RakyzuRoute.ArtistDetail("artist-1")
        val backStack = mutableListOf<NavKey>(
            RakyzuRoute.Search,
            artist,
            RakyzuRoute.AlbumDetail("album-1"),
        )

        openArtistDetail(backStack, "artist-1")

        assertEquals(listOf(RakyzuRoute.Search, artist), backStack)
    }

    @Test
    fun metadataNavigationReturnsToExistingAlbumWithoutDuplicatingIt() {
        val album = RakyzuRoute.AlbumDetail("album-1")
        val backStack = mutableListOf<NavKey>(
            RakyzuRoute.Search,
            album,
            RakyzuRoute.ArtistDetail("artist-1"),
        )

        openAlbumDetail(backStack, "album-1")

        assertEquals(listOf(RakyzuRoute.Search, album), backStack)
    }
}
