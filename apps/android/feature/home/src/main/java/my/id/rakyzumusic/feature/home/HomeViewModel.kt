package my.id.rakyzumusic.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
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
import my.id.rakyzumusic.core.model.DiscoveryMode
import my.id.rakyzumusic.core.model.HomeFeedSnapshot
import my.id.rakyzumusic.core.model.ListeningHistoryItem
import my.id.rakyzumusic.core.model.PersonalizedCollection
import my.id.rakyzumusic.core.model.PersonalizedTrack
import my.id.rakyzumusic.core.model.TasteProfile
import my.id.rakyzumusic.core.model.Track

data class HomeUiState(
    val catalog: CatalogSnapshot = EMPTY_CATALOG,
    val recentlyPlayed: List<Track> = emptyList(),
    val listeningHistory: List<ListeningHistoryItem> = emptyList(),
    val recommendations: List<PersonalizedTrack> = emptyList(),
    val mixes: List<PersonalizedCollection> = emptyList(),
    val radioStations: List<PersonalizedCollection> = emptyList(),
    val tasteProfile: TasteProfile = TasteProfile(),
    val derivedSections: HomeDerivedSections = catalog.toHomeDerivedSections(),
    val catalogFreshness: CatalogFreshness = CatalogFreshness(),
    val isRefreshing: Boolean = true,
    val isWaitingForConnection: Boolean = false,
    val refreshMessage: String? = null,
    val personalizationMessage: String? = null,
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

class HomeViewModel internal constructor(
    private val userId: String,
    private val repository: CatalogRepository,
    private val connectivityMonitor: ConnectivityMonitor,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val retryDelaysMillis: List<Long> = DEFAULT_RETRY_DELAYS_MILLIS,
    private val diagnostics: HomeFeedDiagnosticSink = NoOpHomeFeedDiagnosticSink,
    private val elapsedRealtimeMillis: () -> Long = {
        System.nanoTime() / NANOS_PER_MILLISECOND
    },
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
                recordDiagnostic(
                    HomeFeedDiagnosticEvent.FeedObserved(HomeFeedShape.from(feed)),
                )
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
                val triggersRecovery = connectivityAvailable && waitingForConnectivity
                recordDiagnostic(
                    HomeFeedDiagnosticEvent.ConnectivityObserved(
                        isOnline = connectivityAvailable,
                        triggersRecovery = triggersRecovery,
                    ),
                )
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
                if (triggersRecovery) {
                    startRefresh(HomeRefreshTrigger.ConnectivityRecovery)
                }
            }
        }
        startRefresh(HomeRefreshTrigger.Initial)
    }

    fun refresh() = startRefresh(HomeRefreshTrigger.Manual)

    fun setPersonalizationEnabled(enabled: Boolean) = updatePersonalization(
        successMessage = if (enabled) "Private recommendations enabled" else "Private recommendations paused",
    ) { repository.setPersonalizationEnabled(userId, enabled) }

    fun setDiscoveryMode(mode: DiscoveryMode) = updatePersonalization(
        successMessage = "Discovery mode changed to ${mode.name.lowercase()}",
    ) { repository.setDiscoveryMode(userId, mode) }

    fun hideRecommendation(trackId: String) = updatePersonalization(
        successMessage = "Recommendation hidden",
    ) { repository.setRecommendationHidden(userId, trackId, true) }

    fun setTasteSignalExcluded(trackId: String, excluded: Boolean) = updatePersonalization(
        successMessage = if (excluded) {
            "This track will not shape your taste profile"
        } else {
            "This track can shape your taste profile again"
        },
    ) { repository.setTasteSignalExcluded(userId, trackId, excluded) }

    fun clearPersonalizationData() = updatePersonalization(
        successMessage = "Listening history and private recommendation controls cleared",
    ) { repository.clearPersonalizationData(userId) }

    fun clearPersonalizationMessage() {
        mutableUiState.update { it.copy(personalizationMessage = null) }
    }

    private fun updatePersonalization(
        successMessage: String,
        action: suspend () -> Boolean,
    ) {
        viewModelScope.launch {
            val succeeded = try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                false
            }
            mutableUiState.update {
                it.copy(
                    personalizationMessage = if (succeeded) successMessage else
                        "Personalization settings could not be updated",
                )
            }
        }
    }

    private fun startRefresh(trigger: HomeRefreshTrigger) {
        if (refreshInProgress) {
            recordDiagnostic(HomeFeedDiagnosticEvent.RefreshCoalesced(trigger))
            return
        }
        refreshInProgress = true
        waitingForConnectivity = false
        lastRefreshFailure = null
        val refreshStartedAtMillis = elapsedRealtimeMillis()
        recordDiagnostic(
            HomeFeedDiagnosticEvent.RefreshStarted(
                trigger = trigger,
                isOnline = isOnline,
            ),
        )
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
                val execution = refreshWithRetry(trigger)
                val duration = HomeRefreshDuration.from(
                    elapsedRealtimeMillis() - refreshStartedAtMillis,
                )
                when (val result = execution.result) {
                    is CatalogRefreshResult.Success -> {
                        recordDiagnostic(
                            HomeFeedDiagnosticEvent.RefreshSucceeded(
                                trigger = trigger,
                                attempts = HomeRefreshAttempt.from(execution.attemptCount),
                                duration = duration,
                            ),
                        )
                        mutableUiState.update {
                            it.copy(
                                catalogFreshness = result.syncedAtEpochMillis.toCatalogFreshness(
                                    currentTimeMillis = currentTimeMillis(),
                                ),
                                isRefreshing = false,
                                isWaitingForConnection = false,
                                refreshMessage = null,
                            )
                        }
                    }
                    is CatalogRefreshResult.Failure -> {
                        lastRefreshFailure = result.reason
                        waitingForConnectivity =
                            result.reason == CatalogRefreshFailure.NetworkUnavailable && !isOnline
                        recoverAfterCompletion = waitingForConnectivity
                        recordDiagnostic(
                            HomeFeedDiagnosticEvent.RefreshFailed(
                                trigger = trigger,
                                attempts = HomeRefreshAttempt.from(execution.attemptCount),
                                duration = duration,
                                failure = result.reason,
                                waitingForConnection = waitingForConnectivity,
                            ),
                        )
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
                startRefresh(HomeRefreshTrigger.ConnectivityRecovery)
            }
        }
    }

    private suspend fun refreshWithRetry(
        trigger: HomeRefreshTrigger,
    ): HomeRefreshExecution {
        var retryIndex = 0
        var attemptCount = 0
        while (true) {
            attemptCount += 1
            val result = repository.refresh()
            if (result is CatalogRefreshResult.Success) {
                return HomeRefreshExecution(result, attemptCount)
            }
            val failure = result as CatalogRefreshResult.Failure
            val retryDelayMillis = if (
                isOnline && failure.reason != CatalogRefreshFailure.InvalidPayload
            ) {
                retryDelaysMillis.getOrNull(retryIndex)
            } else {
                null
            } ?: return HomeRefreshExecution(failure, attemptCount)

            recordDiagnostic(
                HomeFeedDiagnosticEvent.RetryScheduled(
                    trigger = trigger,
                    nextAttempt = HomeRefreshAttempt.from(attemptCount + 1),
                    delay = HomeRetryDelay.from(retryDelayMillis),
                    failure = failure.reason,
                ),
            )
            delay(retryDelayMillis)
            if (!isOnline) {
                return HomeRefreshExecution(
                    result = CatalogRefreshResult.Failure(
                        CatalogRefreshFailure.NetworkUnavailable,
                    ),
                    attemptCount = attemptCount,
                )
            }
            retryIndex += 1
        }
    }

    private fun recordDiagnostic(event: HomeFeedDiagnosticEvent) {
        try {
            diagnostics.record(event)
        } catch (_: RuntimeException) {
            // Diagnostics must never alter Home feed behavior.
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
                    return HomeViewModel(
                        userId = userId,
                        repository = repository,
                        connectivityMonitor = connectivityMonitor,
                        diagnostics = AndroidHomeFeedDiagnostics.sink,
                    ) as T
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
    val stableListeningHistory = listeningHistory.reuseWhenEqual(feed.listeningHistory)
    val stableRecommendations = recommendations.reuseWhenEqual(feed.recommendations)
    val stableMixes = mixes.reuseWhenEqual(feed.mixes)
    val stableRadioStations = radioStations.reuseWhenEqual(feed.radioStations)
    return copy(
        catalog = stableCatalog,
        recentlyPlayed = stableRecentlyPlayed,
        listeningHistory = stableListeningHistory,
        recommendations = stableRecommendations,
        mixes = stableMixes,
        radioStations = stableRadioStations,
        tasteProfile = feed.tasteProfile,
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

private data class HomeRefreshExecution(
    val result: CatalogRefreshResult,
    val attemptCount: Int,
)

private const val NANOS_PER_MILLISECOND = 1_000_000L
