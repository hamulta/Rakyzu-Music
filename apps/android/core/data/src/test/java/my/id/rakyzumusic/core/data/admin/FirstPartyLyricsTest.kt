package my.id.rakyzumusic.core.data.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstPartyLyricsTest {
    @Test
    fun `manual lyrics discard blank separators and remain first party content`() {
        val result = FirstPartyLyrics.parse(LyricsSourceFormat.Manual, "First line\n\nSecond line")
        assertEquals(
            listOf(ParsedLyricLine("First line"), ParsedLyricLine("Second line")),
            (result as LyricsParseResult.Success).lines,
        )
    }

    @Test
    fun `LRC parser supports metadata multiple timestamps and stable sorting`() {
        val result = FirstPartyLyrics.parse(
            LyricsSourceFormat.Lrc,
            "[ar:Rakyzu]\n[00:05.25][00:08.250]Later\n[00:01.5]First",
        ) as LyricsParseResult.Success
        assertEquals(listOf(1_500L, 5_250L, 8_250L), result.lines.map { it.startTimeMs })
        assertEquals(listOf("First", "Later", "Later"), result.lines.map { it.text })
    }

    @Test
    fun `SRT parser supports CRLF and multiline cues`() {
        val result = FirstPartyLyrics.parse(
            LyricsSourceFormat.Srt,
            "1\r\n00:00:01,000 --> 00:00:03,000\r\nFirst\r\nline\r\n\r\n" +
                "2\r\n00:00:04,500 --> 00:00:06,000\r\nSecond",
        ) as LyricsParseResult.Success
        assertEquals(1_000L, result.lines.first().startTimeMs)
        assertEquals("First\nline", result.lines.first().text)
        assertTrue(FirstPartyLyrics.format(LyricsSourceFormat.Srt, result.lines).contains("00:00:04,500"))
    }

    @Test
    fun `malformed and oversized documents fail closed`() {
        assertEquals(LyricsParseResult.Invalid, FirstPartyLyrics.parse(LyricsSourceFormat.Lrc, "untimed"))
        assertEquals(
            LyricsParseResult.Invalid,
            FirstPartyLyrics.parse(LyricsSourceFormat.Manual, "x".repeat(1_048_577)),
        )
    }
}
