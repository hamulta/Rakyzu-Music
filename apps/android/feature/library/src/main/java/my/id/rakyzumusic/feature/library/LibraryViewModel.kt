package my.id.rakyzumusic.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import my.id.rakyzumusic.core.data.library.LibraryActionResult
import my.id.rakyzumusic.core.data.library.LibraryFailure
import my.id.rakyzumusic.core.data.library.LibraryRepository
import my.id.rakyzumusic.core.data.network.ConnectivityMonitor
import my.id.rakyzumusic.core.model.Artist
import my.id.rakyzumusic.core.model.LibraryAlbum
import my.id.rakyzumusic.core.model.LibraryItemKind
import my.id.rakyzumusic.core.model.LibrarySnapshot
import my.id.rakyzumusic.core.model.Track

enum class LibraryFilter(val label: String) {
    All("All"),
    Songs("Liked Songs"),
    Albums("Albums"),
    Artists("Artists"),
}

enum class LibrarySort(val label: String) {
    RecentlyAdded("Recently added"),
    OldestAdded("Oldest added"),
    Alphabetical("A–Z"),
}

data class LibraryFreshness(
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
            in (2L * MINUTES_PER_HOUR)..<MINUTES_PER_DAY ->
                "Updated ${age / MINUTES_PER_HOUR} hours ago"
            in MINUTES_PER_DAY..<(2L * MINUTES_PER_DAY) -> "Updated 1 day ago"
            else -> "Updated ${age / MINUTES_PER_DAY} days ago"
        }

    private companion object {
        const val MINUTES_PER_HOUR = 60L
        const val MINUTES_PER_DAY = 24L * MINUTES_PER_HOUR
        const val STALE_AFTER_MINUTES = MINUTES_PER_DAY
    }
}

internal data class LibraryVisibleContent(
    val likedTracks: List<Track>,
    val savedAlbums: List<LibraryAlbum>,
    val followedArtists: List<Artist>,
) {
    val hasItems: Boolean
        get() = likedTracks.isNotEmpty() || savedAlbums.isNotEmpty() || followedArtists.isNotEmpty()
}

data class LibraryUiState(
    val hasObservedLibrary: Boolean = false,
    val library: LibrarySnapshot = EMPTY_LIBRARY,
    val query: String = "",
    val filter: LibraryFilter = LibraryFilter.All,
    val sort: LibrarySort = LibrarySort.RecentlyAdded,
    val freshness: LibraryFreshness = LibraryFreshness(),
    val isRefreshing: Boolean = false,
    val isOnline: Boolean = true,
    val isWaitingForConnection: Boolean = false,
    val pendingItems: Set<String> = emptySet(),
    val message: String? = null,
    val messageIsError: Boolean = false,
) {
    val likedTrackIds: Set<String>
        get() = library.likedTracks.mapTo(mutableSetOf()) { it.id }

    val savedAlbumIds: Set<String>
        get() = library.savedAlbums.mapTo(mutableSetOf()) { it.album.id }

    val followedArtistIds: Set<String>
        get() = library.followedArtists.mapTo(mutableSetOf()) { it.id }

    val pendingTrackIds: Set<String>
        get() = pendingItems.filterItemIds(LibraryItemKind.Track)

    val pendingAlbumIds: Set<String>
        get() = pendingItems.filterItemIds(LibraryItemKind.Album)

    val pendingArtistIds: Set<String>
        get() = pendingItems.filterItemIds(LibraryItemKind.Artist)

    val visibleLikedTracks: List<Track>
        get() = deriveLibraryVisibleContent(library, query, filter, sort).likedTracks

    val visibleSavedAlbums: List<LibraryAlbum>
        get() = deriveLibraryVisibleContent(library, query, filter, sort).savedAlbums

    val visibleFollowedArtists: List<Artist>
        get() = deriveLibraryVisibleContent(library, query, filter, sort).followedArtists

    val hasVisibleItems: Boolean
        get() = deriveLibraryVisibleContent(library, query, filter, sort).hasItems

    val hasNoMatchingItems: Boolean
        get() = hasObservedLibrary && !library.isEmpty && !hasVisibleItems

    val isShowingStaleSavedLibrary: Boolean
        get() = message != null && (messageIsError || isWaitingForConnection) &&
            !library.isEmpty && freshness.isStale

    fun isPending(kind: LibraryItemKind, itemId: String): Boolean =
        itemKey(kind, itemId) in pendingItems
}

class LibraryViewModel internal constructor(
    private val userId: String,
    private val repository: LibraryRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val connectivityMonitor: ConnectivityMonitor = AlwaysOnlineLibraryConnectivityMonitor,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val retryDelaysMillis: List<Long> = DEFAULT_RETRY_DELAYS_MILLIS,
    private val diagnostics: LibraryDiagnosticSink = NoOpLibraryDiagnosticSink,
    private val elapsedRealtimeMillis: () -> Long = {
        System.nanoTime() / NANOS_PER_MILLISECOND
    },
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        LibraryUiState(
            query = savedStateHandle.get<String>(SAVED_LIBRARY_QUERY_KEY)
                .orEmpty()
                .take(MAX_LIBRARY_QUERY_LENGTH),
            filter = enumValueOrDefault(
                savedStateHandle.get<String>(SAVED_LIBRARY_FILTER_KEY),
                LibraryFilter.All,
            ),
            sort = enumValueOrDefault(
                savedStateHandle.get<String>(SAVED_LIBRARY_SORT_KEY),
                LibrarySort.RecentlyAdded,
            ),
            isOnline = connectivityMonitor.isCurrentlyOnline(),
        ),
    )
    val uiState: StateFlow<LibraryUiState> = mutableUiState.asStateFlow()
    private var refreshInProgress = false
    private var isOnline = connectivityMonitor.isCurrentlyOnline()
    private var waitingForConnectivity = false
    private var lastRefreshFailure: LibraryFailure? = null

    init {
        require(retryDelaysMillis.all { it >= 0L })
        viewModelScope.launch {
            repository.observeLibrary(userId).collect { library ->
                recordDiagnostic(
                    LibraryDiagnosticEvent.SnapshotObserved(
                        shape = LibraryShape.from(library),
                        hasSyncTime = library.lastSyncedAtEpochMillis != null,
                    ),
                )
                mutableUiState.update {
                    it.copy(
                        hasObservedLibrary = true,
                        library = it.library.reuseWhenEqual(library),
                        freshness = library.lastSyncedAtEpochMillis.toLibraryFreshness(
                            currentTimeMillis(),
                        ),
                    )
                }
                if (library.pendingMutationCount > 0 && !isOnline && !refreshInProgress) {
                    waitingForConnectivity = true
                    mutableUiState.update {
                        it.copy(
                            isWaitingForConnection = true,
                            message = LibraryFailure.NetworkUnavailable.toSafeMessage(true),
                            messageIsError = false,
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            connectivityMonitor.isOnline.collect { connectivityAvailable ->
                val triggersRecovery = connectivityAvailable && waitingForConnectivity
                recordDiagnostic(
                    LibraryDiagnosticEvent.ConnectivityObserved(
                        isOnline = connectivityAvailable,
                        triggersRecovery = triggersRecovery,
                    ),
                )
                isOnline = connectivityAvailable
                mutableUiState.update { it.copy(isOnline = connectivityAvailable) }
                if (
                    !connectivityAvailable &&
                    lastRefreshFailure == LibraryFailure.NetworkUnavailable &&
                    !refreshInProgress
                ) {
                    waitingForConnectivity = true
                    mutableUiState.update {
                        it.copy(
                            isWaitingForConnection = true,
                            message = LibraryFailure.NetworkUnavailable.toSafeMessage(true),
                            messageIsError = false,
                        )
                    }
                }
                if (triggersRecovery) startRefresh(LibraryRefreshTrigger.ConnectivityRecovery)
            }
        }
        startRefresh(LibraryRefreshTrigger.Initial)
    }

    fun updateQuery(value: String) {
        val bounded = value.take(MAX_LIBRARY_QUERY_LENGTH)
        savedStateHandle[SAVED_LIBRARY_QUERY_KEY] = bounded
        mutableUiState.update { it.copy(query = bounded) }
    }

    fun clearQuery() = updateQuery("")

    fun selectFilter(filter: LibraryFilter) {
        savedStateHandle[SAVED_LIBRARY_FILTER_KEY] = filter.name
        mutableUiState.update { it.copy(filter = filter) }
    }

    fun selectSort(sort: LibrarySort) {
        savedStateHandle[SAVED_LIBRARY_SORT_KEY] = sort.name
        mutableUiState.update { it.copy(sort = sort) }
    }

    fun refresh() = startRefresh(LibraryRefreshTrigger.Manual)

    private fun startRefresh(trigger: LibraryRefreshTrigger) {
        if (refreshInProgress) {
            recordDiagnostic(LibraryDiagnosticEvent.RefreshCoalesced(trigger))
            return
        }
        refreshInProgress = true
        waitingForConnectivity = false
        lastRefreshFailure = null
        val startedAtMillis = elapsedRealtimeMillis()
        recordDiagnostic(
            LibraryDiagnosticEvent.RefreshStarted(trigger = trigger, isOnline = isOnline),
        )
        mutableUiState.update {
            it.copy(
                isRefreshing = true,
                isWaitingForConnection = false,
                message = null,
                messageIsError = false,
            )
        }
        viewModelScope.launch {
            var recoverAfterCompletion = false
            try {
                val execution = refreshWithRetry(trigger)
                val duration = LibraryRefreshDuration.from(
                    elapsedRealtimeMillis() - startedAtMillis,
                )
                when (val result = execution.result) {
                    LibraryActionResult.Success -> {
                        recordDiagnostic(
                            LibraryDiagnosticEvent.RefreshSucceeded(
                                trigger,
                                LibraryRefreshAttempt.from(execution.attemptCount),
                                duration,
                            ),
                        )
                        mutableUiState.update {
                            it.copy(
                                isRefreshing = false,
                                isWaitingForConnection = false,
                                message = null,
                                messageIsError = false,
                            )
                        }
                    }
                    is LibraryActionResult.Failure -> {
                        lastRefreshFailure = result.reason
                        waitingForConnectivity =
                            result.reason == LibraryFailure.NetworkUnavailable && !isOnline
                        recoverAfterCompletion = waitingForConnectivity
                        recordDiagnostic(
                            LibraryDiagnosticEvent.RefreshFailed(
                                trigger,
                                LibraryRefreshAttempt.from(execution.attemptCount),
                                duration,
                                result.reason,
                                waitingForConnectivity,
                            ),
                        )
                        mutableUiState.update {
                            it.copy(
                                isRefreshing = false,
                                isWaitingForConnection = waitingForConnectivity,
                                message = result.reason.toSafeMessage(waitingForConnectivity),
                                messageIsError = !waitingForConnectivity,
                            )
                        }
                    }
                    is LibraryActionResult.Queued -> {
                        // Refresh does not currently produce queued results; handle defensively.
                        mutableUiState.update {
                            it.copy(
                                isRefreshing = false,
                                message = result.queuedMessage(),
                                messageIsError = false,
                            )
                        }
                    }
                }
            } finally {
                refreshInProgress = false
            }
            if (recoverAfterCompletion && isOnline) {
                startRefresh(LibraryRefreshTrigger.ConnectivityRecovery)
            }
        }
    }

    private suspend fun refreshWithRetry(
        trigger: LibraryRefreshTrigger,
    ): LibraryRefreshExecution {
        var retryIndex = 0
        var attemptCount = 0
        while (true) {
            attemptCount += 1
            val result = repository.refresh(userId)
            if (result !is LibraryActionResult.Failure) {
                return LibraryRefreshExecution(result, attemptCount)
            }
            val retryDelayMillis = if (isOnline && result.reason.isTransient) {
                retryDelaysMillis.getOrNull(retryIndex)
            } else {
                null
            } ?: return LibraryRefreshExecution(result, attemptCount)

            recordDiagnostic(
                LibraryDiagnosticEvent.RetryScheduled(
                    trigger,
                    LibraryRefreshAttempt.from(attemptCount + 1),
                    LibraryRetryDelay.from(retryDelayMillis),
                    result.reason,
                ),
            )
            delay(retryDelayMillis)
            if (!isOnline) {
                return LibraryRefreshExecution(
                    LibraryActionResult.Failure(LibraryFailure.NetworkUnavailable),
                    attemptCount,
                )
            }
            retryIndex += 1
        }
    }

    fun setSaved(kind: LibraryItemKind, itemId: String, saved: Boolean) {
        val key = itemKey(kind, itemId)
        if (itemId.isBlank() || key in mutableUiState.value.pendingItems) return
        mutableUiState.update {
            it.copy(pendingItems = it.pendingItems + key, message = null)
        }
        viewModelScope.launch {
            val result = repository.setSaved(userId, kind, itemId, saved)
            if (result is LibraryActionResult.Queued && !isOnline) {
                waitingForConnectivity = true
            }
            recordDiagnostic(
                LibraryDiagnosticEvent.MutationCompleted(
                    kind = kind,
                    desiredSaved = saved,
                    outcome = result.toDiagnosticOutcome(),
                    pendingMutations = LibraryContentCount.from(
                        (result as? LibraryActionResult.Queued)?.pendingMutationCount ?: 0,
                    ),
                ),
            )
            mutableUiState.update {
                it.copy(
                    pendingItems = it.pendingItems - key,
                    message = when (result) {
                        LibraryActionResult.Success -> kind.successMessage(saved)
                        is LibraryActionResult.Queued -> result.queuedMessage()
                        is LibraryActionResult.Failure -> result.failureMessage()
                    },
                    messageIsError = result is LibraryActionResult.Failure,
                    isWaitingForConnection = waitingForConnectivity,
                )
            }
        }
    }

    private fun recordDiagnostic(event: LibraryDiagnosticEvent) {
        try {
            diagnostics.record(event)
        } catch (_: RuntimeException) {
            // Diagnostics must never alter Library behavior.
        }
    }

    companion object {
        fun factory(
            userId: String,
            repository: LibraryRepository,
            connectivityMonitor: ConnectivityMonitor,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(LibraryViewModel::class.java))
                    return LibraryViewModel(
                        userId,
                        repository,
                        connectivityMonitor = connectivityMonitor,
                        diagnostics = AndroidLibraryDiagnostics.sink,
                    ) as T
                }

                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras,
                ): T {
                    require(modelClass.isAssignableFrom(LibraryViewModel::class.java))
                    return LibraryViewModel(
                        userId = userId,
                        repository = repository,
                        savedStateHandle = extras.createSavedStateHandle(),
                        connectivityMonitor = connectivityMonitor,
                        diagnostics = AndroidLibraryDiagnostics.sink,
                    ) as T
                }
            }
    }
}

private fun LibraryActionResult.statusMessage(): String? = when (this) {
    LibraryActionResult.Success -> null
    is LibraryActionResult.Queued -> queuedMessage()
    is LibraryActionResult.Failure -> when (reason) {
        LibraryFailure.NetworkUnavailable -> "Library sync needs an internet connection."
        LibraryFailure.ServiceUnavailable -> "Your Library is temporarily unavailable."
        LibraryFailure.InvalidRequest -> "That Library action is not valid."
        LibraryFailure.InvalidPayload -> "Library returned an invalid response."
    }
}

private fun LibraryActionResult.Failure.failureMessage(): String =
    (this as LibraryActionResult).statusMessage().orEmpty()

private val LibraryFailure.isTransient: Boolean
    get() = this == LibraryFailure.NetworkUnavailable || this == LibraryFailure.ServiceUnavailable

private fun LibraryActionResult.toDiagnosticOutcome(): LibraryMutationOutcome = when (this) {
    LibraryActionResult.Success -> LibraryMutationOutcome.Applied
    is LibraryActionResult.Queued -> LibraryMutationOutcome.Queued
    is LibraryActionResult.Failure -> LibraryMutationOutcome.Failed
}

private fun LibraryFailure.toSafeMessage(isWaitingForConnection: Boolean): String = when (this) {
    LibraryFailure.NetworkUnavailable -> if (isWaitingForConnection) {
        "You're offline. Rakyzu Music will sync your Library when your connection returns."
    } else {
        "Library could not be refreshed after retrying. Check your connection and try again."
    }
    LibraryFailure.ServiceUnavailable ->
        "Your Library is temporarily unavailable after retrying."
    LibraryFailure.InvalidRequest -> "That Library request is not valid."
    LibraryFailure.InvalidPayload -> "The latest Library update could not be verified."
}

private fun LibraryActionResult.Queued.queuedMessage(): String =
    if (pendingMutationCount == 1) {
        "Saved offline. 1 Library change is waiting to sync."
    } else {
        "Saved offline. $pendingMutationCount Library changes are waiting to sync."
    }

private fun LibraryItemKind.successMessage(saved: Boolean): String = when (this) {
    LibraryItemKind.Track -> if (saved) "Added to Liked Songs." else "Removed from Liked Songs."
    LibraryItemKind.Album -> if (saved) "Album saved." else "Album removed."
    LibraryItemKind.Artist -> if (saved) "Artist followed." else "Artist unfollowed."
}

internal fun String.normalizedLibraryText(): String = Normalizer.normalize(
    trim().replace(WHITESPACE, " "),
    Normalizer.Form.NFD,
).replace(COMBINING_MARKS, "").lowercase(Locale.ROOT)

internal fun deriveLibraryVisibleContent(
    library: LibrarySnapshot,
    query: String,
    filter: LibraryFilter,
    sort: LibrarySort,
): LibraryVisibleContent {
    val queryTokens = query.normalizedLibraryText().split(' ').filter(String::isNotBlank)
    fun String.matchesTokens(): Boolean {
        if (queryTokens.isEmpty()) return true
        val value = normalizedLibraryText()
        return queryTokens.all(value::contains)
    }

    val tracks = if (filter == LibraryFilter.All || filter == LibraryFilter.Songs) {
        library.likedTracks
            .filter { listOf(it.title, it.artist, it.albumTitle).joinToString(" ").matchesTokens() }
            .sortedWith(library.trackComparator(sort))
    } else {
        emptyList()
    }
    val albums = if (filter == LibraryFilter.All || filter == LibraryFilter.Albums) {
        library.savedAlbums
            .filter { listOf(it.album.title, it.artistName).joinToString(" ").matchesTokens() }
            .sortedWith(library.albumComparator(sort))
    } else {
        emptyList()
    }
    val artists = if (filter == LibraryFilter.All || filter == LibraryFilter.Artists) {
        library.followedArtists
            .filter { it.name.matchesTokens() }
            .sortedWith(library.artistComparator(sort))
    } else {
        emptyList()
    }
    return LibraryVisibleContent(tracks, albums, artists)
}

internal fun Long?.toLibraryFreshness(currentTimeMillis: Long): LibraryFreshness {
    if (this == null) return LibraryFreshness()
    val ageMillis = (currentTimeMillis - this).coerceAtLeast(0L)
    return LibraryFreshness(ageMillis / MILLIS_PER_MINUTE)
}

private fun <T> T.reuseWhenEqual(candidate: T): T = if (this == candidate) this else candidate

private fun LibrarySnapshot.trackComparator(sort: LibrarySort): Comparator<Track> = when (sort) {
    LibrarySort.RecentlyAdded -> compareByDescending<Track> {
        likedTrackSavedAtEpochMillis[it.id] ?: Long.MIN_VALUE
    }.thenBy(Track::id)
    LibrarySort.OldestAdded -> compareBy<Track> {
        likedTrackSavedAtEpochMillis[it.id] ?: Long.MAX_VALUE
    }.thenBy(Track::id)
    LibrarySort.Alphabetical -> compareBy(String.CASE_INSENSITIVE_ORDER, Track::title)
        .thenBy(String.CASE_INSENSITIVE_ORDER, Track::artist)
        .thenBy(Track::id)
}

private fun LibrarySnapshot.albumComparator(sort: LibrarySort): Comparator<LibraryAlbum> =
    when (sort) {
        LibrarySort.RecentlyAdded -> compareByDescending<LibraryAlbum> {
            savedAlbumSavedAtEpochMillis[it.album.id] ?: Long.MIN_VALUE
        }.thenBy { it.album.id }
        LibrarySort.OldestAdded -> compareBy<LibraryAlbum> {
            savedAlbumSavedAtEpochMillis[it.album.id] ?: Long.MAX_VALUE
        }.thenBy { it.album.id }
        LibrarySort.Alphabetical -> compareBy<LibraryAlbum> {
            it.album.title.lowercase(Locale.ROOT)
        }
            .thenBy { it.artistName.lowercase(Locale.ROOT) }
            .thenBy { it.album.id }
    }

private fun LibrarySnapshot.artistComparator(sort: LibrarySort): Comparator<Artist> = when (sort) {
    LibrarySort.RecentlyAdded -> compareByDescending<Artist> {
        followedArtistSavedAtEpochMillis[it.id] ?: Long.MIN_VALUE
    }.thenBy(Artist::id)
    LibrarySort.OldestAdded -> compareBy<Artist> {
        followedArtistSavedAtEpochMillis[it.id] ?: Long.MAX_VALUE
    }.thenBy(Artist::id)
    LibrarySort.Alphabetical -> compareBy(String.CASE_INSENSITIVE_ORDER, Artist::name)
        .thenBy(Artist::id)
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: default

private fun itemKey(kind: LibraryItemKind, itemId: String) = "${kind.name}:$itemId"

private fun Set<String>.filterItemIds(kind: LibraryItemKind): Set<String> {
    val prefix = "${kind.name}:"
    return asSequence()
        .filter { it.startsWith(prefix) }
        .map { it.removePrefix(prefix) }
        .toSet()
}

internal const val SAVED_LIBRARY_QUERY_KEY = "library_query"
internal const val SAVED_LIBRARY_FILTER_KEY = "library_filter"
internal const val SAVED_LIBRARY_SORT_KEY = "library_sort"
internal const val MAX_LIBRARY_QUERY_LENGTH = 100

private val WHITESPACE = Regex("\\s+")
private val COMBINING_MARKS = Regex("\\p{M}+")

private val EMPTY_LIBRARY = LibrarySnapshot(
    likedTracks = emptyList(),
    savedAlbums = emptyList(),
    followedArtists = emptyList(),
    lastSyncedAtEpochMillis = null,
)

private object AlwaysOnlineLibraryConnectivityMonitor : ConnectivityMonitor {
    override val isOnline = flowOf(true)

    override fun isCurrentlyOnline() = true
}

private data class LibraryRefreshExecution(
    val result: LibraryActionResult,
    val attemptCount: Int,
)

private const val MILLIS_PER_MINUTE = 60_000L
private const val NANOS_PER_MILLISECOND = 1_000_000L
private val DEFAULT_RETRY_DELAYS_MILLIS = listOf(1_000L, 3_000L)
