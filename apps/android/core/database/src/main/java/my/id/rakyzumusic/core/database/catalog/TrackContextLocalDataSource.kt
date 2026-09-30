package my.id.rakyzumusic.core.database.catalog

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import my.id.rakyzumusic.core.model.LyricLine
import my.id.rakyzumusic.core.model.LyricsKind
import my.id.rakyzumusic.core.model.TrackContext
import my.id.rakyzumusic.core.model.TrackCredit
import my.id.rakyzumusic.core.model.TrackCreditRole
import my.id.rakyzumusic.core.model.TrackLyrics

interface TrackContextLocalDataSource {
    fun observe(userId: String, trackId: String): Flow<TrackContext?>

    suspend fun replace(userId: String, context: TrackContext)

    suspend fun reconcileCatalog(userId: String, trackIds: Set<String>): Int

    suspend fun stats(userId: String): TrackContextCacheStats
}

data class TrackContextCacheStats(
    val trackCount: Int = 0,
    val lyricLineCount: Int = 0,
    val creditCount: Int = 0,
)

internal class RoomTrackContextLocalDataSource(
    private val database: RakyzuDatabase,
) : TrackContextLocalDataSource {
    private val dao = database.catalogDao()

    override fun observe(userId: String, trackId: String): Flow<TrackContext?> =
        database.invalidationTracker.createFlow(
            "track_contexts",
            "track_lyric_lines",
            "track_credits",
        ).map { read(userId, trackId) }

    private suspend fun read(userId: String, trackId: String): TrackContext? {
        val entity = dao.getTrackContext(userId, trackId) ?: return null
        val kind = LyricsKind.entries.firstOrNull { it.name == entity.lyricsKind } ?: return null
        val lines = dao.getTrackLyricLines(userId, trackId).map {
            LyricLine(text = it.text, startTimeMs = it.startTimeMs)
        }
        val credits = dao.getTrackCredits(userId, trackId).mapNotNull {
            val role = TrackCreditRole.entries.firstOrNull { role -> role.name == it.role }
                ?: return@mapNotNull null
            TrackCredit(it.displayName, role, it.sourceName)
        }
        return TrackContext(
            trackId = entity.trackId,
            lyrics = TrackLyrics(
                kind = kind,
                lines = lines,
                providerName = entity.providerName,
                providerNotice = entity.providerNotice,
            ),
            credits = credits,
            catalogRevision = entity.catalogRevision,
            cachedAtEpochMillis = entity.cachedAtEpochMillis,
            expiresAtEpochMillis = entity.expiresAtEpochMillis,
        )
    }

    override suspend fun replace(userId: String, context: TrackContext) {
        require(userId.isNotBlank() && context.trackId.isNotBlank())
        dao.replaceTrackContext(
            context = TrackContextEntity(
                userId = userId,
                trackId = context.trackId,
                lyricsKind = context.lyrics.kind.name,
                providerName = context.lyrics.providerName,
                providerNotice = context.lyrics.providerNotice,
                catalogRevision = context.catalogRevision,
                cachedAtEpochMillis = context.cachedAtEpochMillis,
                expiresAtEpochMillis = context.expiresAtEpochMillis,
            ),
            lines = context.lyrics.lines.mapIndexed { index, line ->
                TrackLyricLineEntity(userId, context.trackId, index, line.text, line.startTimeMs)
            },
            credits = context.credits.mapIndexed { index, credit ->
                TrackCreditEntity(
                    userId,
                    context.trackId,
                    index,
                    credit.displayName,
                    credit.role.name,
                    credit.sourceName,
                )
            },
        )
    }

    override suspend fun reconcileCatalog(userId: String, trackIds: Set<String>): Int {
        if (userId.isBlank() || trackIds.isEmpty()) return 0
        return dao.deleteTrackContextsOutsideCatalog(userId, trackIds.toList())
    }

    override suspend fun stats(userId: String): TrackContextCacheStats {
        if (userId.isBlank()) return TrackContextCacheStats()
        return TrackContextCacheStats(
            trackCount = dao.countTrackContexts(userId),
            lyricLineCount = dao.countTrackLyricLines(userId),
            creditCount = dao.countTrackCredits(userId),
        )
    }
}
