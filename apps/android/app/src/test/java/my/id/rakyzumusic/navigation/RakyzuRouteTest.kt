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
}
