package my.id.rakyzumusic.core.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDeliveryRepositoryTest {
    @Test
    fun authenticatedRequestUsesApiPathAndAuthorizationHeader() {
        val repository = repository(accessToken = "listener-token")

        val result = repository.streamRequest(TRACK_ID.uppercase())

        assertTrue(result is MediaStreamRequestResult.Ready)
        val request = (result as MediaStreamRequestResult.Ready).request
        assertEquals(
            "https://api.rakyzu.my.id/v1/tracks/$TRACK_ID/stream",
            request.url,
        )
        assertEquals("Bearer listener-token", request.requestHeaders()["Authorization"])
    }

    @Test
    fun accessTokenIsRedactedFromStringRepresentation() {
        val result = repository(accessToken = "listener-token").streamRequest(TRACK_ID)
        val request = (result as MediaStreamRequestResult.Ready).request

        assertFalse(request.toString().contains("listener-token"))
        assertTrue(request.toString().contains("REDACTED"))
    }

    @Test
    fun missingSessionFailsClosed() {
        val result = repository(accessToken = null).streamRequest(TRACK_ID)

        assertEquals(
            MediaStreamRequestResult.Failure(MediaStreamRequestFailure.NotAuthenticated),
            result,
        )
    }

    @Test
    fun malformedTrackIdCannotChangeTheApiPath() {
        val result = repository(accessToken = "listener-token")
            .streamRequest("../../private-object")

        assertEquals(
            MediaStreamRequestResult.Failure(MediaStreamRequestFailure.InvalidTrackId),
            result,
        )
    }

    @Test
    fun configurationOnlyAcceptsAnHttpsOrigin() {
        for (baseUrl in listOf(
            "http://api.rakyzu.my.id",
            "https://user@example.com",
            "https://api.rakyzu.my.id/private",
            "https://api.rakyzu.my.id?token=value",
            "not a url",
        )) {
            val result = repository(baseUrl = baseUrl, accessToken = "listener-token")
                .streamRequest(TRACK_ID)
            assertEquals(
                MediaStreamRequestResult.Failure(
                    MediaStreamRequestFailure.InvalidConfiguration,
                ),
                result,
            )
        }
    }

    private fun repository(
        baseUrl: String = "https://api.rakyzu.my.id/",
        accessToken: String?,
    ): MediaDeliveryRepository = AuthenticatedMediaDeliveryRepository(
        configuration = RakyzuApiConfiguration(baseUrl),
        accessTokenProvider = AccessTokenProvider { accessToken },
    )

    private companion object {
        const val TRACK_ID = "a3000000-0000-4000-8000-000000000001"
    }
}
