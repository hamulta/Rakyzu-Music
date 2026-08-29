package my.id.rakyzumusic.core.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackProgressSamplerTest {
    @Test
    fun samplerRunsOnlyWhilePlaybackIsActiveAndDoesNotDuplicateJobs() {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        var isPlaying = true
        var sampleCount = 0
        val sampler = PlaybackProgressSampler(
            scope = scope,
            intervalMs = 500L,
            isPlaybackActive = { isPlaying },
            sample = { sampleCount += 1 },
        )

        sampler.synchronize()
        sampler.synchronize()
        scope.runCurrent()

        assertTrue(sampler.isRunning)
        assertEquals(1, sampleCount)

        scope.advanceTimeBy(500L)
        scope.runCurrent()

        assertEquals(2, sampleCount)

        isPlaying = false
        sampler.synchronize()
        scope.advanceTimeBy(1_000L)
        scope.runCurrent()

        assertFalse(sampler.isRunning)
        assertEquals(2, sampleCount)
    }

    @Test
    fun inactivePlaybackNeverStartsAProgressJob() {
        val scope = TestScope(StandardTestDispatcher())
        var sampleCount = 0
        val sampler = PlaybackProgressSampler(
            scope = scope,
            intervalMs = 500L,
            isPlaybackActive = { false },
            sample = { sampleCount += 1 },
        )

        sampler.synchronize()
        scope.runCurrent()

        assertFalse(sampler.isRunning)
        assertEquals(0, sampleCount)
    }
}
