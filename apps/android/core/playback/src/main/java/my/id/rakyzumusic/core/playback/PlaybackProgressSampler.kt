package my.id.rakyzumusic.core.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class PlaybackProgressSampler(
    private val scope: CoroutineScope,
    private val intervalMs: Long,
    private val isPlaybackActive: () -> Boolean,
    private val sample: () -> Unit,
) {
    private var samplingJob: Job? = null

    val isRunning: Boolean
        get() = samplingJob?.isActive == true

    fun synchronize() {
        if (!isPlaybackActive()) {
            stop()
            return
        }
        if (samplingJob?.isActive == true) return

        samplingJob = scope.launch {
            while (isActive && isPlaybackActive()) {
                sample()
                delay(intervalMs)
            }
        }
    }

    fun stop() {
        samplingJob?.cancel()
        samplingJob = null
    }
}
