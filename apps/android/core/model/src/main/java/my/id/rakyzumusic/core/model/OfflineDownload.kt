package my.id.rakyzumusic.core.model

import java.io.InputStream

enum class DownloadCollectionKind {
    Album,
    Playlist,
}

enum class OfflineDownloadStatus {
    Queued,
    Downloading,
    Paused,
    Completed,
    Failed,
    Cancelled,
    Unavailable,
}

enum class DownloadSignOutPolicy {
    RemoveFromDevice,
    KeepEncrypted,
}

data class OfflineDownloadItem(
    val userId: String,
    val trackId: String,
    val title: String,
    val artist: String,
    val collectionKind: DownloadCollectionKind,
    val collectionId: String,
    val collectionTitle: String,
    val status: OfflineDownloadStatus,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val updatedAtEpochMillis: Long,
    val failureCode: String? = null,
) {
    val progressFraction: Float?
        get() = totalBytes
            ?.takeIf { it > 0L }
            ?.let { (downloadedBytes.toDouble() / it.toDouble()).coerceIn(0.0, 1.0).toFloat() }
}

data class OfflineDownloadStorage(
    val encryptedBytes: Long = 0L,
    val availableBytes: Long = 0L,
    val reserveBytes: Long = MIN_OFFLINE_STORAGE_RESERVE_BYTES,
) {
    val hasDownloadCapacity: Boolean
        get() = availableBytes > reserveBytes
}

data class OfflineDownloadsSnapshot(
    val items: List<OfflineDownloadItem> = emptyList(),
    val allowMobileDownloads: Boolean = false,
    val storage: OfflineDownloadStorage = OfflineDownloadStorage(),
    val signOutPolicy: DownloadSignOutPolicy = DownloadSignOutPolicy.RemoveFromDevice,
) {
    val completedCount: Int
        get() = items.asSequence()
            .filter { it.status == OfflineDownloadStatus.Completed }
            .distinctBy(OfflineDownloadItem::trackId)
            .count()

    fun collectionItems(kind: DownloadCollectionKind, id: String): List<OfflineDownloadItem> =
        items.filter { it.collectionKind == kind && it.collectionId == id }

    val diagnostics: OfflineDownloadDiagnostics
        get() = OfflineDownloadDiagnostics.from(items)
}

/** Aggregate-only diagnostics. Titles, account IDs, track IDs and file tokens never leave storage. */
data class OfflineDownloadDiagnostics(
    val totalCount: Int,
    val activeCount: Int,
    val readyCount: Int,
    val attentionCount: Int,
) {
    companion object {
        fun from(items: List<OfflineDownloadItem>): OfflineDownloadDiagnostics =
            OfflineDownloadDiagnostics(
                totalCount = items.distinctBy(OfflineDownloadItem::trackId).size,
                activeCount = items.countDistinctWithStatus(
                    OfflineDownloadStatus.Queued,
                    OfflineDownloadStatus.Downloading,
                ),
                readyCount = items.countDistinctWithStatus(OfflineDownloadStatus.Completed),
                attentionCount = items.countDistinctWithStatus(
                    OfflineDownloadStatus.Failed,
                    OfflineDownloadStatus.Unavailable,
                ),
            )
    }
}

private fun List<OfflineDownloadItem>.countDistinctWithStatus(
    vararg statuses: OfflineDownloadStatus,
): Int = asSequence()
    .filter { it.status in statuses }
    .distinctBy(OfflineDownloadItem::trackId)
    .count()

/**
 * A decrypted, account-authorized stream. Callers must close [input] after playback.
 * No filesystem path, object key, signed URL, or credential crosses this boundary.
 */
data class OfflineMediaAsset(
    val input: InputStream,
    val plaintextLength: Long,
    val contentType: String,
)

const val MIN_OFFLINE_STORAGE_RESERVE_BYTES: Long = 1_073_741_824L
