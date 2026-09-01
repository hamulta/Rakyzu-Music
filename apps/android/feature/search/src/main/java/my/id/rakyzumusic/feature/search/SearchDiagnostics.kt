package my.id.rakyzumusic.feature.search

import android.util.Log
import my.id.rakyzumusic.core.data.catalog.CatalogSearchFailure
import my.id.rakyzumusic.core.model.CatalogSnapshot

internal enum class SearchContentCount {
    None,
    One,
    Few,
    Some,
    Many;

    companion object {
        fun from(size: Int): SearchContentCount = when (size.coerceAtLeast(0)) {
            0 -> None
            1 -> One
            in 2..10 -> Few
            in 11..50 -> Some
            else -> Many
        }
    }
}

internal data class SearchCatalogShape(
    val artists: SearchContentCount,
    val albums: SearchContentCount,
    val tracks: SearchContentCount,
    val categories: SearchContentCount,
) {
    companion object {
        fun from(catalog: CatalogSnapshot, categoryCount: Int) = SearchCatalogShape(
            artists = SearchContentCount.from(catalog.artists.size),
            albums = SearchContentCount.from(catalog.albums.size),
            tracks = SearchContentCount.from(catalog.tracks.size),
            categories = SearchContentCount.from(categoryCount),
        )
    }
}

internal data class SearchResultShape(
    val artists: SearchContentCount,
    val albums: SearchContentCount,
    val tracks: SearchContentCount,
) {
    companion object {
        fun from(results: SearchResults) = SearchResultShape(
            artists = SearchContentCount.from(results.artists.size),
            albums = SearchContentCount.from(results.albums.size),
            tracks = SearchContentCount.from(results.tracks.size),
        )
    }
}

internal enum class SearchDuration {
    Under16Millis,
    Under50Millis,
    Under250Millis,
    UnderOneSecond,
    OneSecondOrMore;

    companion object {
        fun from(durationMillis: Long): SearchDuration = when (durationMillis.coerceAtLeast(0L)) {
            in 0L..<16L -> Under16Millis
            in 16L..<50L -> Under50Millis
            in 50L..<250L -> Under250Millis
            in 250L..<1_000L -> UnderOneSecond
            else -> OneSecondOrMore
        }
    }
}

internal enum class RemoteSearchTrigger {
    Submit,
    ManualRetry,
    ConnectivityRecovery,
    LoadMore,
}

internal enum class RemoteSearchPage {
    Initial,
    Additional,
}

internal sealed interface SearchDiagnosticEvent {
    data class CatalogObserved(
        val shape: SearchCatalogShape,
    ) : SearchDiagnosticEvent

    data class LocalSearchCompleted(
        val duration: SearchDuration,
        val results: SearchResultShape,
    ) : SearchDiagnosticEvent

    data class ConnectivityObserved(
        val isOnline: Boolean,
        val triggersRecovery: Boolean,
    ) : SearchDiagnosticEvent

    data class RemoteSearchStarted(
        val trigger: RemoteSearchTrigger,
        val page: RemoteSearchPage,
    ) : SearchDiagnosticEvent

    data class RemoteSearchSkippedOffline(
        val trigger: RemoteSearchTrigger,
        val page: RemoteSearchPage,
    ) : SearchDiagnosticEvent

    data class RemoteSearchSucceeded(
        val trigger: RemoteSearchTrigger,
        val page: RemoteSearchPage,
        val duration: SearchDuration,
        val results: SearchResultShape,
        val hasNextPage: Boolean,
    ) : SearchDiagnosticEvent

    data class RemoteSearchFailed(
        val trigger: RemoteSearchTrigger,
        val page: RemoteSearchPage,
        val duration: SearchDuration,
        val failure: CatalogSearchFailure,
    ) : SearchDiagnosticEvent
}

internal fun interface SearchDiagnosticSink {
    fun record(event: SearchDiagnosticEvent)
}

internal object NoOpSearchDiagnosticSink : SearchDiagnosticSink {
    override fun record(event: SearchDiagnosticEvent) = Unit
}

internal class BoundedSearchDiagnosticSink(
    private val capacity: Int,
    private val logger: (String) -> Unit,
) : SearchDiagnosticSink {
    private val lock = Any()
    private val events = ArrayDeque<SearchDiagnosticEvent>(capacity)

    init {
        require(capacity in 1..MAX_DIAGNOSTIC_EVENTS)
    }

    override fun record(event: SearchDiagnosticEvent) {
        synchronized(lock) {
            if (events.size == capacity) events.removeFirst()
            events.addLast(event)
        }
        try {
            logger(event.toBoundedLogLine())
        } catch (_: RuntimeException) {
            // Log transport failure must not affect Search behavior.
        }
    }

    fun snapshot(): List<SearchDiagnosticEvent> = synchronized(lock) {
        events.toList()
    }
}

internal object AndroidSearchDiagnostics {
    val sink: SearchDiagnosticSink by lazy {
        BoundedSearchDiagnosticSink(capacity = DEFAULT_DIAGNOSTIC_EVENTS) { message ->
            Log.d(LOG_TAG, message)
        }
    }
}

internal fun SearchDiagnosticEvent.toBoundedLogLine(): String {
    val line = when (this) {
        is SearchDiagnosticEvent.CatalogObserved ->
            "event=catalog_observed artists=${shape.artists.wireName()} " +
                "albums=${shape.albums.wireName()} tracks=${shape.tracks.wireName()} " +
                "categories=${shape.categories.wireName()}"
        is SearchDiagnosticEvent.LocalSearchCompleted ->
            "event=local_search_completed duration=${duration.wireName()} " +
                "artists=${results.artists.wireName()} albums=${results.albums.wireName()} " +
                "tracks=${results.tracks.wireName()}"
        is SearchDiagnosticEvent.ConnectivityObserved ->
            "event=connectivity_observed online=$isOnline recovery=$triggersRecovery"
        is SearchDiagnosticEvent.RemoteSearchStarted ->
            "event=remote_search_started trigger=${trigger.wireName()} page=${page.wireName()}"
        is SearchDiagnosticEvent.RemoteSearchSkippedOffline ->
            "event=remote_search_skipped_offline trigger=${trigger.wireName()} " +
                "page=${page.wireName()}"
        is SearchDiagnosticEvent.RemoteSearchSucceeded ->
            "event=remote_search_succeeded trigger=${trigger.wireName()} " +
                "page=${page.wireName()} duration=${duration.wireName()} " +
                "artists=${results.artists.wireName()} albums=${results.albums.wireName()} " +
                "tracks=${results.tracks.wireName()} next=$hasNextPage"
        is SearchDiagnosticEvent.RemoteSearchFailed ->
            "event=remote_search_failed trigger=${trigger.wireName()} " +
                "page=${page.wireName()} duration=${duration.wireName()} " +
                "failure=${failure.wireName()}"
    }
    return line.take(MAX_LOG_LINE_CHARACTERS)
}

private fun Enum<*>.wireName(): String = name
    .replace(UPPERCASE_BOUNDARY, "_$1")
    .lowercase()

private const val DEFAULT_DIAGNOSTIC_EVENTS = 32
private const val MAX_DIAGNOSTIC_EVENTS = 64
private const val MAX_LOG_LINE_CHARACTERS = 240
private const val LOG_TAG = "RakyzuSearch"
private val UPPERCASE_BOUNDARY = Regex("(?<!^)([A-Z])")
