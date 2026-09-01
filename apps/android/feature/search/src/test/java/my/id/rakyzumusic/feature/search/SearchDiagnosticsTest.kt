package my.id.rakyzumusic.feature.search

import my.id.rakyzumusic.core.data.catalog.CatalogSearchFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchDiagnosticsTest {
    @Test
    fun contentAndDurationValuesAreCoarselyBucketed() {
        assertEquals(SearchContentCount.None, SearchContentCount.from(-1))
        assertEquals(SearchContentCount.One, SearchContentCount.from(1))
        assertEquals(SearchContentCount.Few, SearchContentCount.from(10))
        assertEquals(SearchContentCount.Some, SearchContentCount.from(50))
        assertEquals(SearchContentCount.Many, SearchContentCount.from(Int.MAX_VALUE))
        assertEquals(SearchDuration.Under16Millis, SearchDuration.from(-1L))
        assertEquals(SearchDuration.Under50Millis, SearchDuration.from(16L))
        assertEquals(SearchDuration.Under250Millis, SearchDuration.from(50L))
        assertEquals(SearchDuration.UnderOneSecond, SearchDuration.from(250L))
        assertEquals(SearchDuration.OneSecondOrMore, SearchDuration.from(1_000L))
    }

    @Test
    fun boundedSinkEvictsOldestEventsInInsertionOrder() {
        val lines = mutableListOf<String>()
        val sink = BoundedSearchDiagnosticSink(capacity = 2, logger = lines::add)
        val first = SearchDiagnosticEvent.RemoteSearchStarted(
            RemoteSearchTrigger.Submit,
            RemoteSearchPage.Initial,
        )
        val second = SearchDiagnosticEvent.RemoteSearchStarted(
            RemoteSearchTrigger.LoadMore,
            RemoteSearchPage.Additional,
        )
        val third = SearchDiagnosticEvent.RemoteSearchSkippedOffline(
            RemoteSearchTrigger.ConnectivityRecovery,
            RemoteSearchPage.Initial,
        )

        sink.record(first)
        sink.record(second)
        sink.record(third)

        assertEquals(listOf(second, third), sink.snapshot())
        assertEquals(3, lines.size)
    }

    @Test
    fun diagnosticCapacityHasAProcessSafetyCeiling() {
        assertThrows(IllegalArgumentException::class.java) {
            BoundedSearchDiagnosticSink(capacity = 65, logger = {})
        }
    }

    @Test
    fun loggerFailureDoesNotEscapeDiagnosticBoundary() {
        val event = SearchDiagnosticEvent.RemoteSearchStarted(
            RemoteSearchTrigger.ManualRetry,
            RemoteSearchPage.Initial,
        )
        val sink = BoundedSearchDiagnosticSink(capacity = 1) {
            error("logger unavailable")
        }

        sink.record(event)

        assertEquals(listOf(event), sink.snapshot())
    }

    @Test
    fun structuredLinesContainOnlyBoundedNonContentFields() {
        val line = SearchDiagnosticEvent.RemoteSearchFailed(
            trigger = RemoteSearchTrigger.ConnectivityRecovery,
            page = RemoteSearchPage.Initial,
            duration = SearchDuration.OneSecondOrMore,
            failure = CatalogSearchFailure.ServiceUnavailable,
        ).toBoundedLogLine()

        assertEquals(
            "event=remote_search_failed trigger=connectivity_recovery " +
                "page=initial duration=one_second_or_more failure=service_unavailable",
            line,
        )
        assertTrue(line.length <= 240)
        listOf("query", "listener", "signal", "track-", "album-", "artist-", "http").forEach {
            assertFalse(line.contains(it, ignoreCase = true))
        }
    }
}
