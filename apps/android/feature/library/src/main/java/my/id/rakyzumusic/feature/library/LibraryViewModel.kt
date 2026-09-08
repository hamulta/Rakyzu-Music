package my.id.rakyzumusic.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import my.id.rakyzumusic.core.data.library.LibraryActionResult
import my.id.rakyzumusic.core.data.library.LibraryFailure
import my.id.rakyzumusic.core.data.library.LibraryRepository
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

data class LibraryUiState(
    val hasObservedLibrary: Boolean = false,
    val library: LibrarySnapshot = EMPTY_LIBRARY,
    val query: String = "",
    val filter: LibraryFilter = LibraryFilter.All,
    val sort: LibrarySort = LibrarySort.RecentlyAdded,
    val isRefreshing: Boolean = false,
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
        get() = if (filter == LibraryFilter.All || filter == LibraryFilter.Songs) {
            library.likedTracks
                .filter { it.matchesLibraryQuery(query) }
                .sortedWith(library.trackComparator(sort))
        } else {
            emptyList()
        }

    val visibleSavedAlbums: List<LibraryAlbum>
        get() = if (filter == LibraryFilter.All || filter == LibraryFilter.Albums) {
            library.savedAlbums
                .filter { it.matchesLibraryQuery(query) }
                .sortedWith(library.albumComparator(sort))
        } else {
            emptyList()
        }

    val visibleFollowedArtists: List<Artist>
        get() = if (filter == LibraryFilter.All || filter == LibraryFilter.Artists) {
            library.followedArtists
                .filter { it.name.matchesLibraryQuery(query) }
                .sortedWith(library.artistComparator(sort))
        } else {
            emptyList()
        }

    val hasVisibleItems: Boolean
        get() = visibleLikedTracks.isNotEmpty() ||
            visibleSavedAlbums.isNotEmpty() || visibleFollowedArtists.isNotEmpty()

    val hasNoMatchingItems: Boolean
        get() = hasObservedLibrary && !library.isEmpty && !hasVisibleItems

    fun isPending(kind: LibraryItemKind, itemId: String): Boolean =
        itemKey(kind, itemId) in pendingItems
}

class LibraryViewModel internal constructor(
    private val userId: String,
    private val repository: LibraryRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
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
        ),
    )
    val uiState: StateFlow<LibraryUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeLibrary(userId).collect { library ->
                mutableUiState.update {
                    it.copy(hasObservedLibrary = true, library = library)
                }
            }
        }
        refresh()
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

    fun refresh() {
        if (mutableUiState.value.isRefreshing) return
        mutableUiState.update { it.copy(isRefreshing = true, message = null) }
        viewModelScope.launch {
            val result = repository.refresh(userId)
            mutableUiState.update {
                it.copy(
                    isRefreshing = false,
                    message = result.statusMessage(),
                    messageIsError = result is LibraryActionResult.Failure,
                )
            }
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
            mutableUiState.update {
                it.copy(
                    pendingItems = it.pendingItems - key,
                    message = when (result) {
                        LibraryActionResult.Success -> kind.successMessage(saved)
                        is LibraryActionResult.Queued -> result.queuedMessage()
                        is LibraryActionResult.Failure -> result.failureMessage()
                    },
                    messageIsError = result is LibraryActionResult.Failure,
                )
            }
        }
    }

    companion object {
        fun factory(userId: String, repository: LibraryRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(LibraryViewModel::class.java))
                    return LibraryViewModel(userId, repository) as T
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

private fun Track.matchesLibraryQuery(query: String): Boolean = listOf(
    title,
    artist,
    albumTitle,
).joinToString(" ").matchesLibraryQuery(query)

private fun LibraryAlbum.matchesLibraryQuery(query: String): Boolean = listOf(
    album.title,
    artistName,
).joinToString(" ").matchesLibraryQuery(query)

private fun String.matchesLibraryQuery(query: String): Boolean {
    val normalizedQuery = query.normalizedLibraryText()
    if (normalizedQuery.isBlank()) return true
    val normalizedValue = normalizedLibraryText()
    return normalizedQuery.split(' ').all(normalizedValue::contains)
}

internal fun String.normalizedLibraryText(): String = Normalizer.normalize(
    trim().replace(WHITESPACE, " "),
    Normalizer.Form.NFD,
).replace(COMBINING_MARKS, "").lowercase(Locale.ROOT)

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
