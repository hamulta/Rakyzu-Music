package my.id.rakyzumusic.core.data.library

import kotlinx.coroutines.flow.Flow
import my.id.rakyzumusic.core.model.LibraryItemKind
import my.id.rakyzumusic.core.model.LibrarySnapshot

interface LibraryRepository {
    fun observeLibrary(userId: String): Flow<LibrarySnapshot>

    suspend fun refresh(userId: String): LibraryActionResult

    suspend fun setSaved(
        userId: String,
        kind: LibraryItemKind,
        itemId: String,
        saved: Boolean,
    ): LibraryActionResult
}

sealed interface LibraryActionResult {
    data object Success : LibraryActionResult

    data class Failure(val reason: LibraryFailure) : LibraryActionResult
}

enum class LibraryFailure {
    NetworkUnavailable,
    ServiceUnavailable,
    InvalidRequest,
    InvalidPayload,
}
