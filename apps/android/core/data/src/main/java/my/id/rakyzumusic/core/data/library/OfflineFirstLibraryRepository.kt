package my.id.rakyzumusic.core.data.library

import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import my.id.rakyzumusic.core.database.catalog.LibraryLocalDataSource
import my.id.rakyzumusic.core.database.catalog.StoredLibraryMutation
import my.id.rakyzumusic.core.database.catalog.StoredLibrarySelection
import my.id.rakyzumusic.core.database.catalog.StoredRemoteLibraryChange
import my.id.rakyzumusic.core.model.LibraryItemKind
import my.id.rakyzumusic.core.model.LibrarySnapshot

internal data class RemoteLibrarySelection(
    val kind: LibraryItemKind,
    val itemId: String,
    val savedAtEpochMillis: Long,
)

internal data class RemoteLibraryCursor(
    val savedAtEpochMillis: Long,
    val kind: LibraryItemKind,
    val itemId: String,
)

internal data class RemoteLibraryPage(
    val selections: List<RemoteLibrarySelection>,
    val nextCursor: RemoteLibraryCursor?,
)

internal data class RemoteLibraryChange(
    val sequence: Long,
    val kind: LibraryItemKind,
    val itemId: String,
    val saved: Boolean,
    val savedAtEpochMillis: Long,
)

internal data class RemoteLibraryChangePage(
    val changes: List<RemoteLibraryChange>,
    val hasMore: Boolean,
)

internal interface LibraryRemoteDataSource {
    suspend fun fetchSyncAnchor(): Long

    suspend fun fetchLibraryPage(cursor: RemoteLibraryCursor?, limit: Int): RemoteLibraryPage

    suspend fun fetchLibraryChanges(afterSequence: Long, limit: Int): RemoteLibraryChangePage

    suspend fun setSaved(kind: LibraryItemKind, itemId: String, saved: Boolean): Boolean
}

internal class OfflineFirstLibraryRepository(
    private val localDataSource: LibraryLocalDataSource,
    private val remoteDataSource: LibraryRemoteDataSource,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : LibraryRepository {
    private val operationMutex = Mutex()

    override fun observeLibrary(userId: String): Flow<LibrarySnapshot> =
        localDataSource.observeLibrary(userId)

    override suspend fun refresh(userId: String): LibraryActionResult = operationMutex.withLock {
        if (userId.isBlank()) return LibraryActionResult.Failure(LibraryFailure.InvalidRequest)
        return libraryRequest {
            flushPendingMutations(userId)
            var cursor = localDataSource.getLibraryChangeCursor(userId)
            if (cursor == null) {
                val anchor = remoteDataSource.fetchSyncAnchor()
                if (anchor < 0L) throw InvalidLibraryPayloadException()
                val selections = fetchInitialSnapshot()
                localDataSource.replaceLibrary(
                    userId = userId,
                    selections = selections.map {
                        StoredLibrarySelection(it.kind, it.itemId, it.savedAtEpochMillis)
                    },
                    syncedAtEpochMillis = currentTimeMillis(),
                )
                localDataSource.setLibraryChangeCursor(userId, anchor)
                cursor = anchor
            }
            fetchIncrementalChanges(userId, requireNotNull(cursor))
            LibraryActionResult.Success
        }
    }

    override suspend fun setSaved(
        userId: String,
        kind: LibraryItemKind,
        itemId: String,
        saved: Boolean,
    ): LibraryActionResult = operationMutex.withLock {
        val normalizedId = itemId.trim()
        if (userId.isBlank() || normalizedId.isBlank()) {
            return LibraryActionResult.Failure(LibraryFailure.InvalidRequest)
        }
        val mutation = StoredLibraryMutation(
            kind = kind,
            itemId = normalizedId,
            saved = saved,
            queuedAtEpochMillis = currentTimeMillis(),
        )
        localDataSource.enqueueLibraryMutation(userId, mutation)
        return try {
            when (flushPendingMutations(userId)) {
                FlushResult.Complete -> LibraryActionResult.Success
                FlushResult.Queued -> queuedResult(userId)
                FlushResult.Rejected ->
                    LibraryActionResult.Failure(LibraryFailure.InvalidPayload)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (error.toLibraryFailure().isRetryable) {
                queuedResult(userId)
            } else {
                LibraryActionResult.Failure(error.toLibraryFailure())
            }
        }
    }

    private suspend fun fetchInitialSnapshot(): List<RemoteLibrarySelection> {
        val allSelections = mutableListOf<RemoteLibrarySelection>()
        var cursor: RemoteLibraryCursor? = null
        repeat(MAX_INITIAL_PAGES) {
            val page = remoteDataSource.fetchLibraryPage(cursor, PAGE_SIZE)
            if (!page.selections.isValid() ||
                page.selections.any { selection ->
                    allSelections.any { it.kind == selection.kind && it.itemId == selection.itemId }
                }
            ) {
                throw InvalidLibraryPayloadException()
            }
            allSelections += page.selections
            val next = page.nextCursor ?: return allSelections
            if (page.selections.isEmpty() || next == cursor) throw InvalidLibraryPayloadException()
            cursor = next
        }
        throw InvalidLibraryPayloadException()
    }

    private suspend fun fetchIncrementalChanges(userId: String, startingCursor: Long) {
        var cursor = startingCursor
        repeat(MAX_CHANGE_PAGES_PER_REFRESH) {
            val page = remoteDataSource.fetchLibraryChanges(cursor, PAGE_SIZE)
            if (!page.changes.isValidAfter(cursor)) throw InvalidLibraryPayloadException()
            if (page.changes.isNotEmpty()) {
                localDataSource.applyRemoteLibraryChanges(
                    userId = userId,
                    changes = page.changes.map { change ->
                        StoredRemoteLibraryChange(
                            sequence = change.sequence,
                            kind = change.kind,
                            itemId = change.itemId,
                            saved = change.saved,
                            savedAtEpochMillis = change.savedAtEpochMillis,
                        )
                    },
                )
                cursor = page.changes.last().sequence
            }
            if (!page.hasMore) return
            if (page.changes.isEmpty()) throw InvalidLibraryPayloadException()
        }
    }

    private suspend fun flushPendingMutations(userId: String): FlushResult {
        val mutations = localDataSource.getPendingLibraryMutations(userId, OUTBOX_BATCH_SIZE)
        var rejected = false
        for (mutation in mutations) {
            try {
                val accepted = remoteDataSource.setSaved(
                    mutation.kind,
                    mutation.itemId,
                    mutation.saved,
                )
                if (accepted) {
                    localDataSource.acknowledgeLibraryMutation(userId, mutation)
                } else {
                    rejected = true
                    localDataSource.acknowledgeLibraryMutation(userId, mutation)
                    localDataSource.setLibraryItem(
                        userId = userId,
                        selection = StoredLibrarySelection(
                            mutation.kind,
                            mutation.itemId,
                            mutation.queuedAtEpochMillis,
                        ),
                        saved = !mutation.saved,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                localDataSource.recordLibraryMutationAttempt(userId, mutation)
                if (error.toLibraryFailure().isRetryable) return FlushResult.Queued
                throw error
            }
        }
        return when {
            rejected -> FlushResult.Rejected
            localDataSource.getPendingLibraryMutations(userId, 1).isNotEmpty() -> FlushResult.Queued
            else -> FlushResult.Complete
        }
    }

    private suspend fun queuedResult(userId: String): LibraryActionResult.Queued {
        val count = localDataSource.countPendingLibraryMutations(userId)
        return LibraryActionResult.Queued(count)
    }

    private sealed interface FlushResult {
        data object Complete : FlushResult
        data object Queued : FlushResult
        data object Rejected : FlushResult
    }

    private companion object {
        const val PAGE_SIZE = 50
        const val MAX_INITIAL_PAGES = 20
        const val MAX_CHANGE_PAGES_PER_REFRESH = 10
        const val OUTBOX_BATCH_SIZE = 50
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

private fun List<RemoteLibraryChange>.isValidAfter(cursor: Long): Boolean =
    all { it.sequence > cursor && it.itemId.isNotBlank() && it.savedAtEpochMillis >= 0L } &&
        zipWithNext().all { (first, second) -> first.sequence < second.sequence }

private fun Throwable.toLibraryFailure(): LibraryFailure = when (this) {
    is InvalidLibraryPayloadException -> LibraryFailure.InvalidPayload
    is HttpRequestTimeoutException,
    is HttpRequestException,
    -> LibraryFailure.NetworkUnavailable
    is PostgrestRestException -> LibraryFailure.ServiceUnavailable
    else -> LibraryFailure.ServiceUnavailable
}

internal class InvalidLibraryPayloadException : IllegalStateException("Invalid library payload")

private val LibraryFailure.isRetryable: Boolean
    get() = this == LibraryFailure.NetworkUnavailable || this == LibraryFailure.ServiceUnavailable
