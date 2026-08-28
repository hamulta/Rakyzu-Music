package my.id.rakyzumusic.core.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthCredentialsTest {
    @Test
    fun validCredentialsAreNormalized() {
        val result = AuthCredentials(
            email = "  Listener@Rakyzu.My.Id ",
            password = "Secure-Voice1",
        ).validate()

        assertEquals(
            CredentialValidation.Valid(
                AuthCredentials(
                    email = "listener@rakyzu.my.id",
                    password = "Secure-Voice1",
                ),
            ),
            result,
        )
    }

    @Test
    fun malformedEmailIsRejected() {
        val result = AuthCredentials("not-an-email", "Secure-Voice1").validate()

        assertTrue(result is CredentialValidation.InvalidEmail)
    }

    @Test
    fun shortPasswordIsRejected() {
        val result = AuthCredentials("listener@rakyzu.my.id", "short").validate()

        assertTrue(result is CredentialValidation.WeakPassword)
    }

    @Test
    fun passwordWithoutRequiredCharacterGroupsIsRejected() {
        val result = AuthCredentials("listener@rakyzu.my.id", "secure-password").validate()

        assertTrue(result is CredentialValidation.WeakPassword)
    }
}
