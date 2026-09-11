package my.id.rakyzumusic.core.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthCallbackTest {
    @Test
    fun exactEmailConfirmationPkceCallbackIsParsed() {
        val callback = parseAuthCallback("my.id.rakyzumusic://auth?code=confirm%20code-123")

        assertTrue(callback is AuthCallback.AuthorizationCode)
        callback as AuthCallback.AuthorizationCode
        assertEquals(AuthCallbackPurpose.EmailConfirmation, callback.purpose)
        assertEquals("confirm code-123", callback.code)
        assertEquals(
            "AuthorizationCode(purpose=EmailConfirmation, code=redacted)",
            callback.toString(),
        )
    }

    @Test
    fun exactPasswordRecoveryPkceCallbackIsParsed() {
        val callback = parseAuthCallback(
            "my.id.rakyzumusic://auth/recovery?code=recovery%20code-123",
        )

        assertTrue(callback is AuthCallback.AuthorizationCode)
        callback as AuthCallback.AuthorizationCode
        assertEquals(AuthCallbackPurpose.PasswordRecovery, callback.purpose)
        assertEquals("recovery code-123", callback.code)
    }

    @Test
    fun implicitCallbacksRequireTheTypeMatchingTheirPath() {
        val confirmation = parseAuthCallback(
            "my.id.rakyzumusic://auth#" +
                "access_token=confirm-access&refresh_token=confirm-refresh&type=signup",
        )
        val recovery = parseAuthCallback(
            "my.id.rakyzumusic://auth/recovery#" +
                "access_token=recovery-access&refresh_token=recovery-refresh&type=recovery",
        )

        assertTrue(confirmation is AuthCallback.ImplicitSession)
        assertEquals(AuthCallbackPurpose.EmailConfirmation, confirmation?.purpose)
        assertTrue(recovery is AuthCallback.ImplicitSession)
        assertEquals(AuthCallbackPurpose.PasswordRecovery, recovery?.purpose)
        assertNull(
            parseAuthCallback(
                "my.id.rakyzumusic://auth/recovery#" +
                    "access_token=access&refresh_token=refresh&type=signup",
            ),
        )
        assertNull(
            parseAuthCallback(
                "my.id.rakyzumusic://auth#" +
                    "access_token=access&refresh_token=refresh&type=recovery",
            ),
        )
    }

    @Test
    fun malformedCallbacksAreRejected() {
        listOf(
            null,
            "my.id.rakyzumusic://auth",
            "my.id.rakyzumusic://auth?code=",
            "my.id.rakyzumusic://auth?code=%0A",
            "my.id.rakyzumusic://auth?code=first&code=second",
            "https://auth?code=valid",
            "my.id.rakyzumusic://evil?code=valid",
            "my.id.rakyzumusic://auth@evil?code=valid",
            "my.id.rakyzumusic://auth:443?code=valid",
            "my.id.rakyzumusic://auth/unknown?code=valid",
            "my.id.rakyzumusic://auth/recovery/next?code=valid",
            "my.id.rakyzumusic://auth?code=valid#extra",
            "my.id.rakyzumusic://auth#access_token=access&type=signup",
            "my.id.rakyzumusic://auth#junk&access_token=access&refresh_token=refresh&type=signup",
            "my.id.rakyzumusic://auth#access_token=access&refresh_token=refresh&type=signup&next=bad",
            "MY.ID.RAKYZUMUSIC://auth?code=valid",
            "my.id.rakyzumusic://auth?code=valid&next=unexpected",
        ).forEach { assertNull(it, parseAuthCallback(it)) }
    }
}
