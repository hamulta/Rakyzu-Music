package my.id.rakyzumusic.core.data.auth

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

enum class AuthCallbackPurpose {
    EmailConfirmation,
    PasswordRecovery,
}

sealed interface AuthCallback {
    val purpose: AuthCallbackPurpose

    class AuthorizationCode(
        override val purpose: AuthCallbackPurpose,
        val code: String,
    ) : AuthCallback {
        override fun toString() = "AuthorizationCode(purpose=$purpose, code=redacted)"
    }

    class ImplicitSession(
        override val purpose: AuthCallbackPurpose,
        val accessToken: String,
        val refreshToken: String,
    ) : AuthCallback {
        override fun toString() = "ImplicitSession(purpose=$purpose, tokens=redacted)"
    }
}

fun parseAuthCallback(uriValue: String?): AuthCallback? {
    if (uriValue.isNullOrBlank() || uriValue.length > MAX_CALLBACK_LENGTH) return null
    val uri = runCatching { URI(uriValue) }.getOrNull() ?: return null
    if (uri.scheme != CALLBACK_SCHEME || uri.rawAuthority != CALLBACK_AUTHORITY) return null

    val purpose = when (uri.rawPath.orEmpty()) {
        EMAIL_CONFIRMATION_PATH -> AuthCallbackPurpose.EmailConfirmation
        PASSWORD_RECOVERY_PATH -> AuthCallbackPurpose.PasswordRecovery
        else -> return null
    }
    return parsePkceCode(purpose, uri.rawQuery, uri.rawFragment)
        ?: parseImplicitSession(purpose, uri.rawQuery, uri.rawFragment)
}

private fun parsePkceCode(
    purpose: AuthCallbackPurpose,
    rawQuery: String?,
    rawFragment: String?,
): AuthCallback? {
    if (rawFragment != null) return null
    val queryParts = rawQuery?.split('&') ?: return null
    if (queryParts.size != 1) return null
    val parameter = queryParts.single()
    if (parameter.substringBefore('=', missingDelimiterValue = "") != AUTH_CODE_PARAMETER) return null
    val code = decodeUrl(parameter.substringAfter('=', missingDelimiterValue = "")) ?: return null
    return code.takeIf {
        it.isNotBlank() && it.length <= MAX_AUTH_CODE_LENGTH && it.none(Char::isISOControl)
    }?.let { AuthCallback.AuthorizationCode(purpose, it) }
}

private fun parseImplicitSession(
    purpose: AuthCallbackPurpose,
    rawQuery: String?,
    rawFragment: String?,
): AuthCallback? {
    if (rawQuery != null || rawFragment.isNullOrBlank()) return null
    val parameters = rawFragment.split('&').map { part ->
        val name = part.substringBefore('=', missingDelimiterValue = "")
        val value = part.substringAfter('=', missingDelimiterValue = "")
        if (name.isBlank() || value.isBlank()) return null
        name to value
    }
    if (parameters.map { it.first }.distinct().size != parameters.size) return null
    val values = parameters.toMap()
    if (!ALLOWED_FRAGMENT_PARAMETERS.containsAll(values.keys)) return null
    if (values[CALLBACK_TYPE_PARAMETER] !in purpose.allowedImplicitTypes) return null
    val accessToken = values[ACCESS_TOKEN_PARAMETER]?.let(::decodeUrl)
    val refreshToken = values[REFRESH_TOKEN_PARAMETER]?.let(::decodeUrl)
    return if (validToken(accessToken) && validToken(refreshToken)) {
        AuthCallback.ImplicitSession(
            purpose = purpose,
            accessToken = accessToken.orEmpty(),
            refreshToken = refreshToken.orEmpty(),
        )
    } else {
        null
    }
}

private val AuthCallbackPurpose.allowedImplicitTypes: Set<String>
    get() = when (this) {
        AuthCallbackPurpose.EmailConfirmation -> setOf(SIGNUP_TYPE_VALUE)
        AuthCallbackPurpose.PasswordRecovery -> setOf(RECOVERY_TYPE_VALUE)
    }

private fun validToken(value: String?): Boolean =
    !value.isNullOrBlank() && value.length <= MAX_AUTH_TOKEN_LENGTH && value.none(Char::isISOControl)

private fun decodeUrl(value: String): String? = runCatching {
    URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}.getOrNull()

private const val CALLBACK_SCHEME = "my.id.rakyzumusic"
private const val CALLBACK_AUTHORITY = "auth"
private const val EMAIL_CONFIRMATION_PATH = ""
private const val PASSWORD_RECOVERY_PATH = "/recovery"
private const val AUTH_CODE_PARAMETER = "code"
private const val CALLBACK_TYPE_PARAMETER = "type"
private const val SIGNUP_TYPE_VALUE = "signup"
private const val RECOVERY_TYPE_VALUE = "recovery"
private const val ACCESS_TOKEN_PARAMETER = "access_token"
private const val REFRESH_TOKEN_PARAMETER = "refresh_token"
private val ALLOWED_FRAGMENT_PARAMETERS = setOf(
    ACCESS_TOKEN_PARAMETER,
    REFRESH_TOKEN_PARAMETER,
    "expires_at",
    "expires_in",
    "token_type",
    CALLBACK_TYPE_PARAMETER,
)
private const val MAX_AUTH_CODE_LENGTH = 4_096
private const val MAX_AUTH_TOKEN_LENGTH = 8_192
private const val MAX_CALLBACK_LENGTH = 20_000
