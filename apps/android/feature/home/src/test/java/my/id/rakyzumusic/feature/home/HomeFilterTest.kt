package my.id.rakyzumusic.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HomeFilterTest {
    @Test
    fun stableKeysRestoreEveryKnownFilter() {
        HomeFilter.entries.forEach { filter ->
            assertEquals(filter, HomeFilter.restore(filter.storageKey))
        }
    }

    @Test
    fun unknownOrMissingSavedValueFallsBackToMusic() {
        assertEquals(HomeFilter.Music, HomeFilter.restore(null))
        assertEquals(HomeFilter.Music, HomeFilter.restore("removed-filter"))
    }

    @Test
    fun persistedFilterKeysAreUnique() {
        val keys = HomeFilter.entries.map(HomeFilter::storageKey)

        assertEquals(keys.size, keys.toSet().size)
        assertNotEquals(HomeFilter.Music.storageKey, HomeFilter.NewReleases.storageKey)
    }
}
