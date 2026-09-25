package my.id.rakyzumusic.core.data.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import my.id.rakyzumusic.core.data.media.MediaDeliveryRepository
import my.id.rakyzumusic.core.data.media.OfflineDownloadRequestResult
import my.id.rakyzumusic.core.database.catalog.OfflineDownloadLocalDataSource
import my.id.rakyzumusic.core.database.catalog.StoredOfflineDownload
import my.id.rakyzumusic.core.database.catalog.StoredOfflineDownloadCollection
import my.id.rakyzumusic.core.model.DownloadCollectionKind
import my.id.rakyzumusic.core.model.MIN_OFFLINE_STORAGE_RESERVE_BYTES
import my.id.rakyzumusic.core.model.OfflineDownloadItem
import my.id.rakyzumusic.core.model.OfflineDownloadStatus
import my.id.rakyzumusic.core.model.OfflineDownloadsSnapshot
import my.id.rakyzumusic.core.model.OfflineDownloadStorage
import my.id.rakyzumusic.core.model.OfflineMediaAsset
import my.id.rakyzumusic.core.model.Track

sealed interface OfflineDownloadActionResult {
    data object Accepted : OfflineDownloadActionResult
    data class Rejected(val reason: OfflineDownloadFailure) : OfflineDownloadActionResult
}

enum class OfflineDownloadFailure {
    InvalidRequest,
    WrongAccount,
    InsufficientStorage,
    NotAuthenticated,
    NotEntitled,
    MediaUnavailable,
    Network,
    Storage,
}

interface OfflineDownloadRepository {
    fun observe(userId: String): Flow<OfflineDownloadsSnapshot>
    suspend fun enqueueCollection(
        userId: String,
        kind: DownloadCollectionKind,
        collectionId: String,
        collectionTitle: String,
        tracks: List<Track>,
    ): OfflineDownloadActionResult
    suspend fun pause(userId: String, trackId: String): OfflineDownloadActionResult
    suspend fun resume(userId: String, trackId: String): OfflineDownloadActionResult
    suspend fun cancel(userId: String, trackId: String): OfflineDownloadActionResult
    suspend fun retry(userId: String, trackId: String): OfflineDownloadActionResult
    suspend fun setAllowMobileDownloads(userId: String, allow: Boolean): OfflineDownloadActionResult
    suspend fun clearCompleted(userId: String): OfflineDownloadActionResult
    fun openForPlayback(userId: String, trackId: String): OfflineMediaAsset?
}

enum class DownloadExecutionResult { Success, Retry, Failure }

interface ExecutableOfflineDownloadRepository : OfflineDownloadRepository {
    suspend fun execute(userId: String, trackId: String): DownloadExecutionResult
}

internal class AuthenticatedOfflineDownloadRepository(
    context: Context,
    private val local: OfflineDownloadLocalDataSource,
    private val mediaDelivery: MediaDeliveryRepository,
    private val activeUserId: () -> String?,
    private val now: () -> Long = System::currentTimeMillis,
) : ExecutableOfflineDownloadRepository {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    private val store = EncryptedDownloadStore(appContext)

    override fun observe(userId: String): Flow<OfflineDownloadsSnapshot> = if (userId.isBlank()) {
        flowOf(OfflineDownloadsSnapshot())
    } else {
        local.observe(userId).map { stored ->
            OfflineDownloadsSnapshot(
                items = stored.items.map(StoredOfflineDownload::item),
                allowMobileDownloads = stored.allowMobileDownloads,
                storage = OfflineDownloadStorage(
                    encryptedBytes = stored.items
                        .distinctBy { it.item.trackId }
                        .sumOf { store.encryptedBytes(it.fileToken) },
                    availableBytes = store.availableBytes(),
                ),
            )
        }
    }

    override suspend fun enqueueCollection(
        userId: String,
        kind: DownloadCollectionKind,
        collectionId: String,
        collectionTitle: String,
        tracks: List<Track>,
    ): OfflineDownloadActionResult {
        if (!ownsSession(userId)) return OfflineDownloadActionResult.Rejected(OfflineDownloadFailure.WrongAccount)
        if (collectionId.isBlank() || collectionTitle.isBlank() || tracks.isEmpty() ||
            tracks.any { it.id.isBlank() }
        ) return OfflineDownloadActionResult.Rejected(OfflineDownloadFailure.InvalidRequest)
        if (store.availableBytes() <= MIN_OFFLINE_STORAGE_RESERVE_BYTES) {
            return OfflineDownloadActionResult.Rejected(OfflineDownloadFailure.InsufficientStorage)
        }
        val timestamp = now()
        val existing = local.getAll(userId).associateBy { it.item.trackId }
        local.upsert(tracks.distinctBy(Track::id).map { track ->
            val prior = existing[track.id]
            val reusable = prior?.item?.status == OfflineDownloadStatus.Completed &&
                prior.fileToken != null &&
                prior.licenseExpiresAtEpochMillis?.let { it > timestamp } == true &&
                store.encryptedBytes(prior.fileToken) > 0L
            val alreadyActive = prior?.item?.status == OfflineDownloadStatus.Queued ||
                prior?.item?.status == OfflineDownloadStatus.Downloading
            val retained = prior.takeIf { reusable || alreadyActive }
            if (prior?.item?.status == OfflineDownloadStatus.Completed && !reusable) {
                store.delete(prior.fileToken)
            }
            StoredOfflineDownload(
                item = OfflineDownloadItem(
                    userId = userId,
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    collectionKind = kind,
                    collectionId = collectionId,
                    collectionTitle = collectionTitle,
                    status = when {
                        reusable -> OfflineDownloadStatus.Completed
                        alreadyActive -> retained?.item?.status ?: OfflineDownloadStatus.Queued
                        else -> OfflineDownloadStatus.Queued
                    },
                    downloadedBytes = retained?.item?.downloadedBytes ?: 0L,
                    totalBytes = retained?.item?.totalBytes,
                    updatedAtEpochMillis = timestamp,
                ),
                fileToken = prior?.fileToken.takeIf { reusable },
                contentType = prior?.contentType.takeIf { reusable },
                licenseExpiresAtEpochMillis = prior?.licenseExpiresAtEpochMillis.takeIf { reusable },
                attemptCount = prior?.attemptCount ?: 0,
                requestedAtEpochMillis = timestamp,
            )
        })
        local.linkCollections(tracks.distinctBy(Track::id).map { track ->
            StoredOfflineDownloadCollection(
                userId = userId,
                kind = kind,
                collectionId = collectionId,
                collectionTitle = collectionTitle,
                trackId = track.id,
            )
        })
        val allowMobile = local.allowMobileDownloads(userId)
        tracks.distinctBy(Track::id).forEach { track ->
            val prior = existing[track.id]
            val reusable = prior?.item?.status == OfflineDownloadStatus.Completed &&
                prior.fileToken != null &&
                prior.licenseExpiresAtEpochMillis?.let { it > timestamp } == true &&
                store.encryptedBytes(prior.fileToken) > 0L
            val alreadyActive = prior?.item?.status == OfflineDownloadStatus.Queued ||
                prior?.item?.status == OfflineDownloadStatus.Downloading
            if (!reusable && !alreadyActive) {
                schedule(userId, track.id, allowMobile)
            }
        }
        return OfflineDownloadActionResult.Accepted
    }

    override suspend fun pause(userId: String, trackId: String): OfflineDownloadActionResult =
        transition(userId, trackId, OfflineDownloadStatus.Paused) {
            workManager.cancelUniqueWork(workName(userId, trackId))
        }

    override suspend fun resume(userId: String, trackId: String): OfflineDownloadActionResult =
        restart(userId, trackId)

    override suspend fun retry(userId: String, trackId: String): OfflineDownloadActionResult =
        restart(userId, trackId)

    override suspend fun cancel(userId: String, trackId: String): OfflineDownloadActionResult {
        if (!ownsSession(userId)) return OfflineDownloadActionResult.Rejected(OfflineDownloadFailure.WrongAccount)
        val item = local.get(userId, trackId)
            ?: return OfflineDownloadActionResult.Rejected(OfflineDownloadFailure.InvalidRequest)
        local.setStatus(userId, trackId, OfflineDownloadStatus.Cancelled, updatedAtEpochMillis = now())
        workManager.cancelUniqueWork(workName(userId, trackId))
        store.delete(item.fileToken)
        return OfflineDownloadActionResult.Accepted
    }

    override suspend fun setAllowMobileDownloads(
        userId: String,
        allow: Boolean,
    ): OfflineDownloadActionResult {
        if (!ownsSession(userId)) return OfflineDownloadActionResult.Rejected(OfflineDownloadFailure.WrongAccount)
        local.setAllowMobileDownloads(userId, allow)
        local.getAll(userId)
            .filter { it.item.status == OfflineDownloadStatus.Queued }
            .forEach { schedule(userId, it.item.trackId, allow) }
        return OfflineDownloadActionResult.Accepted
    }

    override suspend fun clearCompleted(userId: String): OfflineDownloadActionResult {
        if (!ownsSession(userId)) return OfflineDownloadActionResult.Rejected(OfflineDownloadFailure.WrongAccount)
        local.getAll(userId)
            .filter { it.item.status == OfflineDownloadStatus.Completed }
            .forEach { store.delete(it.fileToken) }
        local.removeCompleted(userId)
        return OfflineDownloadActionResult.Accepted
    }

    override fun openForPlayback(userId: String, trackId: String): OfflineMediaAsset? {
        if (!ownsSession(userId)) return null
        val item = runCatching {
            kotlinx.coroutines.runBlocking(Dispatchers.IO) { local.get(userId, trackId) }
        }.getOrNull() ?: return null
        if (item.item.status != OfflineDownloadStatus.Completed ||
            item.licenseExpiresAtEpochMillis?.let { it <= now() } != false
        ) return null
        return store.open(
            userId = userId,
            fileToken = item.fileToken ?: return null,
            plaintextLength = item.item.totalBytes ?: return null,
            contentType = item.contentType ?: DEFAULT_CONTENT_TYPE,
        )
    }

    override suspend fun execute(userId: String, trackId: String): DownloadExecutionResult =
        withContext(Dispatchers.IO) {
            when (sessionAccess(userId)) {
                SessionAccess.Pending -> return@withContext DownloadExecutionResult.Retry
                SessionAccess.DifferentAccount -> return@withContext DownloadExecutionResult.Failure
                SessionAccess.Allowed -> Unit
            }
            val stored = local.get(userId, trackId) ?: return@withContext DownloadExecutionResult.Failure
            if (stored.item.status in setOf(
                    OfflineDownloadStatus.Paused,
                    OfflineDownloadStatus.Cancelled,
                    OfflineDownloadStatus.Completed,
                )
            ) return@withContext DownloadExecutionResult.Success
            local.setStatus(
                userId,
                trackId,
                OfflineDownloadStatus.Downloading,
                incrementAttempt = true,
                updatedAtEpochMillis = now(),
            )
            val request = when (val result = mediaDelivery.downloadRequest(trackId)) {
                is OfflineDownloadRequestResult.Ready -> result.request
                is OfflineDownloadRequestResult.Failure -> {
                    val failure = if (result.reason.name == "NotAuthenticated") {
                        OfflineDownloadFailure.NotAuthenticated
                    } else OfflineDownloadFailure.MediaUnavailable
                    fail(userId, trackId, failure)
                    return@withContext DownloadExecutionResult.Failure
                }
            }
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = NETWORK_TIMEOUT_MILLIS
                    readTimeout = NETWORK_TIMEOUT_MILLIS
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    request.requestHeaders().forEach { (name, value) ->
                        setRequestProperty(name, value)
                    }
                    setRequestProperty("X-Rakyzu-Audio-Quality", "standard")
                    setRequestProperty("X-Rakyzu-Download-Intent", "offline")
                }
                val status = connection.responseCode
                if (status !in 200..299) {
                    val failure = when (status) {
                        401 -> OfflineDownloadFailure.NotAuthenticated
                        403 -> OfflineDownloadFailure.NotEntitled
                        404 -> OfflineDownloadFailure.MediaUnavailable
                        else -> OfflineDownloadFailure.Network
                    }
                    fail(userId, trackId, failure)
                    return@withContext if (status >= 500 || status == 408 || status == 429) {
                        DownloadExecutionResult.Retry
                    } else DownloadExecutionResult.Failure
                }
                if (!connection.getHeaderField(OFFLINE_ALLOWED_HEADER).equals("true", true)) {
                    fail(userId, trackId, OfflineDownloadFailure.NotEntitled)
                    return@withContext DownloadExecutionResult.Failure
                }
                val total = connection.contentLengthLong.takeIf { it in 1..MAX_DOWNLOAD_BYTES }
                    ?: run {
                        fail(userId, trackId, OfflineDownloadFailure.MediaUnavailable)
                        return@withContext DownloadExecutionResult.Failure
                    }
                if (store.availableBytes() - total < MIN_OFFLINE_STORAGE_RESERVE_BYTES) {
                    fail(userId, trackId, OfflineDownloadFailure.InsufficientStorage)
                    return@withContext DownloadExecutionResult.Failure
                }
                val contentType = connection.contentType
                    ?.substringBefore(';')
                    ?.lowercase()
                    ?.takeIf { it.startsWith("audio/") || it == DEFAULT_CONTENT_TYPE }
                    ?: DEFAULT_CONTENT_TYPE
                val licenseExpiry = parseLicenseExpiry(
                    connection.getHeaderField(LICENSE_EXPIRY_HEADER),
                    now(),
                ) ?: run {
                    fail(userId, trackId, OfflineDownloadFailure.NotEntitled)
                    return@withContext DownloadExecutionResult.Failure
                }
                var lastReported = 0L
                val encrypted = connection.inputStream.use { input ->
                    store.write(userId, trackId, input, MAX_DOWNLOAD_BYTES) { downloaded ->
                        if (downloaded - lastReported >= PROGRESS_STEP_BYTES || downloaded == total) {
                            lastReported = downloaded
                            local.setProgress(userId, trackId, downloaded, total, now())
                        }
                    }
                }
                if (encrypted.plaintextBytes != total) {
                    store.delete(encrypted.fileToken)
                    fail(userId, trackId, OfflineDownloadFailure.Network)
                    return@withContext DownloadExecutionResult.Retry
                }
                val latestStatus = local.get(userId, trackId)?.item?.status
                if (latestStatus != OfflineDownloadStatus.Downloading) {
                    store.delete(encrypted.fileToken)
                    return@withContext DownloadExecutionResult.Success
                }
                local.complete(
                    userId,
                    trackId,
                    encrypted.plaintextBytes,
                    encrypted.fileToken,
                    contentType,
                    licenseExpiry,
                    now(),
                )
                DownloadExecutionResult.Success
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                fail(userId, trackId, OfflineDownloadFailure.Network)
                DownloadExecutionResult.Retry
            } finally {
                connection?.disconnect()
            }
        }

    private suspend fun restart(userId: String, trackId: String): OfflineDownloadActionResult {
        if (!ownsSession(userId)) return OfflineDownloadActionResult.Rejected(OfflineDownloadFailure.WrongAccount)
        val item = local.get(userId, trackId)
            ?: return OfflineDownloadActionResult.Rejected(OfflineDownloadFailure.InvalidRequest)
        if (item.item.status == OfflineDownloadStatus.Completed) return OfflineDownloadActionResult.Accepted
        local.setStatus(userId, trackId, OfflineDownloadStatus.Queued, updatedAtEpochMillis = now())
        schedule(userId, trackId, local.allowMobileDownloads(userId))
        return OfflineDownloadActionResult.Accepted
    }

    private suspend fun transition(
        userId: String,
        trackId: String,
        status: OfflineDownloadStatus,
        action: () -> Unit,
    ): OfflineDownloadActionResult {
        if (!ownsSession(userId)) return OfflineDownloadActionResult.Rejected(OfflineDownloadFailure.WrongAccount)
        if (local.get(userId, trackId) == null) {
            return OfflineDownloadActionResult.Rejected(OfflineDownloadFailure.InvalidRequest)
        }
        local.setStatus(userId, trackId, status, updatedAtEpochMillis = now())
        action()
        return OfflineDownloadActionResult.Accepted
    }

    private suspend fun fail(userId: String, trackId: String, failure: OfflineDownloadFailure) {
        local.setStatus(
            userId,
            trackId,
            OfflineDownloadStatus.Failed,
            failureCode = failure.name,
            updatedAtEpochMillis = now(),
        )
    }

    private fun schedule(userId: String, trackId: String, allowMobile: Boolean) {
        val request = OneTimeWorkRequestBuilder<OfflineDownloadWorker>()
            .setInputData(workDataOf(USER_ID_INPUT to userId, TRACK_ID_INPUT to trackId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(requiredDownloadNetworkType(allowMobile))
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(accountTag(userId))
            .build()
        workManager.enqueueUniqueWork(workName(userId, trackId), ExistingWorkPolicy.REPLACE, request)
    }

    private fun ownsSession(userId: String): Boolean =
        sessionAccess(userId) == SessionAccess.Allowed

    private fun sessionAccess(userId: String): SessionAccess {
        if (userId.isBlank()) return SessionAccess.DifferentAccount
        val active = activeUserId() ?: return SessionAccess.Pending
        return if (active.equals(userId, ignoreCase = true)) {
            SessionAccess.Allowed
        } else {
            SessionAccess.DifferentAccount
        }
    }

    private fun parseLicenseExpiry(value: String?, currentTime: Long): Long? {
        val parsed = try {
            value?.let(Instant::parse)?.toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
        return parsed?.takeIf { it > currentTime }
    }

    private companion object {
        const val MAX_DOWNLOAD_BYTES = 512L * 1024L * 1024L
        const val PROGRESS_STEP_BYTES = 256L * 1024L
        const val NETWORK_TIMEOUT_MILLIS = 30_000
        const val DEFAULT_CONTENT_TYPE = "application/octet-stream"
        const val OFFLINE_ALLOWED_HEADER = "X-Rakyzu-Offline-Allowed"
        const val LICENSE_EXPIRY_HEADER = "X-Rakyzu-Offline-License-Expires"
    }

    private enum class SessionAccess { Pending, DifferentAccount, Allowed }
}

internal fun workName(userId: String, trackId: String): String =
    "rakyzu-download-${safeWorkPart(userId)}-${safeWorkPart(trackId)}"

internal fun accountTag(userId: String): String = "rakyzu-download-account-${safeWorkPart(userId)}"

internal fun requiredDownloadNetworkType(allowMobile: Boolean): NetworkType =
    if (allowMobile) NetworkType.CONNECTED else NetworkType.UNMETERED

private fun safeWorkPart(value: String): String = value.lowercase().replace(Regex("[^a-z0-9-]"), "_")

internal const val USER_ID_INPUT = "user_id"
internal const val TRACK_ID_INPUT = "track_id"

internal data object UnavailableOfflineDownloadRepository : ExecutableOfflineDownloadRepository {
    override fun observe(userId: String) = flowOf(OfflineDownloadsSnapshot())
    override suspend fun enqueueCollection(userId: String, kind: DownloadCollectionKind,
        collectionId: String, collectionTitle: String, tracks: List<Track>) = unavailable()
    override suspend fun pause(userId: String, trackId: String) = unavailable()
    override suspend fun resume(userId: String, trackId: String) = unavailable()
    override suspend fun cancel(userId: String, trackId: String) = unavailable()
    override suspend fun retry(userId: String, trackId: String) = unavailable()
    override suspend fun setAllowMobileDownloads(userId: String, allow: Boolean) = unavailable()
    override suspend fun clearCompleted(userId: String) = unavailable()
    override fun openForPlayback(userId: String, trackId: String): OfflineMediaAsset? = null
    override suspend fun execute(userId: String, trackId: String) = DownloadExecutionResult.Failure
    private fun unavailable() = OfflineDownloadActionResult.Rejected(OfflineDownloadFailure.NotAuthenticated)
}
