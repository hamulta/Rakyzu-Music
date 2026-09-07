package my.id.rakyzumusic.core.data.library

import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import my.id.rakyzumusic.core.database.catalog.LibraryLocalDataSource
import my.id.rakyzumusic.core.database.catalog.StoredLibrarySelection
import my.id.rakyzumusic.core.model.LibraryItemKind
import my.id.rakyzumusic.core.model.LibrarySnapshot

internal data class RemoteLibrarySelection(
    val kind: LibraryItemKind,
    val itemId: String,
    val savedAtEpochMillis: Long,
)

internal interface LibraryRemoteDataSource {
    suspend fun fetchLibrary(): List<RemoteLibrarySelection>

    suspend fun setSaved(kind: LibraryItemKind, itemId: String, saved: Boolean): Boolean
}

internal class OfflineFirstLibraryRepository(
    private val localDataSource: LibraryLocalDataSource,
    private val remoteDataSource: LibraryRemoteDataSource,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : LibraryRepository {
    override fun observeLibrary(userId: String): Flow<LibrarySnapshot> =
        localDataSource.observeLibrary(userId)

    override suspend fun refresh(userId: String): LibraryActionResult {
        if (userId.isBlank()) return LibraryActionResult.Failure(LibraryFailure.InvalidRequest)
        return libraryRequest {
            val selections = remoteDataSource.fetchLibrary()
            if (!selections.isValid()) {
                return@libraryRequest LibraryActionResult.Failure(LibraryFailure.InvalidPayload)
            }
            localDataSource.replaceLibrary(
                userId = userId,
                selections = selections.map {
                    StoredLibrarySelection(it.kind, it.itemId, it.savedAtEpochMillis)
                },
                syncedAtEpochMillis = currentTimeMillis(),
            )
            LibraryActionResult.Success
        }
    }

    override suspend fun setSaved(
        userId: String,
        kind: LibraryItemKind,
        itemId: String,
        saved: Boolean,
    ): LibraryActionResult {
        val normalizedId = itemId.trim()
        if (userId.isBlank() || normalizedId.isBlank()) {
            return LibraryActionResult.Failure(LibraryFailure.InvalidRequest)
        }
        return libraryRequest {
            if (!remoteDataSource.setSaved(kind, normalizedId, saved)) {
                return@libraryRequest LibraryActionResult.Failure(LibraryFailure.InvalidPayload)
            }
            localDataSource.setLibraryItem(
                userId = userId,
                selection = StoredLibrarySelection(
                    kind = kind,
                    itemId = normalizedId,
                    savedAtEpochMillis = currentTimeMillis(),
                ),
                saved = saved,
            )
            LibraryActionResult.Success
        }
    }
}

private suspend fun libraryRequest(
    block: suspend () -> LibraryActionResult,
): LibraryActionResult = try {
    block()
} catch (error: CancellationException) {
    throw error
} catch (error: Throwable) {
    LibraryActionResult.Failure(error.toLibraryFailure())
}

private fun List<RemoteLibrarySelection>.isValid(): Boolean =
    all { it.itemId.isNotBlank() && it.savedAtEpochMillis >= 0L } &&
        map { it.kind to it.itemId }.let { it.size == it.toSet().size }

private fun Throwable.toLibraryFailure(): LibraryFailure = when (this) {
    is InvalidLibraryPayloadException -> LibraryFailure.InvalidPayload
    is HttpRequestTimeoutException,
    is HttpRequestException,
    -> LibraryFailure.NetworkUnavailable
    is PostgrestRestException -> LibraryFailure.ServiceUnavailable
    else -> LibraryFailure.ServiceUnavailable
}

internal class InvalidLibraryPayloadException : IllegalStateException("Invalid library payload")
