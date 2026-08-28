package my.id.rakyzumusic.core.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthCredentialsTest {
    @Test
    fun validCredentialsAreNormalized() {
        val result = AuthCredentials(
            email = "  Listener@Rakyzu.My.Id ",
            password = "secure-voice",
        ).validate()

        assertEquals(
            CredentialValidation.Valid(
                AuthCredentials(
                    email = "listener@rakyzu.my.id",
                    password = "secure-voice",
                ),
            ),
            result,
        )
    }

    @Test
    fun malformedEmailIsRejected() {
        val result = AuthCredentials("not-an-email", "secure-voice").validate()

        assertTrue(result is CredentialValidation.InvalidEmail)
    }

    @Test
    fun shortPasswordIsRejected() {
        val result = AuthCredentials("listener@rakyzu.my.id", "short").validate()

        assertTrue(result is CredentialValidation.WeakPassword)
    }
}
