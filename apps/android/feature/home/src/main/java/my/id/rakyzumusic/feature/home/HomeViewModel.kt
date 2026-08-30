package my.id.rakyzumusic.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import my.id.rakyzumusic.core.data.catalog.CatalogRefreshFailure
import my.id.rakyzumusic.core.data.catalog.CatalogRefreshResult
import my.id.rakyzumusic.core.data.catalog.CatalogRepository
import my.id.rakyzumusic.core.data.network.ConnectivityMonitor
import my.id.rakyzumusic.core.model.Album
import my.id.rakyzumusic.core.model.CatalogSnapshot
import my.id.rakyzumusic.core.model.HomeFeedSnapshot
import my.id.rakyzumusic.core.model.Track

data class HomeUiState(
    val catalog: CatalogSnapshot = EMPTY_CATALOG,
    val recentlyPlayed: List<Track> = emptyList(),
    val derivedSections: HomeDerivedSections = catalog.toHomeDerivedSections(),
    val catalogFreshness: CatalogFreshness = CatalogFreshness(),
    val isRefreshing: Boolean = true,
    val isWaitingForConnection: Boolean = false,
    val refreshMessage: String? = null,
) {
    val hasPlayableContent: Boolean
        get() = catalog.tracks.isNotEmpty()

    val isShowingSavedCatalog: Boolean
        get() = refreshMessage != null && hasPlayableContent

    val isShowingStaleSavedCatalog: Boolean
        get() = isShowingSavedCatalog && catalogFreshness.isStale

    val isEmptyAfterRefresh: Boolean
        get() = !isRefreshing && refreshMessage == null && !hasPlayableContent
}

data class HomeDerivedSections(
    val featuredQueue: List<Track> = emptyList(),
    val newReleaseTracks: List<Track> = emptyList(),
) {
    val featuredTrack: Track?
        get() = featuredQueue.firstOrNull()
}

data class CatalogFreshness(
    val ageMinutes: Long? = null,
) {
    val isStale: Boolean
        get() = ageMinutes != null && ageMinutes >= STALE_AFTER_MINUTES

    val label: String
        get() = when (val age = ageMinutes) {
            null -> "Update time unavailable"
            0L -> "Updated just now"
            1L -> "Updated 1 minute ago"
            in 2L..<MINUTES_PER_HOUR -> "Updated $age minutes ago"
            in MINUTES_PER_HOUR..<(2L * MINUTES_PER_HOUR) -> "Updated 1 hour ago"
            in (2L * MINUTES_PER_HOUR)..<MINUTES_PER_DAY -> {
                "Updated ${age / MINUTES_PER_HOUR} hours ago"
            }
            in MINUTES_PER_DAY..<(2L * MINUTES_PER_DAY) -> "Updated 1 day ago"
            else -> "Updated ${age / MINUTES_PER_DAY} days ago"
        }

    private companion object {
        const val MINUTES_PER_HOUR = 60L
        const val MINUTES_PER_DAY = 24L * MINUTES_PER_HOUR
        const val STALE_AFTER_MINUTES = MINUTES_PER_DAY
    }
}

class HomeViewModel(
    private val userId: String,
    private val repository: CatalogRepository,
    private val connectivityMonitor: ConnectivityMonitor,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val retryDelaysMillis: List<Long> = DEFAULT_RETRY_DELAYS_MILLIS,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = mutableUiState.asStateFlow()
    private var refreshInProgress = false
    private var isOnline = connectivityMonitor.isCurrentlyOnline()
    private var waitingForConnectivity = false
    private var lastRefreshFailure: CatalogRefreshFailure? = null

    init {
        require(retryDelaysMillis.all { it >= 0L })
        viewModelScope.launch {
            repository.observeHomeFeed(userId).collect { feed ->
                mutableUiState.update {
                    it.withHomeFeed(
                        feed = feed,
                        currentTimeMillis = currentTimeMillis(),
                    )
                }
            }
        }
        viewModelScope.launch {
            connectivityMonitor.isOnline.collect { connectivityAvailable ->
                isOnline = connectivityAvailable
                if (
                    !connectivityAvailable &&
                    lastRefreshFailure == CatalogRefreshFailure.NetworkUnavailable &&
                    !refreshInProgress
                ) {
                    waitingForConnectivity = true
                    mutableUiState.update {
                        it.copy(
                            isWaitingForConnection = true,
                            refreshMessage = CatalogRefreshFailure.NetworkUnavailable.toSafeMessage(
                                isWaitingForConnection = true,
                            ),
                        )
                    }
                }
                if (connectivityAvailable && waitingForConnectivity) {
                    startRefresh()
                }
            }
        }
        refresh()
    }

    fun refresh() = startRefresh()

    private fun startRefresh() {
        if (refreshInProgress) return
        refreshInProgress = true
        waitingForConnectivity = false
        lastRefreshFailure = null
        mutableUiState.update {
            it.copy(
                isRefreshing = true,
                isWaitingForConnection = false,
                refreshMessage = null,
            )
        }
        viewModelScope.launch {
            var recoverAfterCompletion = false
            try {
                when (val result = refreshWithRetry()) {
                    is CatalogRefreshResult.Success -> mutableUiState.update {
                        it.copy(
                            catalogFreshness = result.syncedAtEpochMillis.toCatalogFreshness(
                                currentTimeMillis = currentTimeMillis(),
                            ),
                            isRefreshing = false,
                            isWaitingForConnection = false,
                            refreshMessage = null,
                        )
                    }
                    is CatalogRefreshResult.Failure -> {
                        lastRefreshFailure = result.reason
                        waitingForConnectivity =
                            result.reason == CatalogRefreshFailure.NetworkUnavailable && !isOnline
                        recoverAfterCompletion = waitingForConnectivity
                        mutableUiState.update {
                            it.copy(
                                catalogFreshness = it.catalog.lastSyncedAtEpochMillis.toCatalogFreshness(
                                    currentTimeMillis = currentTimeMillis(),
                                ),
                                isRefreshing = false,
                                isWaitingForConnection = waitingForConnectivity,
                                refreshMessage = result.reason.toSafeMessage(
                                    isWaitingForConnection = waitingForConnectivity,
                                ),
                            )
                        }
                    }
                }
            } finally {
                refreshInProgress = false
            }
            if (recoverAfterCompletion && isOnline) {
                startRefresh()
            }
        }
    }

    private suspend fun refreshWithRetry(): CatalogRefreshResult {
        var retryIndex = 0
        while (true) {
            val result = repository.refresh()
            if (result is CatalogRefreshResult.Success) return result
            val failure = result as CatalogRefreshResult.Failure
            val retryDelayMillis = if (
                isOnline && failure.reason != CatalogRefreshFailure.InvalidPayload
            ) {
                retryDelaysMillis.getOrNull(retryIndex)
            } else {
                null
            } ?: return failure

            delay(retryDelayMillis)
            if (!isOnline) {
                return CatalogRefreshResult.Failure(CatalogRefreshFailure.NetworkUnavailable)
            }
            retryIndex += 1
        }
    }

    companion object {
        fun factory(
            userId: String,
            repository: CatalogRepository,
            connectivityMonitor: ConnectivityMonitor,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(HomeViewModel::class.java))
                    return HomeViewModel(userId, repository, connectivityMonitor) as T
                }
            }
    }
}

internal fun HomeUiState.withHomeFeed(
    feed: HomeFeedSnapshot,
    currentTimeMillis: Long,
): HomeUiState {
    val stableCatalog = catalog.reuseWhenEqual(feed.catalog)
    val stableRecentlyPlayed = recentlyPlayed.reuseWhenEqual(feed.recentlyPlayed)
    return copy(
        catalog = stableCatalog,
        recentlyPlayed = stableRecentlyPlayed,
        derivedSections = if (stableCatalog === catalog) {
            derivedSections
        } else {
            stableCatalog.toHomeDerivedSections()
        },
        catalogFreshness = stableCatalog.lastSyncedAtEpochMillis.toCatalogFreshness(
            currentTimeMillis = currentTimeMillis,
        ),
    )
}

internal fun CatalogSnapshot.toHomeDerivedSections(): HomeDerivedSections {
    val featuredQueue = editorialShelves
        .firstOrNull()
        ?.tracks
        ?.takeIf(List<Track>::isNotEmpty)
        ?: tracks
    if (albums.isEmpty() || tracks.isEmpty()) {
        return HomeDerivedSections(featuredQueue = featuredQueue)
    }

    val tracksByAlbum = tracks.groupBy(Track::albumId)
    val newReleaseTracks = buildList(tracks.size) {
        albums.sortedWith(NEW_RELEASE_ALBUM_ORDER).forEach { album ->
            addAll(tracksByAlbum[album.id].orEmpty())
        }
    }
    return HomeDerivedSections(
        featuredQueue = featuredQueue,
        newReleaseTracks = newReleaseTracks,
    )
}

private fun <T> T.reuseWhenEqual(candidate: T): T = if (this == candidate) this else candidate

internal fun Long?.toCatalogFreshness(currentTimeMillis: Long): CatalogFreshness {
    if (this == null) return CatalogFreshness()
    val ageMillis = (currentTimeMillis - this).coerceAtLeast(0L)
    return CatalogFreshness(ageMinutes = ageMillis / MILLIS_PER_MINUTE)
}

private fun CatalogRefreshFailure.toSafeMessage(isWaitingForConnection: Boolean): String = when (this) {
    CatalogRefreshFailure.NetworkUnavailable -> if (isWaitingForConnection) {
        "You're offline. Rakyzu Music will retry when your connection returns."
    } else {
        "The catalog could not be refreshed after retrying. Check your connection and try again."
    }
    CatalogRefreshFailure.ServiceUnavailable ->
        "The Rakyzu catalog is temporarily unavailable after retrying."
    CatalogRefreshFailure.InvalidPayload -> "The latest catalog update could not be verified."
}

private val EMPTY_CATALOG = CatalogSnapshot(
    artists = emptyList(),
    albums = emptyList(),
    tracks = emptyList(),
    lastSyncedAtEpochMillis = null,
)

private const val MILLIS_PER_MINUTE = 60_000L

private val DEFAULT_RETRY_DELAYS_MILLIS = listOf(1_000L, 3_000L)

private val NEW_RELEASE_ALBUM_ORDER = compareByDescending<Album> { it.releaseDate }
