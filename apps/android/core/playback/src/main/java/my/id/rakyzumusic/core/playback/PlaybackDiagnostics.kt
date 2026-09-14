package my.id.rakyzumusic.core.playback

enum class PlaybackQueueSize {
    Empty,
    One,
    Few,
    Some,
    Many;

    companion object {
        fun from(size: Int): PlaybackQueueSize = when (size.coerceAtLeast(0)) {
            0 -> Empty
            1 -> One
            in 2..10 -> Few
            in 11..100 -> Some
            else -> Many
        }
    }
}

enum class PlaybackFailureKind {
    Network,
    Source,
    Unexpected,
}

sealed interface PlaybackDiagnosticEvent {
    data class QueueStarted(val size: PlaybackQueueSize) : PlaybackDiagnosticEvent
    data class PlaybackFailed(
        val failure: PlaybackFailureKind,
        val queueSize: PlaybackQueueSize,
        val retryable: Boolean,
    ) : PlaybackDiagnosticEvent
    data class RecoveryAttempted(
        val attempt: Int,
        val queueSize: PlaybackQueueSize,
    ) : PlaybackDiagnosticEvent
}

fun interface PlaybackDiagnosticSink {
    fun record(event: PlaybackDiagnosticEvent)
}

object NoOpPlaybackDiagnosticSink : PlaybackDiagnosticSink {
    override fun record(event: PlaybackDiagnosticEvent) = Unit
}

class BoundedPlaybackDiagnosticSink(
    capacity: Int = 32,
    private val logger: (String) -> Unit,
) : PlaybackDiagnosticSink {
    private val capacity = capacity.also { require(it in 1..64) }
    private val events = ArrayDeque<PlaybackDiagnosticEvent>(capacity)
    private val lock = Any()

    override fun record(event: PlaybackDiagnosticEvent) {
        synchronized(lock) {
            if (events.size == capacity) events.removeFirst()
            events.addLast(event)
        }
        runCatching { logger(event.toBoundedLogLine()) }
    }

    fun snapshot(): List<PlaybackDiagnosticEvent> = synchronized(lock) { events.toList() }
}

fun PlaybackDiagnosticEvent.toBoundedLogLine(): String = when (this) {
    is PlaybackDiagnosticEvent.QueueStarted ->
        "event=queue_started size=${size.wireName()}"
    is PlaybackDiagnosticEvent.PlaybackFailed ->
        "event=playback_failed failure=${failure.wireName()} size=${queueSize.wireName()} " +
            "retryable=$retryable"
    is PlaybackDiagnosticEvent.RecoveryAttempted ->
        "event=recovery_attempted attempt=${attempt.coerceIn(1, 3)} size=${queueSize.wireName()}"
}.take(160)

private fun Enum<*>.wireName(): String = name
    .replace(UPPERCASE_BOUNDARY, "$1_$2")
    .lowercase()

private val UPPERCASE_BOUNDARY = Regex("([a-z])([A-Z])")
