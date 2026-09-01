package my.id.rakyzumusic.core.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RecentSearchRepositoryTest {
    @Test
    fun disabledHistoryNeverRecordsQuery() {
        val state = RecentSearchState(isEnabled = false)

        assertEquals(state, state.withRecordedQuery("Midnight Signal"))
    }

    @Test
    fun recordingNormalizesWhitespaceAndMovesDuplicateToFront() {
        val state = RecentSearchState(
            isEnabled = true,
            queries = listOf("Afterglow", "midnight signal"),
        )

        assertEquals(
            listOf("Midnight Signal", "Afterglow"),
            state.withRecordedQuery("  Midnight   Signal  ").queries,
        )
    }

    @Test
    fun historyIsBoundedToTenQueries() {
        var state = RecentSearchState(isEnabled = true)
        repeat(14) { index -> state = state.withRecordedQuery("Query $index") }

        assertEquals(10, state.queries.size)
        assertEquals("Query 13", state.queries.first())
        assertFalse("Query 0" in state.queries)
    }

    @Test
    fun oneCharacterQueryIsNotSensitiveHistory() {
        val state = RecentSearchState(isEnabled = true)

        assertEquals(emptyList<String>(), state.withRecordedQuery("x").queries)
    }
}
