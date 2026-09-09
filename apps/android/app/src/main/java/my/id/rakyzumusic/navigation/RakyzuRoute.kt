package my.id.rakyzumusic.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface RakyzuRoute : NavKey {
    @Serializable
    data object Home : RakyzuRoute

    @Serializable
    data object Search : RakyzuRoute

    @Serializable
    data class ArtistDetail(val artistId: String) : RakyzuRoute

    @Serializable
    data class AlbumDetail(val albumId: String) : RakyzuRoute

    @Serializable
    data object Library : RakyzuRoute

    @Serializable
    data object Create : RakyzuRoute

    @Serializable
    data object NowPlaying : RakyzuRoute
}

internal fun selectTopLevelRoute(
    backStack: MutableList<NavKey>,
    route: RakyzuRoute,
) {
    if (backStack.lastOrNull() == route) return

    backStack.clear()
    backStack.add(route)
}

internal fun openNowPlaying(backStack: MutableList<NavKey>) {
    if (backStack.lastOrNull() != RakyzuRoute.NowPlaying) {
        backStack.add(RakyzuRoute.NowPlaying)
    }
}

internal fun openArtistDetail(
    backStack: MutableList<NavKey>,
    artistId: String,
) {
    val route = RakyzuRoute.ArtistDetail(artistId)
    if (artistId.isBlank() || backStack.lastOrNull() == route) return

    backStack.navigateToExistingOrAdd(route)
}

internal fun dismissArtistDetail(backStack: MutableList<NavKey>) {
    if (backStack.size > 1 && backStack.lastOrNull() is RakyzuRoute.ArtistDetail) {
        backStack.removeAt(backStack.lastIndex)
    }
}

internal fun openAlbumDetail(
    backStack: MutableList<NavKey>,
    albumId: String,
) {
    val route = RakyzuRoute.AlbumDetail(albumId)
    if (albumId.isBlank() || backStack.lastOrNull() == route) return

    backStack.navigateToExistingOrAdd(route)
}

internal fun dismissAlbumDetail(backStack: MutableList<NavKey>) {
    if (backStack.size > 1 && backStack.lastOrNull() is RakyzuRoute.AlbumDetail) {
        backStack.removeAt(backStack.lastIndex)
    }
}

internal fun dismissNowPlaying(backStack: MutableList<NavKey>) {
    if (backStack.size > 1 && backStack.lastOrNull() == RakyzuRoute.NowPlaying) {
        backStack.removeAt(backStack.lastIndex)
    }
}

private fun MutableList<NavKey>.navigateToExistingOrAdd(route: RakyzuRoute) {
    val existingIndex = indexOfLast { it == route }
    if (existingIndex < 0) {
        add(route)
    } else {
        subList(existingIndex + 1, size).clear()
    }
}
