package my.id.rakyzumusic.core.data.download

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineDownloadPolicyTest {
    @Test
    fun wifiIsDefaultAndMobileRequiresExplicitOptIn() {
        assertEquals(NetworkType.UNMETERED, requiredDownloadNetworkType(false))
        assertEquals(NetworkType.CONNECTED, requiredDownloadNetworkType(true))
    }

    @Test
    fun uniqueWorkNamesAreAccountScopedAndContainNoRawSeparators() {
        val first = workName("listener/one", "track:one")
        val second = workName("listener/two", "track:one")

        assertFalse(first.contains('/'))
        assertFalse(first.contains(':'))
        assertFalse(first == second)
    }

    @Test
    fun quotaCountsOnlyNewDistinctTracksAndBoundsCollectionSize() {
        assertTrue(hasOfflineQuotaCapacity(setOf("one"), listOf("one", "two", "two")))
        assertFalse(hasOfflineQuotaCapacity(emptySet(), List(501) { "track-$it" }))
        assertFalse(hasOfflineQuotaCapacity((1..2_000).map { "track-$it" }.toSet(), listOf("new")))
    }

    @Test
    fun contentRevisionRejectsHeaderInjectionAndUnboundedValues() {
        assertEquals("etag:one", sanitizeContentRevision(" etag:one "))
        assertNull(sanitizeContentRevision("etag\r\ninjected"))
        assertNull(sanitizeContentRevision("x".repeat(129)))
    }
}
