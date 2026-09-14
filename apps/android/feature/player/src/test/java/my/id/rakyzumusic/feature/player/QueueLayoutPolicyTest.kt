package my.id.rakyzumusic.feature.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueLayoutPolicyTest {
    @Test
    fun compactLayoutProtectsNarrowScreensAndLargeText() {
        assertTrue(shouldUseCompactQueueLayout(availableWidthDp = 320f, fontScale = 1f))
        assertTrue(shouldUseCompactQueueLayout(availableWidthDp = 600f, fontScale = 1.3f))
        assertFalse(shouldUseCompactQueueLayout(availableWidthDp = 400f, fontScale = 1.2f))
    }
}
