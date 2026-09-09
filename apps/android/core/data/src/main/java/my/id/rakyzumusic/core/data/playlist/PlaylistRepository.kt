package my.id.rakyzumusic.core.data.playlist

import kotlinx.coroutines.flow.Flow
import my.id.rakyzumusic.core.model.PlaylistSnapshot
import my.id.rakyzumusic.core.model.PlaylistSummary
import my.id.rakyzumusic.core.model.PlaylistDetail

interface PlaylistRepository {
    suspend fun artwork(userId: String, playlistId: String): PlaylistArtworkResult
    suspend fun updateArtwork(userId: String, playlistId: String, png: ByteArray?): PlaylistArtworkResult
    fun observeDetail(userId: String, playlistId: String): Flow<PlaylistDetail?>
    suspend fun refreshDetail(userId: String, playlistId: String): PlaylistActionResult
    suspend fun mutate(userId: String, playlistId: String, revision: Long, mutation: PlaylistMutation): PlaylistActionResult
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
    Conflict,
    NetworkUnavailable,
    ServiceUnavailable,
    InvalidRequest,
    InvalidPayload,
}

sealed interface PlaylistMutation {
    data class Add(val trackId: String) : PlaylistMutation
    data class Remove(val trackId: String) : PlaylistMutation
    data class Reorder(val trackIds: List<String>) : PlaylistMutation
    data class Metadata(val name: String, val description: String) : PlaylistMutation
}
