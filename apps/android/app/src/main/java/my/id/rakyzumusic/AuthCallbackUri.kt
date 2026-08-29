package my.id.rakyzumusic

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal fun isPasswordRecoveryCallback(uriValue: String?): Boolean {
    if (uriValue.isNullOrBlank()) return false

    val uri = runCatching { URI(uriValue) }.getOrNull() ?: return false
    if (uri.scheme != CALLBACK_SCHEME || uri.rawAuthority != CALLBACK_AUTHORITY) return false
    if (uri.rawPath != PASSWORD_RECOVERY_PATH || uri.rawFragment != null) return false

    val queryParts = uri.rawQuery?.split('&') ?: return false
    if (queryParts.size != 1) return false

    val parameter = queryParts.single()
    if (parameter.substringBefore('=', missingDelimiterValue = "") != AUTH_CODE_PARAMETER) {
        return false
    }
    val encodedCode = parameter.substringAfter('=', missingDelimiterValue = "")
    val code = runCatching {
        URLDecoder.decode(encodedCode, StandardCharsets.UTF_8.name())
    }.getOrNull() ?: return false

    return code.isNotBlank() &&
        code.length <= MAX_AUTH_CODE_LENGTH &&
        code.none(Char::isISOControl)
}

private const val CALLBACK_SCHEME = "my.id.rakyzumusic"
private const val CALLBACK_AUTHORITY = "auth"
private const val PASSWORD_RECOVERY_PATH = "/recovery"
private const val AUTH_CODE_PARAMETER = "code"
private const val MAX_AUTH_CODE_LENGTH = 4_096
