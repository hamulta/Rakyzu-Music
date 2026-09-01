package my.id.rakyzumusic.core.data.search

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class RecentSearchState(
    val isEnabled: Boolean = false,
    val queries: List<String> = emptyList(),
)

interface RecentSearchRepository {
    fun observe(userId: String): Flow<RecentSearchState>

    suspend fun setEnabled(userId: String, enabled: Boolean)

    suspend fun record(userId: String, query: String)

    suspend fun clear(userId: String)
}

fun createRecentSearchRepository(context: Context): RecentSearchRepository =
    EncryptedRecentSearchRepository(context)

internal fun RecentSearchState.withRecordedQuery(rawQuery: String): RecentSearchState {
    if (!isEnabled) return this
    val query = rawQuery.trim().replace(WHITESPACE, " ").take(MAX_QUERY_LENGTH)
    if (query.length < MIN_QUERY_LENGTH) return this
    val normalized = query.lowercase(Locale.ROOT)
    return copy(
        queries = buildList {
            add(query)
            addAll(this@withRecordedQuery.queries.filterNot {
                it.lowercase(Locale.ROOT) == normalized
            })
        }.take(MAX_RECENT_SEARCHES),
    )
}

private class EncryptedRecentSearchRepository(context: Context) : RecentSearchRepository {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val json = Json { ignoreUnknownKeys = true }
    private val states = mutableMapOf<String, MutableStateFlow<RecentSearchState>>()
    private val lock = Any()

    override fun observe(userId: String): Flow<RecentSearchState> = synchronized(lock) {
        val key = userStorageKey(userId)
        states.getOrPut(key) { MutableStateFlow(readState(key)) }
    }

    override suspend fun setEnabled(userId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        update(userStorageKey(userId)) { current ->
            current.copy(
                isEnabled = enabled,
                queries = if (enabled) current.queries else emptyList(),
            )
        }
    }

    override suspend fun record(userId: String, query: String) = withContext(Dispatchers.IO) {
        update(userStorageKey(userId)) { it.withRecordedQuery(query) }
    }

    override suspend fun clear(userId: String) = withContext(Dispatchers.IO) {
        update(userStorageKey(userId)) { it.copy(queries = emptyList()) }
    }

    private fun update(
        key: String,
        transform: (RecentSearchState) -> RecentSearchState,
    ) = synchronized(lock) {
        val flow = states.getOrPut(key) { MutableStateFlow(readState(key)) }
        val updated = transform(flow.value)
        if (updated != flow.value) {
            writeState(key, updated)
            flow.value = updated
        }
    }

    private fun readState(key: String): RecentSearchState {
        val envelope = preferences.getString(key, null) ?: return RecentSearchState()
        return try {
            val parts = envelope.split(ENVELOPE_SEPARATOR, limit = 3)
            require(parts.size == 3 && parts.first() == ENVELOPE_VERSION)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(
                    GCM_TAG_LENGTH_BITS,
                    Base64.decode(parts[1], Base64.NO_WRAP),
                ),
            )
            val decoded = cipher.doFinal(Base64.decode(parts[2], Base64.NO_WRAP))
                .toString(Charsets.UTF_8)
            json.decodeFromString<PersistedRecentSearchState>(decoded).toDomain()
        } catch (_: GeneralSecurityException) {
            resetCorruptedStore()
            RecentSearchState()
        } catch (_: IllegalArgumentException) {
            resetCorruptedStore()
            RecentSearchState()
        }
    }

    private fun writeState(key: String, state: RecentSearchState) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encoded = json.encodeToString(PersistedRecentSearchState.fromDomain(state))
        val encrypted = cipher.doFinal(encoded.toByteArray(Charsets.UTF_8))
        val envelope = listOf(
            ENVELOPE_VERSION,
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            Base64.encodeToString(encrypted, Base64.NO_WRAP),
        ).joinToString(ENVELOPE_SEPARATOR)
        check(preferences.edit().putString(key, envelope).commit()) {
            "Unable to persist encrypted recent search state"
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private fun resetCorruptedStore() {
        preferences.edit().clear().commit()
        states.values.forEach { it.value = RecentSearchState() }
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
        }
    }

    private fun userStorageKey(userId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(userId.trim().toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        return "listener_$digest"
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "rakyzu_music_recent_search_v1"
        const val PREFERENCES_NAME = "rakyzu_music_secure_recent_search"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val ENVELOPE_VERSION = "v1"
        const val ENVELOPE_SEPARATOR = ":"
    }
}

@Serializable
private data class PersistedRecentSearchState(
    val enabled: Boolean = false,
    val queries: List<String> = emptyList(),
) {
    fun toDomain() = RecentSearchState(
        isEnabled = enabled,
        queries = queries.map { it.trim().replace(WHITESPACE, " ").take(MAX_QUERY_LENGTH) }
            .filter { it.length >= MIN_QUERY_LENGTH }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .take(MAX_RECENT_SEARCHES),
    )

    companion object {
        fun fromDomain(state: RecentSearchState) = PersistedRecentSearchState(
            enabled = state.isEnabled,
            queries = state.queries,
        )
    }
}

private val WHITESPACE = Regex("\\s+")
private const val MIN_QUERY_LENGTH = 2
private const val MAX_QUERY_LENGTH = 100
private const val MAX_RECENT_SEARCHES = 10
