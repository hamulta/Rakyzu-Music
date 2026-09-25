package my.id.rakyzumusic.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineDownloadTest {
    @Test
    fun progressIsBoundedAndUnknownTotalsStayIndeterminate() {
        assertEquals(0.5f, item(50, 100).progressFraction)
        assertEquals(1f, item(150, 100).progressFraction)
        assertNull(item(50, null).progressFraction)
        assertNull(item(50, 0).progressFraction)
    }

    @Test
    fun storageCapacityPreservesTheOneGigabyteReserve() {
        assertFalse(OfflineDownloadStorage(availableBytes = MIN_OFFLINE_STORAGE_RESERVE_BYTES)
            .hasDownloadCapacity)
        assertTrue(OfflineDownloadStorage(availableBytes = MIN_OFFLINE_STORAGE_RESERVE_BYTES + 1)
            .hasDownloadCapacity)
    }

    private fun item(downloaded: Long, total: Long?) = OfflineDownloadItem(
        userId = "listener",
        trackId = "track",
        title = "Signal",
        artist = "Rakyzu",
        collectionKind = DownloadCollectionKind.Album,
        collectionId = "album",
        collectionTitle = "Album",
        status = OfflineDownloadStatus.Downloading,
        downloadedBytes = downloaded,
        totalBytes = total,
        updatedAtEpochMillis = 1,
    )
}
