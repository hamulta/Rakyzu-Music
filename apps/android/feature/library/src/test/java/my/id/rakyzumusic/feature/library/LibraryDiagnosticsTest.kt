package my.id.rakyzumusic.feature.library

import my.id.rakyzumusic.core.data.library.LibraryFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryDiagnosticsTest {
    @Test
    fun contentCountsAreCoarseAndBounded() {
        assertEquals(LibraryContentCount.None, LibraryContentCount.from(-1))
        assertEquals(LibraryContentCount.One, LibraryContentCount.from(1))
        assertEquals(LibraryContentCount.Few, LibraryContentCount.from(10))
        assertEquals(LibraryContentCount.Some, LibraryContentCount.from(50))
        assertEquals(LibraryContentCount.Many, LibraryContentCount.from(Int.MAX_VALUE))
    }

    @Test
    fun boundedSinkEvictsOldestEventsInInsertionOrder() {
        val lines = mutableListOf<String>()
        val sink = BoundedLibraryDiagnosticSink(2, lines::add)
        val first = LibraryDiagnosticEvent.RefreshCoalesced(LibraryRefreshTrigger.Initial)
        val second = LibraryDiagnosticEvent.RefreshCoalesced(LibraryRefreshTrigger.Manual)
        val third = LibraryDiagnosticEvent.RefreshCoalesced(
            LibraryRefreshTrigger.ConnectivityRecovery,
        )

        sink.record(first)
        sink.record(second)
        sink.record(third)

        assertEquals(listOf(second, third), sink.snapshot())
        assertEquals(3, lines.size)
    }

    @Test
    fun capacityHasAHardProcessCeiling() {
        assertThrows(IllegalArgumentException::class.java) {
            BoundedLibraryDiagnosticSink(65) {}
        }
    }

    @Test
    fun loggerFailureCannotEscapeTheBoundary() {
        val event = LibraryDiagnosticEvent.RefreshCoalesced(LibraryRefreshTrigger.Manual)
        val sink = BoundedLibraryDiagnosticSink(1) { error("transport failed") }

        sink.record(event)

        assertEquals(listOf(event), sink.snapshot())
    }

    @Test
    fun structuredLineContainsNoContentOrListenerFields() {
        val line = LibraryDiagnosticEvent.RefreshFailed(
            trigger = LibraryRefreshTrigger.ConnectivityRecovery,
            attempts = LibraryRefreshAttempt.RetryTwo,
            duration = LibraryRefreshDuration.FifteenSecondsOrMore,
            failure = LibraryFailure.NetworkUnavailable,
            waitingForConnection = true,
        ).toBoundedLogLine()

        assertEquals(
            "event=refresh_failed trigger=connectivity_recovery attempts=retry_two " +
                "duration=fifteen_seconds_or_more failure=network_unavailable waiting=true",
            line,
        )
        assertTrue(line.length <= 240)
        assertFalse(line.contains("listener"))
        assertFalse(line.contains("track_id"))
        assertFalse(line.contains("title"))
        assertFalse(line.contains("http"))
    }
}
