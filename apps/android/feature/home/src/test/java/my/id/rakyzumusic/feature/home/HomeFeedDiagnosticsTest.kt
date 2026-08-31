package my.id.rakyzumusic.feature.home

import my.id.rakyzumusic.core.data.catalog.CatalogRefreshFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedDiagnosticsTest {
    @Test
    fun contentCountsAreBucketedWithoutPublishingExactLargeValues() {
        assertEquals(HomeContentCount.None, HomeContentCount.from(-1))
        assertEquals(HomeContentCount.None, HomeContentCount.from(0))
        assertEquals(HomeContentCount.One, HomeContentCount.from(1))
        assertEquals(HomeContentCount.Few, HomeContentCount.from(10))
        assertEquals(HomeContentCount.Some, HomeContentCount.from(50))
        assertEquals(HomeContentCount.Many, HomeContentCount.from(Int.MAX_VALUE))
    }

    @Test
    fun boundedSinkEvictsOldestEventsInInsertionOrder() {
        val loggedLines = mutableListOf<String>()
        val sink = BoundedHomeFeedDiagnosticSink(
            capacity = 2,
            logger = loggedLines::add,
        )
        val first = HomeFeedDiagnosticEvent.RefreshCoalesced(HomeRefreshTrigger.Initial)
        val second = HomeFeedDiagnosticEvent.RefreshCoalesced(HomeRefreshTrigger.Manual)
        val third = HomeFeedDiagnosticEvent.RefreshCoalesced(
            HomeRefreshTrigger.ConnectivityRecovery,
        )

        sink.record(first)
        sink.record(second)
        sink.record(third)

        assertEquals(listOf(second, third), sink.snapshot())
        assertEquals(3, loggedLines.size)
    }

    @Test
    fun diagnosticCapacityHasAProcessSafetyCeiling() {
        assertThrows(IllegalArgumentException::class.java) {
            BoundedHomeFeedDiagnosticSink(capacity = 65, logger = {})
        }
    }

    @Test
    fun loggerFailureDoesNotEscapeTheDiagnosticBoundary() {
        val event = HomeFeedDiagnosticEvent.RefreshCoalesced(HomeRefreshTrigger.Manual)
        val sink = BoundedHomeFeedDiagnosticSink(capacity = 1) {
            error("logger unavailable")
        }

        sink.record(event)

        assertEquals(listOf(event), sink.snapshot())
    }

    @Test
    fun structuredLineContainsOnlyBoundedRefreshFields() {
        val line = HomeFeedDiagnosticEvent.RefreshFailed(
            trigger = HomeRefreshTrigger.ConnectivityRecovery,
            attempts = HomeRefreshAttempt.RetryThreeOrMore,
            duration = HomeRefreshDuration.FifteenSecondsOrMore,
            failure = CatalogRefreshFailure.NetworkUnavailable,
            waitingForConnection = true,
        ).toBoundedLogLine()

        assertEquals(
            "event=refresh_failed trigger=connectivity_recovery " +
                "attempts=retry_three_or_more duration=fifteen_seconds_or_more " +
                "failure=network_unavailable waiting=true",
            line,
        )
        assertTrue(line.length <= 240)
        assertFalse(line.contains("listener"))
        assertFalse(line.contains("track"))
        assertFalse(line.contains("http"))
    }
}
