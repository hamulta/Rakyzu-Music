package my.id.rakyzumusic.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackTest {
    @Test
    fun formattedDuration_usesMinutesAndZeroPaddedSeconds() {
        val track = Track(
            id = "track-1",
            title = "Midnight Signal",
            artist = "Rakyzu Sessions",
            durationMs = 185_900L,
        )

        assertEquals("3:05", track.formattedDuration())
    }

    @Test
    fun formattedDuration_clampsNegativeDurations() {
        val track = Track("track-2", "Unknown", "Unknown", -1L)

        assertEquals("0:00", track.formattedDuration())
    }

    @Test
    fun catalogSnapshotReportsWhetherItHasLocalContent() {
        val empty = CatalogSnapshot(emptyList(), emptyList(), emptyList(), null)
        val cached = empty.copy(
            artists = listOf(Artist("artist-1", "Rakyzu Sessions")),
        )

        assertEquals(true, empty.isEmpty)
        assertEquals(false, cached.isEmpty)
    }
}
