package my.id.rakyzumusic.core.data.playlist

import kotlinx.coroutines.flow.Flow
import my.id.rakyzumusic.core.model.PlaylistSnapshot
import my.id.rakyzumusic.core.model.PlaylistSummary

interface PlaylistRepository {
    fun observePlaylists(userId: String): Flow<PlaylistSnapshot>

    suspend fun refresh(userId: String): PlaylistActionResult

    suspend fun create(
        userId: String,
        name: String,
        description: String,
    ): PlaylistActionResult
}

sealed interface PlaylistActionResult {
    data class Success(val playlist: PlaylistSummary? = null) : PlaylistActionResult

    data class Failure(val reason: PlaylistFailure) : PlaylistActionResult
}

enum class PlaylistFailure {
    NetworkUnavailable,
    ServiceUnavailable,
    InvalidRequest,
    InvalidPayload,
}
