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
        assertEquals(
            "123e4567-e89b-42d3-a456-426614174000",
            parseRakyzuTrackShareUri(
                "my.id.rakyzumusic://track/123E4567-E89B-42D3-A456-426614174000",
            ),
        )
        assertNull(
            parseRakyzuTrackShareUri(
                "my.id.rakyzumusic://track/123e4567-e89b-42d3-a456-426614174000?token=secret",
            ),
        )
    }

    @Test
    fun licensedLyricsAndBoundedCreditsFailClosed() {
        assertFalse(
            TrackLyrics(
                LyricsKind.Unavailable,
                listOf(LyricLine("hidden")),
                null,
                null,
            ).isDisplayable,
        )
        assertTrue(
            TrackLyrics(
                LyricsKind.Plain,
                listOf(LyricLine("Original words")),
                "Provider",
                "Licensed",
            ).isDisplayable,
        )
        assertTrue(
            TrackContext(
                trackId = "track",
                lyrics = TrackLyrics(LyricsKind.Unavailable, emptyList(), null, null),
                credits = listOf(TrackCredit("Rakyzu", TrackCreditRole.PrimaryArtist)),
                catalogRevision = "one",
            ).hasCredits,
        )
    }

    @Test
    fun timeSyncedLyricsSelectTheLatestStartedLine() {
        val lyrics = TrackLyrics(
            kind = LyricsKind.TimeSynced,
            lines = listOf(
                LyricLine("First", 0),
                LyricLine("Second", 2_000),
                LyricLine("Third", 4_000),
            ),
            providerName = "Provider",
            providerNotice = "Licensed",
        )

        assertEquals(1, lyrics.activeLineIndex(3_500))
        assertNull(lyrics.activeLineIndex(-1))
    }
}
