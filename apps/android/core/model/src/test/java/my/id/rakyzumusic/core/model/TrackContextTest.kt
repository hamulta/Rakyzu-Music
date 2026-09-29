package my.id.rakyzumusic.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackContextTest {
    @Test
    fun shareUriAcceptsOnlyCanonicalTrackIdentifiers() {
        assertEquals(
            "my.id.rakyzumusic://track/123e4567-e89b-42d3-a456-426614174000",
            rakyzuTrackShareUri("123E4567-E89B-42D3-A456-426614174000"),
        )
        assertNull(rakyzuTrackShareUri("../private-object"))
    }

    @Test
    fun licensedLyricsAndBoundedCreditsFailClosed() {
        assertFalse(TrackLyrics(LyricsKind.None, listOf("hidden"), null, null).isDisplayable)
        assertTrue(TrackLyrics(LyricsKind.Plain, listOf("Original words"), "Provider", "Licensed").isDisplayable)
        assertTrue(
            TrackContext(
                trackId = "track",
                lyrics = TrackLyrics(LyricsKind.None, emptyList(), null, null),
                credits = listOf(TrackCredit("Rakyzu", TrackCreditRole.PrimaryArtist)),
                catalogRevision = "one",
            ).hasCredits,
        )
    }
}
