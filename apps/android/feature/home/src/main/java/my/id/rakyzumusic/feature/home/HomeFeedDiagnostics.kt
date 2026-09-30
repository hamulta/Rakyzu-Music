package my.id.rakyzumusic.feature.home

import android.util.Log
import my.id.rakyzumusic.core.data.catalog.CatalogRefreshFailure
import my.id.rakyzumusic.core.model.HomeFeedSnapshot

internal enum class HomeContentCount {
    None,
    One,
    Few,
    Some,
    Many;

    companion object {
        fun from(size: Int): HomeContentCount = when (size.coerceAtLeast(0)) {
            0 -> None
            1 -> One
            in 2..10 -> Few
            in 11..50 -> Some
            else -> Many
        }
    }
}

internal data class HomeFeedShape(
    val artists: HomeContentCount,
    val albums: HomeContentCount,
    val tracks: HomeContentCount,
    val shelves: HomeContentCount,
    val recentlyPlayed: HomeContentCount,
    val smartRecommendations: HomeContentCount,
) {
    companion object {
        fun from(feed: HomeFeedSnapshot): HomeFeedShape = HomeFeedShape(
            artists = HomeContentCount.from(feed.catalog.artists.size),
            albums = HomeContentCount.from(feed.catalog.albums.size),
            tracks = HomeContentCount.from(feed.catalog.tracks.size),
            shelves = HomeContentCount.from(feed.catalog.editorialShelves.size),
            recentlyPlayed = HomeContentCount.from(feed.recentlyPlayed.size),
            smartRecommendations = HomeContentCount.from(feed.smartRecommendations.size),
        )
    }
}

internal enum class HomeRefreshTrigger {
    Initial,
    Manual,
    ConnectivityRecovery,
}

internal enum class HomeRefreshAttempt {
    First,
    RetryOne,
    RetryTwo,
    RetryThreeOrMore;

    companion object {
        fun from(attemptNumber: Int): HomeRefreshAttempt = when (attemptNumber.coerceAtLeast(1)) {
            1 -> First
            2 -> RetryOne
            3 -> RetryTwo
            else -> RetryThreeOrMore
        }
    }
}

internal enum class HomeRefreshDuration {
    Under250Millis,
    UnderOneSecond,
    UnderFiveSeconds,
    UnderFifteenSeconds,
    FifteenSecondsOrMore;

    companion object {
        fun from(durationMillis: Long): HomeRefreshDuration = when (
            durationMillis.coerceAtLeast(0L)
        ) {
            in 0L..<250L -> Under250Millis
            in 250L..<1_000L -> UnderOneSecond
            in 1_000L..<5_000L -> UnderFiveSeconds
            in 5_000L..<15_000L -> UnderFifteenSeconds
            else -> FifteenSecondsOrMore
        }
    }
}

internal enum class HomeRetryDelay {
    Immediate,
    OneSecond,
    TwoToFourSeconds,
    FiveToFourteenSeconds,
    FifteenSecondsOrMore;

    companion object {
        fun from(delayMillis: Long): HomeRetryDelay = when (delayMillis.coerceAtLeast(0L)) {
            in 0L..<1_000L -> Immediate
            in 1_000L..<2_000L -> OneSecond
            in 2_000L..<5_000L -> TwoToFourSeconds
            in 5_000L..<15_000L -> FiveToFourteenSeconds
            else -> FifteenSecondsOrMore
        }
    }
}

internal sealed interface HomeFeedDiagnosticEvent {
    data class FeedObserved(
        val shape: HomeFeedShape,
    ) : HomeFeedDiagnosticEvent

    data class ConnectivityObserved(
        val isOnline: Boolean,
        val triggersRecovery: Boolean,
    ) : HomeFeedDiagnosticEvent

    data class RefreshStarted(
        val trigger: HomeRefreshTrigger,
        val isOnline: Boolean,
    ) : HomeFeedDiagnosticEvent

    data class RefreshCoalesced(
        val trigger: HomeRefreshTrigger,
    ) : HomeFeedDiagnosticEvent

    data class RetryScheduled(
        val trigger: HomeRefreshTrigger,
        val nextAttempt: HomeRefreshAttempt,
        val delay: HomeRetryDelay,
        val failure: CatalogRefreshFailure,
    ) : HomeFeedDiagnosticEvent

    data class RefreshSucceeded(
        val trigger: HomeRefreshTrigger,
        val attempts: HomeRefreshAttempt,
        val duration: HomeRefreshDuration,
    ) : HomeFeedDiagnosticEvent

    data class RefreshFailed(
        val trigger: HomeRefreshTrigger,
        val attempts: HomeRefreshAttempt,
        val duration: HomeRefreshDuration,
        val failure: CatalogRefreshFailure,
        val waitingForConnection: Boolean,
    ) : HomeFeedDiagnosticEvent
}

internal fun interface HomeFeedDiagnosticSink {
    fun record(event: HomeFeedDiagnosticEvent)
}

internal object NoOpHomeFeedDiagnosticSink : HomeFeedDiagnosticSink {
    override fun record(event: HomeFeedDiagnosticEvent) = Unit
}

internal class BoundedHomeFeedDiagnosticSink(
    private val capacity: Int,
    private val logger: (String) -> Unit,
) : HomeFeedDiagnosticSink {
    private val lock = Any()
    private val events = ArrayDeque<HomeFeedDiagnosticEvent>(capacity)

    init {
        require(capacity in 1..MAX_DIAGNOSTIC_EVENTS)
    }

    override fun record(event: HomeFeedDiagnosticEvent) {
        synchronized(lock) {
            if (events.size == capacity) events.removeFirst()
            events.addLast(event)
        }
        try {
            logger(event.toBoundedLogLine())
        } catch (_: RuntimeException) {
            // Log transport failure must not affect the product flow.
        }
    }

    fun snapshot(): List<HomeFeedDiagnosticEvent> = synchronized(lock) {
        events.toList()
    }
}

internal object AndroidHomeFeedDiagnostics {
    val sink: HomeFeedDiagnosticSink by lazy {
        BoundedHomeFeedDiagnosticSink(capacity = DEFAULT_DIAGNOSTIC_EVENTS) { message ->
            Log.d(LOG_TAG, message)
        }
    }
}

internal fun HomeFeedDiagnosticEvent.toBoundedLogLine(): String {
    val line = when (this) {
        is HomeFeedDiagnosticEvent.FeedObserved ->
            "event=feed_observed artists=${shape.artists.wireName()} " +
                "albums=${shape.albums.wireName()} tracks=${shape.tracks.wireName()} " +
                "shelves=${shape.shelves.wireName()} recent=${shape.recentlyPlayed.wireName()}"
        is HomeFeedDiagnosticEvent.ConnectivityObserved ->
            "event=connectivity_observed online=$isOnline recovery=$triggersRecovery"
        is HomeFeedDiagnosticEvent.RefreshStarted ->
            "event=refresh_started trigger=${trigger.wireName()} online=$isOnline"
        is HomeFeedDiagnosticEvent.RefreshCoalesced ->
            "event=refresh_coalesced trigger=${trigger.wireName()}"
        is HomeFeedDiagnosticEvent.RetryScheduled ->
            "event=retry_scheduled trigger=${trigger.wireName()} " +
                "attempt=${nextAttempt.wireName()} delay=${delay.wireName()} " +
                "failure=${failure.wireName()}"
        is HomeFeedDiagnosticEvent.RefreshSucceeded ->
            "event=refresh_succeeded trigger=${trigger.wireName()} " +
                "attempts=${attempts.wireName()} duration=${duration.wireName()}"
        is HomeFeedDiagnosticEvent.RefreshFailed ->
            "event=refresh_failed trigger=${trigger.wireName()} " +
                "attempts=${attempts.wireName()} duration=${duration.wireName()} " +
                "failure=${failure.wireName()} waiting=$waitingForConnection"
    }
    return line.take(MAX_LOG_LINE_CHARACTERS)
}

private fun Enum<*>.wireName(): String = name
    .replace(UPPERCASE_BOUNDARY, "_$1")
    .lowercase()

private const val DEFAULT_DIAGNOSTIC_EVENTS = 32
private const val MAX_DIAGNOSTIC_EVENTS = 64
private const val MAX_LOG_LINE_CHARACTERS = 240
private const val LOG_TAG = "RakyzuHomeFeed"
private val UPPERCASE_BOUNDARY = Regex("(?<!^)([A-Z])")
