package my.id.rakyzumusic.core.data.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileContextPayloadTest {
    @Test
    fun `decodes the scalar json object returned by production rpc`() {
        val profile = decodeProfileContextPayload(
            """
            {
              "role": null,
              "artist": null,
              "userId": "11111111-1111-1111-1111-111111111111",
              "verified": false,
              "displayName": "Rakyzu Listener",
              "identityKind": "listener",
              "avatarVersion": null,
              "appearanceMode": "role",
              "avatarAvailable": false,
              "onboardingCompleted": true
            }
            """.trimIndent(),
        )

        assertEquals("11111111-1111-1111-1111-111111111111", profile.userId)
        assertEquals("Rakyzu Listener", profile.displayName)
        assertEquals("listener", profile.identityKind)
        assertTrue(profile.onboardingCompleted)
        assertFalse(profile.verified)
        assertFalse(profile.avatarAvailable)
        assertNull(profile.role)
        assertNull(profile.artist)
    }

    @Test
    fun `decodes staff context and ignores forward compatible fields`() {
        val profile = decodeProfileContextPayload(
            """
            {
              "userId": "22222222-2222-2222-2222-222222222222",
              "displayName": "Rakyzu Executive",
              "onboardingCompleted": true,
              "avatarAvailable": true,
              "avatarVersion": "2026-09-24T00:00:00Z",
              "appearanceMode": "role",
              "identityKind": "staff",
              "role": "c_executive",
              "verified": true,
              "artist": null,
              "futureField": "safe-to-ignore"
            }
            """.trimIndent(),
        )

        assertEquals("staff", profile.identityKind)
        assertEquals("c_executive", profile.role)
        assertTrue(profile.verified)
        assertTrue(profile.avatarAvailable)
    }
}
