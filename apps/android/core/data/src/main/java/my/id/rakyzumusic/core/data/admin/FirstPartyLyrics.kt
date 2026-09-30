package my.id.rakyzumusic.core.data.admin

enum class LyricsSourceFormat(val wireName: String, val displayName: String) {
    Manual("manual", "Manual"),
    Lrc("lrc", "LRC"),
    Srt("srt", "SRT");

    companion object {
        fun fromWire(value: String?): LyricsSourceFormat? = entries.firstOrNull { it.wireName == value }
    }
}

data class EditableLyrics(
    val trackId: String,
    val sourceFormat: LyricsSourceFormat,
    val language: String?,
    val published: Boolean,
    val content: String,
    val lineCount: Int,
)

data class ParsedLyricLine(val text: String, val startTimeMs: Long? = null)

sealed interface LyricsParseResult {
    data class Success(val lines: List<ParsedLyricLine>) : LyricsParseResult
    data object Invalid : LyricsParseResult
}

sealed interface AdminLyricsResult {
    data class Success(val lyrics: EditableLyrics) : AdminLyricsResult
    data class Failure(val reason: AdminFailure) : AdminLyricsResult
}

object FirstPartyLyrics {
    private const val MAX_DOCUMENT_BYTES = 1_048_576
    private const val MAX_LINES = 2_000
    private const val MAX_LINE_LENGTH = 500
    private const val MAX_TIME_MS = 86_400_000L
    private val lrcLine = Regex("^((?:\\[\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?])+)(.*)$")
    private val lrcTimestamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    private val srtTiming = Regex(
        "^(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{3})\\s*-->\\s*" +
            "(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{3})(?:\\s+.*)?$",
    )

    fun parse(format: LyricsSourceFormat, document: String): LyricsParseResult {
        if (document.encodeToByteArray().size > MAX_DOCUMENT_BYTES) return LyricsParseResult.Invalid
        val normalized = document.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val lines = when (format) {
            LyricsSourceFormat.Manual -> parseManual(normalized)
            LyricsSourceFormat.Lrc -> parseLrc(normalized)
            LyricsSourceFormat.Srt -> parseSrt(normalized)
        } ?: return LyricsParseResult.Invalid
        if (lines.isEmpty() || lines.size > MAX_LINES || lines.any {
                it.text.isBlank() || it.text.length > MAX_LINE_LENGTH ||
                    (it.startTimeMs != null && it.startTimeMs !in 0..MAX_TIME_MS)
            }
        ) return LyricsParseResult.Invalid
        return LyricsParseResult.Success(lines)
    }

    fun format(format: LyricsSourceFormat, lines: List<ParsedLyricLine>): String = when (format) {
        LyricsSourceFormat.Manual -> lines.joinToString("\n") { it.text }
        LyricsSourceFormat.Lrc -> lines.joinToString("\n") {
            "[${formatLrcTime(it.startTimeMs ?: 0)}]${it.text}"
        }
        LyricsSourceFormat.Srt -> lines.mapIndexed { index, line ->
            val start = line.startTimeMs ?: 0
            val next = lines.getOrNull(index + 1)?.startTimeMs ?: (start + 3_000)
            "${index + 1}\n${formatSrtTime(start)} --> ${formatSrtTime(maxOf(start + 1, next - 1))}\n${line.text}"
        }.joinToString("\n\n")
    }

    private fun parseManual(document: String): List<ParsedLyricLine>? = document.lineSequence()
        .map(String::trim).filter(String::isNotEmpty).map(::ParsedLyricLine).toList()

    private fun parseLrc(document: String): List<ParsedLyricLine>? {
        val result = mutableListOf<Pair<Int, ParsedLyricLine>>()
        document.lineSequence().forEachIndexed { order, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.matches(Regex("^\\[[A-Za-z]+:.*]$"))) return@forEachIndexed
            val match = lrcLine.matchEntire(line) ?: return null
            val text = match.groupValues[2].trim()
            if (text.isEmpty()) return null
            lrcTimestamp.findAll(match.groupValues[1]).forEach { timestamp ->
                val time = parseLrcTime(timestamp.groupValues) ?: return null
                result += order to ParsedLyricLine(text, time)
            }
        }
        return result.sortedWith(compareBy<Pair<Int, ParsedLyricLine>> { it.second.startTimeMs }.thenBy { it.first })
            .map { it.second }
    }

    private fun parseSrt(document: String): List<ParsedLyricLine>? {
        val result = mutableListOf<ParsedLyricLine>()
        document.split(Regex("\\n{2,}")).forEach { rawBlock ->
            val block = rawBlock.lines().map(String::trim).filter(String::isNotEmpty)
            if (block.isEmpty()) return@forEach
            val timingIndex = if (block.first().all(Char::isDigit)) 1 else 0
            val timing = block.getOrNull(timingIndex)?.let(srtTiming::matchEntire) ?: return null
            val text = block.drop(timingIndex + 1).joinToString("\n").trim()
            if (text.isEmpty()) return null
            val start = parseSrtTime(timing.groupValues.slice(1..4)) ?: return null
            val end = parseSrtTime(timing.groupValues.slice(5..8)) ?: return null
            if (end <= start) return null
            result += ParsedLyricLine(text, start)
        }
        return result.takeIf { lines -> lines.zipWithNext().all { (a, b) ->
            (a.startTimeMs ?: 0) <= (b.startTimeMs ?: 0)
        } }
    }

    private fun parseLrcTime(groups: List<String>): Long? {
        val minutes = groups[1].toLongOrNull() ?: return null
        val seconds = groups[2].toLongOrNull() ?: return null
        if (seconds > 59) return null
        val fraction = groups[3]
        val millis = when (fraction.length) {
            0 -> 0
            1 -> fraction.toLong() * 100
            2 -> fraction.toLong() * 10
            3 -> fraction.toLong()
            else -> return null
        }
        return (minutes * 60_000 + seconds * 1_000 + millis).takeIf { it <= MAX_TIME_MS }
    }

    private fun parseSrtTime(groups: List<String>): Long? {
        val values = groups.map(String::toLongOrNull)
        if (values.any { it == null }) return null
        val (hours, minutes, seconds, millis) = values.requireNoNulls()
        if (minutes > 59 || seconds > 59 || millis > 999) return null
        return (hours * 3_600_000 + minutes * 60_000 + seconds * 1_000 + millis)
            .takeIf { it <= MAX_TIME_MS }
    }

    private fun formatLrcTime(value: Long): String = "%02d:%02d.%03d".format(
        value / 60_000,
        value / 1_000 % 60,
        value % 1_000,
    )

    private fun formatSrtTime(value: Long): String = "%02d:%02d:%02d,%03d".format(
        value / 3_600_000,
        value / 60_000 % 60,
        value / 1_000 % 60,
        value % 1_000,
    )
}
