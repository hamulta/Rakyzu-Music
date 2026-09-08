package my.id.rakyzumusic.feature.library

import android.util.Log
import my.id.rakyzumusic.core.data.library.LibraryFailure
import my.id.rakyzumusic.core.model.LibraryItemKind
import my.id.rakyzumusic.core.model.LibrarySnapshot

internal enum class LibraryContentCount {
    None,
    One,
    Few,
    Some,
    Many;

    companion object {
        fun from(size: Int): LibraryContentCount = when (size.coerceAtLeast(0)) {
            0 -> None
            1 -> One
            in 2..10 -> Few
            in 11..50 -> Some
            else -> Many
        }
    }
}

internal data class LibraryShape(
    val tracks: LibraryContentCount,
    val albums: LibraryContentCount,
    val artists: LibraryContentCount,
    val pendingMutations: LibraryContentCount,
) {
    companion object {
        fun from(snapshot: LibrarySnapshot): LibraryShape = LibraryShape(
            tracks = LibraryContentCount.from(snapshot.likedTracks.size),
            albums = LibraryContentCount.from(snapshot.savedAlbums.size),
            artists = LibraryContentCount.from(snapshot.followedArtists.size),
            pendingMutations = LibraryContentCount.from(snapshot.pendingMutationCount),
        )
    }
}

internal enum class LibraryRefreshTrigger {
    Initial,
    Manual,
    ConnectivityRecovery,
}

internal enum class LibraryRefreshAttempt {
    First,
    RetryOne,
    RetryTwo,
    RetryThreeOrMore;

    companion object {
        fun from(attemptNumber: Int): LibraryRefreshAttempt = when (
            attemptNumber.coerceAtLeast(1)
        ) {
            1 -> First
            2 -> RetryOne
            3 -> RetryTwo
            else -> RetryThreeOrMore
        }
    }
}

internal enum class LibraryRefreshDuration {
    Under250Millis,
    UnderOneSecond,
    UnderFiveSeconds,
    UnderFifteenSeconds,
    FifteenSecondsOrMore;

    companion object {
        fun from(durationMillis: Long): LibraryRefreshDuration = when (
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

internal enum class LibraryRetryDelay {
    Immediate,
    OneSecond,
    TwoToFourSeconds,
    FiveSecondsOrMore;

    companion object {
        fun from(delayMillis: Long): LibraryRetryDelay = when (delayMillis.coerceAtLeast(0L)) {
            in 0L..<1_000L -> Immediate
            in 1_000L..<2_000L -> OneSecond
            in 2_000L..<5_000L -> TwoToFourSeconds
            else -> FiveSecondsOrMore
        }
    }
}

internal enum class LibraryMutationOutcome {
    Applied,
    Queued,
    Failed,
}

internal sealed interface LibraryDiagnosticEvent {
    data class SnapshotObserved(
        val shape: LibraryShape,
        val hasSyncTime: Boolean,
    ) : LibraryDiagnosticEvent

    data class ConnectivityObserved(
        val isOnline: Boolean,
        val triggersRecovery: Boolean,
    ) : LibraryDiagnosticEvent

    data class RefreshStarted(
        val trigger: LibraryRefreshTrigger,
        val isOnline: Boolean,
    ) : LibraryDiagnosticEvent

    data class RefreshCoalesced(
        val trigger: LibraryRefreshTrigger,
    ) : LibraryDiagnosticEvent

    data class RetryScheduled(
        val trigger: LibraryRefreshTrigger,
        val nextAttempt: LibraryRefreshAttempt,
        val delay: LibraryRetryDelay,
        val failure: LibraryFailure,
    ) : LibraryDiagnosticEvent

    data class RefreshSucceeded(
        val trigger: LibraryRefreshTrigger,
        val attempts: LibraryRefreshAttempt,
        val duration: LibraryRefreshDuration,
    ) : LibraryDiagnosticEvent

    data class RefreshFailed(
        val trigger: LibraryRefreshTrigger,
        val attempts: LibraryRefreshAttempt,
        val duration: LibraryRefreshDuration,
        val failure: LibraryFailure,
        val waitingForConnection: Boolean,
    ) : LibraryDiagnosticEvent

    data class MutationCompleted(
        val kind: LibraryItemKind,
        val desiredSaved: Boolean,
        val outcome: LibraryMutationOutcome,
        val pendingMutations: LibraryContentCount,
    ) : LibraryDiagnosticEvent
}

internal fun interface LibraryDiagnosticSink {
    fun record(event: LibraryDiagnosticEvent)
}

internal object NoOpLibraryDiagnosticSink : LibraryDiagnosticSink {
    override fun record(event: LibraryDiagnosticEvent) = Unit
}

internal class BoundedLibraryDiagnosticSink(
    private val capacity: Int,
    private val logger: (String) -> Unit,
) : LibraryDiagnosticSink {
    private val lock = Any()
    private val events = ArrayDeque<LibraryDiagnosticEvent>(capacity)

    init {
        require(capacity in 1..MAX_DIAGNOSTIC_EVENTS)
    }

    override fun record(event: LibraryDiagnosticEvent) {
        synchronized(lock) {
            if (events.size == capacity) events.removeFirst()
            events.addLast(event)
        }
        try {
            logger(event.toBoundedLogLine())
        } catch (_: RuntimeException) {
            // Diagnostics are isolated from the Library state machine.
        }
    }

    fun snapshot(): List<LibraryDiagnosticEvent> = synchronized(lock) { events.toList() }
}

internal object AndroidLibraryDiagnostics {
    val sink: LibraryDiagnosticSink by lazy {
        BoundedLibraryDiagnosticSink(DEFAULT_DIAGNOSTIC_EVENTS) { message ->
            Log.d(LOG_TAG, message)
        }
    }
}

internal fun LibraryDiagnosticEvent.toBoundedLogLine(): String {
    val line = when (this) {
        is LibraryDiagnosticEvent.SnapshotObserved ->
            "event=snapshot_observed tracks=${shape.tracks.wireName()} " +
                "albums=${shape.albums.wireName()} artists=${shape.artists.wireName()} " +
                "pending=${shape.pendingMutations.wireName()} sync_time=$hasSyncTime"
        is LibraryDiagnosticEvent.ConnectivityObserved ->
            "event=connectivity_observed online=$isOnline recovery=$triggersRecovery"
        is LibraryDiagnosticEvent.RefreshStarted ->
            "event=refresh_started trigger=${trigger.wireName()} online=$isOnline"
        is LibraryDiagnosticEvent.RefreshCoalesced ->
            "event=refresh_coalesced trigger=${trigger.wireName()}"
        is LibraryDiagnosticEvent.RetryScheduled ->
            "event=retry_scheduled trigger=${trigger.wireName()} " +
                "attempt=${nextAttempt.wireName()} delay=${delay.wireName()} " +
                "failure=${failure.wireName()}"
        is LibraryDiagnosticEvent.RefreshSucceeded ->
            "event=refresh_succeeded trigger=${trigger.wireName()} " +
                "attempts=${attempts.wireName()} duration=${duration.wireName()}"
        is LibraryDiagnosticEvent.RefreshFailed ->
            "event=refresh_failed trigger=${trigger.wireName()} " +
                "attempts=${attempts.wireName()} duration=${duration.wireName()} " +
                "failure=${failure.wireName()} waiting=$waitingForConnection"
        is LibraryDiagnosticEvent.MutationCompleted ->
            "event=mutation_completed kind=${kind.wireName()} saved=$desiredSaved " +
                "outcome=${outcome.wireName()} pending=${pendingMutations.wireName()}"
    }
    return line.take(MAX_LOG_LINE_CHARACTERS)
}

private fun Enum<*>.wireName(): String = name
    .replace(UPPERCASE_BOUNDARY, "_$1")
    .lowercase()

private const val DEFAULT_DIAGNOSTIC_EVENTS = 32
private const val MAX_DIAGNOSTIC_EVENTS = 64
private const val MAX_LOG_LINE_CHARACTERS = 240
private const val LOG_TAG = "RakyzuLibrary"
private val UPPERCASE_BOUNDARY = Regex("(?<!^)([A-Z])")
