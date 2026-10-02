package my.id.rakyzumusic.feature.auth

import android.content.Context
import android.content.MutableContextWrapper
import android.util.Base64
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.CancellationException

internal sealed interface NativeGoogleAuthResult {
    class Success(
        val idToken: String,
        val rawNonce: String,
    ) : NativeGoogleAuthResult {
        override fun toString(): String = "Success(idToken=redacted, rawNonce=redacted)"
    }

    data object Cancelled : NativeGoogleAuthResult

    data object NoAccount : NativeGoogleAuthResult

    data object InvalidCredential : NativeGoogleAuthResult

    data object Unavailable : NativeGoogleAuthResult

    data object InvalidConfiguration : NativeGoogleAuthResult
}

internal suspend fun requestNativeGoogleCredential(
    context: Context,
    webClientId: String,
): NativeGoogleAuthResult {
    if (!webClientId.isValidGoogleClientId()) {
        return NativeGoogleAuthResult.InvalidConfiguration
    }

    val rawNonce = generateNonce()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(
            GetSignInWithGoogleOption.Builder(webClientId)
                .setNonce(rawNonce.sha256Hex())
                .build(),
        )
        .build()

    return try {
        val result = CredentialManager.create(context).getCredential(
            context = MutableContextWrapper(context),
            request = request,
        )
        val credential = result.credential as? CustomCredential
            ?: return NativeGoogleAuthResult.InvalidCredential
        if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return NativeGoogleAuthResult.InvalidCredential
        }
        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
        NativeGoogleAuthResult.Success(
            idToken = googleCredential.idToken,
            rawNonce = rawNonce,
        )
    } catch (error: GetCredentialCancellationException) {
        NativeGoogleAuthResult.Cancelled
    } catch (error: NoCredentialException) {
        NativeGoogleAuthResult.NoAccount
    } catch (error: GoogleIdTokenParsingException) {
        NativeGoogleAuthResult.InvalidCredential
    } catch (error: GetCredentialException) {
        NativeGoogleAuthResult.Unavailable
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        NativeGoogleAuthResult.Unavailable
    }
}

suspend fun clearNativeCredentialState(context: Context): Boolean = try {
    CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    true
} catch (error: CancellationException) {
    throw error
} catch (error: Throwable) {
    false
}

private fun generateNonce(): String {
    val bytes = ByteArray(NONCE_BYTE_LENGTH)
    SecureRandom().nextBytes(bytes)
    return Base64.encodeToString(
        bytes,
        Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE,
    )
}

private fun String.sha256Hex(): String = MessageDigest
    .getInstance("SHA-256")
    .digest(toByteArray(Charsets.UTF_8))
    .joinToString(separator = "") { byte ->
        val value = byte.toInt() and 0xff
        buildString(2) {
            append(HEX_DIGITS[value ushr 4])
            append(HEX_DIGITS[value and 0x0f])
        }
    }

private fun String.isValidGoogleClientId(): Boolean =
    endsWith(GOOGLE_CLIENT_ID_SUFFIX) && none(Char::isWhitespace)

private const val NONCE_BYTE_LENGTH = 32
private const val GOOGLE_CLIENT_ID_SUFFIX = ".apps.googleusercontent.com"
private const val HEX_DIGITS = "0123456789abcdef"
