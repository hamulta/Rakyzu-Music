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
    data object Library : RakyzuRoute

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

internal fun dismissNowPlaying(backStack: MutableList<NavKey>) {
    if (backStack.size > 1 && backStack.lastOrNull() == RakyzuRoute.NowPlaying) {
        backStack.removeAt(backStack.lastIndex)
    }
}
