package my.id.rakyzumusic.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStreamRequestTest {
    @Test
    fun canonicalTrackUriResolvesAuthenticatedHttpsRequest() {
        val resolver = resolver(
            PlaybackStreamRequestResult.Ready(
                PlaybackNetworkRequest(
                    url = "https://api.rakyzu.my.id/v1/tracks/$TRACK_ID/stream",
                    headers = mapOf("Authorization" to "Bearer listener-token"),
                ),
            ),
        )

        val request = resolver.resolve("rakyzu://tracks/${TRACK_ID.uppercase()}")

        assertEquals(TRACK_ID, requestedTrackId)
        assertEquals("Bearer listener-token", request.headers["Authorization"])
    }

    @Test
    fun arbitraryUriNeverReachesRequestProvider() {
        val resolver = resolver(
            PlaybackStreamRequestResult.Failure(PlaybackRequestFailure.InvalidMediaId),
        )

        val exception = assertThrows(PlaybackRequestException::class.java) {
            resolver.resolve("https://attacker.example/audio.mp3")
        }

        assertEquals(PlaybackRequestFailure.InvalidMediaId, exception.reason)
        assertEquals(null, requestedTrackId)
    }

    @Test
    fun missingAuthorizationHeaderFailsClosed() {
        val resolver = resolver(
            PlaybackStreamRequestResult.Ready(
                PlaybackNetworkRequest(
                    url = "https://api.rakyzu.my.id/v1/tracks/$TRACK_ID/stream",
                    headers = emptyMap(),
                ),
            ),
        )

        val exception = assertThrows(PlaybackRequestException::class.java) {
            resolver.resolve("rakyzu://tracks/$TRACK_ID")
        }

        assertEquals(PlaybackRequestFailure.InvalidConfiguration, exception.reason)
    }

    @Test
    fun credentialsAreRedactedFromStringRepresentation() {
        val request = PlaybackNetworkRequest(
            url = "https://api.rakyzu.my.id/v1/tracks/$TRACK_ID/stream",
            headers = mapOf("Authorization" to "Bearer listener-token"),
        )

        assertFalse(request.toString().contains("listener-token"))
        assertTrue(request.toString().contains("REDACTED"))
    }

    private var requestedTrackId: String? = null

    private fun resolver(result: PlaybackStreamRequestResult): AuthenticatedPlaybackRequestResolver =
        AuthenticatedPlaybackRequestResolver { trackId ->
            requestedTrackId = trackId
            result
        }

    private companion object {
        const val TRACK_ID = "a3000000-0000-4000-8000-000000000001"
    }
}
