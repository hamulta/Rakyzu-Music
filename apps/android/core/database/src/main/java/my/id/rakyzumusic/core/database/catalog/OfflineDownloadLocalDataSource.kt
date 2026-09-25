package my.id.rakyzumusic.core.database.catalog

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import my.id.rakyzumusic.core.model.DownloadCollectionKind
import my.id.rakyzumusic.core.model.OfflineDownloadItem
import my.id.rakyzumusic.core.model.OfflineDownloadStatus

data class StoredOfflineDownload(
    val item: OfflineDownloadItem,
    val fileToken: String?,
    val contentType: String?,
    val licenseExpiresAtEpochMillis: Long?,
    val attemptCount: Int,
    val requestedAtEpochMillis: Long,
)

data class StoredOfflineDownloadsSnapshot(
    val items: List<StoredOfflineDownload>,
    val allowMobileDownloads: Boolean,
)

data class StoredOfflineDownloadCollection(
    val userId: String,
    val kind: DownloadCollectionKind,
    val collectionId: String,
    val collectionTitle: String,
    val trackId: String,
)

interface OfflineDownloadLocalDataSource {
    fun observe(userId: String): Flow<StoredOfflineDownloadsSnapshot>
    suspend fun get(userId: String, trackId: String): StoredOfflineDownload?
    suspend fun getAll(userId: String): List<StoredOfflineDownload>
    suspend fun upsert(items: List<StoredOfflineDownload>)
    suspend fun linkCollections(items: List<StoredOfflineDownloadCollection>)
    suspend fun setStatus(
        userId: String,
        trackId: String,
        status: OfflineDownloadStatus,
        failureCode: String? = null,
        incrementAttempt: Boolean = false,
        updatedAtEpochMillis: Long,
    ): Boolean
    suspend fun setProgress(
        userId: String,
        trackId: String,
        downloadedBytes: Long,
        totalBytes: Long?,
        updatedAtEpochMillis: Long,
    ): Boolean
    suspend fun complete(
        userId: String,
        trackId: String,
        totalBytes: Long,
        fileToken: String,
        contentType: String,
        licenseExpiresAtEpochMillis: Long,
        updatedAtEpochMillis: Long,
    ): Boolean
    suspend fun removeCompleted(userId: String): Int
    suspend fun allowMobileDownloads(userId: String): Boolean
    suspend fun setAllowMobileDownloads(userId: String, allow: Boolean)
}

internal class RoomOfflineDownloadLocalDataSource(
    database: RakyzuDatabase,
) : OfflineDownloadLocalDataSource {
    private val dao = database.catalogDao()

    override fun observe(userId: String): Flow<StoredOfflineDownloadsSnapshot> = combine(
        dao.observeOfflineDownloads(userId),
        dao.observeOfflineDownloadCollections(userId),
        dao.observeAllowMobileDownloads(userId),
    ) { entities, collections, allowMobile ->
        val downloads = entities.associateBy(OfflineDownloadEntity::trackId)
        val linked = collections.mapNotNull { collection ->
            downloads[collection.trackId]?.toStored()?.withCollection(collection)
        }
        val linkedTrackIds = collections.mapTo(mutableSetOf(), OfflineDownloadCollectionEntity::trackId)
        val unlinked = entities.filterNot { it.trackId in linkedTrackIds }
            .map(OfflineDownloadEntity::toStored)
        StoredOfflineDownloadsSnapshot(linked + unlinked, allowMobile == true)
    }

    override suspend fun get(userId: String, trackId: String): StoredOfflineDownload? =
        dao.getOfflineDownload(userId, trackId)?.toStored()

    override suspend fun getAll(userId: String): List<StoredOfflineDownload> =
        dao.getOfflineDownloads(userId).map(OfflineDownloadEntity::toStored)

    override suspend fun upsert(items: List<StoredOfflineDownload>) {
        if (items.isNotEmpty()) dao.insertOfflineDownloads(items.map(StoredOfflineDownload::toEntity))
    }

    override suspend fun linkCollections(items: List<StoredOfflineDownloadCollection>) {
        if (items.isNotEmpty()) dao.insertOfflineDownloadCollections(items.map {
            OfflineDownloadCollectionEntity(
                userId = it.userId,
                collectionKind = it.kind.name,
                collectionId = it.collectionId,
                trackId = it.trackId,
                collectionTitle = it.collectionTitle,
            )
        })
    }

    override suspend fun setStatus(
        userId: String,
        trackId: String,
        status: OfflineDownloadStatus,
        failureCode: String?,
        incrementAttempt: Boolean,
        updatedAtEpochMillis: Long,
    ): Boolean = dao.updateOfflineDownloadStatus(
        userId,
        trackId,
        status.name,
        failureCode,
        if (incrementAttempt) 1 else 0,
        updatedAtEpochMillis,
    ) == 1

    override suspend fun setProgress(
        userId: String,
        trackId: String,
        downloadedBytes: Long,
        totalBytes: Long?,
        updatedAtEpochMillis: Long,
    ): Boolean = dao.updateOfflineDownloadProgress(
        userId,
        trackId,
        OfflineDownloadStatus.Downloading.name,
        downloadedBytes.coerceAtLeast(0L),
        totalBytes?.coerceAtLeast(0L),
        updatedAtEpochMillis,
    ) == 1

    override suspend fun complete(
        userId: String,
        trackId: String,
        totalBytes: Long,
        fileToken: String,
        contentType: String,
        licenseExpiresAtEpochMillis: Long,
        updatedAtEpochMillis: Long,
    ): Boolean = dao.completeOfflineDownload(
        userId,
        trackId,
        OfflineDownloadStatus.Completed.name,
        totalBytes,
        fileToken,
        contentType,
        licenseExpiresAtEpochMillis,
        updatedAtEpochMillis,
    ) == 1

    override suspend fun removeCompleted(userId: String): Int =
        dao.deleteOfflineDownloadsWithStatus(userId, OfflineDownloadStatus.Completed.name)

    override suspend fun allowMobileDownloads(userId: String): Boolean =
        dao.getAllowMobileDownloads(userId) == true

    override suspend fun setAllowMobileDownloads(userId: String, allow: Boolean) {
        dao.insertOfflineDownloadPreference(OfflineDownloadPreferenceEntity(userId, allow))
    }
}

private fun OfflineDownloadEntity.toStored(): StoredOfflineDownload = StoredOfflineDownload(
    item = OfflineDownloadItem(
        userId = userId,
        trackId = trackId,
        title = title,
        artist = artist,
        collectionKind = enumValueOrDefault(collectionKind, DownloadCollectionKind.Album),
        collectionId = collectionId,
        collectionTitle = collectionTitle,
        status = enumValueOrDefault(status, OfflineDownloadStatus.Failed),
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
        updatedAtEpochMillis = updatedAtEpochMillis,
        failureCode = failureCode,
    ),
    fileToken = fileToken,
    contentType = contentType,
    licenseExpiresAtEpochMillis = licenseExpiresAtEpochMillis,
    attemptCount = attemptCount,
    requestedAtEpochMillis = requestedAtEpochMillis,
)

private fun StoredOfflineDownload.toEntity(): OfflineDownloadEntity = OfflineDownloadEntity(
    userId = item.userId,
    trackId = item.trackId,
    title = item.title,
    artist = item.artist,
    collectionKind = item.collectionKind.name,
    collectionId = item.collectionId,
    collectionTitle = item.collectionTitle,
    status = item.status.name,
    downloadedBytes = item.downloadedBytes,
    totalBytes = item.totalBytes,
    fileToken = fileToken,
    contentType = contentType,
    licenseExpiresAtEpochMillis = licenseExpiresAtEpochMillis,
    attemptCount = attemptCount,
    failureCode = item.failureCode,
    requestedAtEpochMillis = requestedAtEpochMillis,
    updatedAtEpochMillis = item.updatedAtEpochMillis,
)

private fun StoredOfflineDownload.withCollection(
    collection: OfflineDownloadCollectionEntity,
): StoredOfflineDownload = copy(
    item = item.copy(
        collectionKind = enumValueOrDefault(collection.collectionKind, item.collectionKind),
        collectionId = collection.collectionId,
        collectionTitle = collection.collectionTitle,
    ),
)

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: fallback
