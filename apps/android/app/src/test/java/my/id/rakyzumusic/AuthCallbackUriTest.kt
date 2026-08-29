package my.id.rakyzumusic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthCallbackUriTest {
    @Test
    fun exactPasswordRecoveryCallbackIsAccepted() {
        assertTrue(
            isPasswordRecoveryCallback(
                "my.id.rakyzumusic://auth/recovery?code=pkce_code-123",
            ),
        )
    }

    @Test
    fun missingOrMalformedAuthorizationCodeIsRejected() {
        assertFalse(isPasswordRecoveryCallback(null))
        assertFalse(isPasswordRecoveryCallback("my.id.rakyzumusic://auth/recovery"))
        assertFalse(isPasswordRecoveryCallback("my.id.rakyzumusic://auth/recovery?code="))
        assertFalse(isPasswordRecoveryCallback("my.id.rakyzumusic://auth/recovery?code=%0A"))
        assertFalse(
            isPasswordRecoveryCallback(
                "my.id.rakyzumusic://auth/recovery?code=first&code=second",
            ),
        )
    }

    @Test
    fun callbackOriginPathAndFragmentMustMatchTheAllowlist() {
        assertFalse(isPasswordRecoveryCallback("https://auth/recovery?code=valid"))
        assertFalse(isPasswordRecoveryCallback("my.id.rakyzumusic://evil/recovery?code=valid"))
        assertFalse(isPasswordRecoveryCallback("my.id.rakyzumusic://auth@evil/recovery?code=valid"))
        assertFalse(isPasswordRecoveryCallback("my.id.rakyzumusic://auth:443/recovery?code=valid"))
        assertFalse(isPasswordRecoveryCallback("my.id.rakyzumusic://auth/recovery/next?code=valid"))
        assertFalse(isPasswordRecoveryCallback("my.id.rakyzumusic://auth/recovery?code=valid#extra"))
        assertFalse(isPasswordRecoveryCallback("MY.ID.RAKYZUMUSIC://auth/recovery?code=valid"))
    }

    @Test
    fun unexpectedQueryParametersAreRejected() {
        assertFalse(
            isPasswordRecoveryCallback(
                "my.id.rakyzumusic://auth/recovery?code=valid&next=javascript%3Aalert(1)",
            ),
        )
    }
}
