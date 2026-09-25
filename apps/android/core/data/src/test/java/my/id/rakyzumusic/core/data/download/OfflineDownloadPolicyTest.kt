package my.id.rakyzumusic.core.data.download

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
