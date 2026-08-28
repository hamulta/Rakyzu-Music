package my.id.rakyzumusic.core.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class EncryptedAuthStore(context: Context) {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val lock = Any()

    fun putString(key: String, value: String) = synchronized(lock) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val envelope = listOf(
            ENVELOPE_VERSION,
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            Base64.encodeToString(encrypted, Base64.NO_WRAP),
        ).joinToString(ENVELOPE_SEPARATOR)

        check(preferences.edit().putString(key, envelope).commit()) {
            "Unable to persist encrypted authentication state"
        }
    }

    fun getString(key: String): String? = synchronized(lock) {
        val envelope = preferences.getString(key, null) ?: return@synchronized null
        try {
            val parts = envelope.split(ENVELOPE_SEPARATOR, limit = 3)
            require(parts.size == 3 && parts.first() == ENVELOPE_VERSION)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val initializationVector = Base64.decode(parts[1], Base64.NO_WRAP)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, initializationVector),
            )
            cipher.doFinal(Base64.decode(parts[2], Base64.NO_WRAP)).toString(Charsets.UTF_8)
        } catch (_: GeneralSecurityException) {
            resetCorruptedStore()
            null
        } catch (_: IllegalArgumentException) {
            resetCorruptedStore()
            null
        }
    }

    fun remove(key: String) = synchronized(lock) {
        check(preferences.edit().remove(key).commit()) {
            "Unable to clear encrypted authentication state"
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
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "rakyzu_music_auth_v1"
        const val PREFERENCES_NAME = "rakyzu_music_secure_auth"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val ENVELOPE_VERSION = "v1"
        const val ENVELOPE_SEPARATOR = ":"
    }
}
