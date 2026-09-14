package my.id.rakyzumusic.core.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackPreferencesTest {
    @Test
    fun wifiAndMobileNetworksResolveTheirIndependentSelections() {
        val preferences = PlaybackPreferences(
            wifiQuality = PlaybackQuality.High,
            mobileQuality = PlaybackQuality.Standard,
        )

        assertEquals(PlaybackQuality.High, preferences.effectiveQuality(isMetered = false))
        assertEquals(PlaybackQuality.Standard, preferences.effectiveQuality(isMetered = true))
    }

    @Test
    fun dataSaverForcesLowOnlyOnMeteredNetworks() {
        val preferences = PlaybackPreferences(
            wifiQuality = PlaybackQuality.High,
            mobileQuality = PlaybackQuality.High,
            dataSaverEnabled = true,
        )

        assertEquals(PlaybackQuality.High, preferences.effectiveQuality(isMetered = false))
        assertEquals(PlaybackQuality.Low, preferences.effectiveQuality(isMetered = true))
    }
}
