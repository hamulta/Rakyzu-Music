package my.id.rakyzumusic.core.data.playlist

import kotlinx.coroutines.flow.Flow
import my.id.rakyzumusic.core.model.PlaylistSnapshot
import my.id.rakyzumusic.core.model.PlaylistSummary
import my.id.rakyzumusic.core.model.PlaylistDetail
import my.id.rakyzumusic.core.model.PlaylistInvite
import my.id.rakyzumusic.core.model.PlaylistRole
import my.id.rakyzumusic.core.model.PlaylistVisibility

interface PlaylistRepository {
    suspend fun artwork(userId: String, playlistId: String): PlaylistArtworkResult
    suspend fun updateArtwork(userId: String, playlistId: String, png: ByteArray?): PlaylistArtworkResult
    fun observeDetail(userId: String, playlistId: String): Flow<PlaylistDetail?>
    suspend fun refreshDetail(userId: String, playlistId: String): PlaylistActionResult
    suspend fun loadMoreDetail(userId: String, playlistId: String): PlaylistActionResult =
        PlaylistActionResult.Failure(PlaylistFailure.InvalidRequest)
    suspend fun mutate(userId: String, playlistId: String, revision: Long, mutation: PlaylistMutation): PlaylistActionResult
    suspend fun retryPending(userId: String, playlistId: String): PlaylistActionResult =
        PlaylistActionResult.Success()
    suspend fun createInvite(
        userId: String,
        playlistId: String,
        role: PlaylistRole,
    ): PlaylistInviteResult = PlaylistInviteResult.Failure(PlaylistFailure.InvalidRequest)
    suspend fun acceptInvite(userId: String, token: String): PlaylistActionResult =
        PlaylistActionResult.Failure(PlaylistFailure.InvalidRequest)
    suspend fun removeMember(userId: String, playlistId: String, memberId: String): PlaylistActionResult =
        PlaylistActionResult.Failure(PlaylistFailure.InvalidRequest)
    suspend fun leave(userId: String, playlistId: String): PlaylistActionResult =
        PlaylistActionResult.Failure(PlaylistFailure.InvalidRequest)
    suspend fun setVisibility(
        userId: String,
        playlistId: String,
        revision: Long,
        visibility: PlaylistVisibility,
    ): PlaylistActionResult = PlaylistActionResult.Failure(PlaylistFailure.InvalidRequest)
    suspend fun setFollowing(
        userId: String,
        playlistId: String,
        following: Boolean,
    ): PlaylistActionResult = PlaylistActionResult.Failure(PlaylistFailure.InvalidRequest)
    fun observePlaylists(userId: String): Flow<PlaylistSnapshot>

    suspend fun refresh(userId: String): PlaylistActionResult
    suspend fun loadMore(userId: String): PlaylistActionResult =
        PlaylistActionResult.Success()

    suspend fun create(
        userId: String,
        name: String,
        description: String,
    ): PlaylistActionResult
}

sealed interface PlaylistActionResult {
    data class Success(val playlist: PlaylistSummary? = null) : PlaylistActionResult
    data class Queued(val pendingCount: Int) : PlaylistActionResult

    data class Failure(val reason: PlaylistFailure) : PlaylistActionResult
}

sealed interface PlaylistInviteResult {
    data class Success(val invite: PlaylistInvite) : PlaylistInviteResult
    data class Failure(val reason: PlaylistFailure) : PlaylistInviteResult
}

enum class PlaylistFailure {
    Conflict,
    NetworkUnavailable,
    ServiceUnavailable,
    InvalidRequest,
    InvalidPayload,
}

@kotlinx.serialization.Serializable
sealed interface PlaylistMutation {
    @kotlinx.serialization.Serializable
    data class Add(val trackId: String) : PlaylistMutation
    @kotlinx.serialization.Serializable
    data class Remove(val trackId: String) : PlaylistMutation
    @kotlinx.serialization.Serializable
    data class Reorder(val trackIds: List<String>) : PlaylistMutation
    @kotlinx.serialization.Serializable
    data class Metadata(val name: String, val description: String) : PlaylistMutation
}
