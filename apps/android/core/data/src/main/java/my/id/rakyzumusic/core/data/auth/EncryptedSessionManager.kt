package my.id.rakyzumusic.core.data.auth

import io.github.jan.supabase.auth.CodeVerifierCache
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json

internal class EncryptedSessionManager(
    private val store: EncryptedAuthStore,
    private val json: Json,
) : SessionManager {
    override suspend fun saveSession(session: UserSession) {
        store.putString(SESSION_KEY, json.encodeToString(session))
    }

    override suspend fun loadSession(): UserSession {
        val encoded = store.getString(SESSION_KEY) ?: throw NoSessionFoundException()
        return runCatching { json.decodeFromString<UserSession>(encoded) }
            .getOrElse {
                store.remove(SESSION_KEY)
                throw NoSessionFoundException()
            }
    }

    override suspend fun deleteSession() {
        store.remove(SESSION_KEY)
    }

    private companion object {
        const val SESSION_KEY = "supabase_session"
    }
}

internal class EncryptedCodeVerifierCache(
    private val store: EncryptedAuthStore,
) : CodeVerifierCache {
    override suspend fun saveCodeVerifier(codeVerifier: String) {
        store.putString(CODE_VERIFIER_KEY, codeVerifier)
    }

    override suspend fun loadCodeVerifier(): String? = store.getString(CODE_VERIFIER_KEY)

    override suspend fun deleteCodeVerifier() {
        store.remove(CODE_VERIFIER_KEY)
    }

    private companion object {
        const val CODE_VERIFIER_KEY = "pkce_code_verifier"
    }
}
