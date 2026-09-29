package my.id.rakyzumusic.core.data.download

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import my.id.rakyzumusic.core.model.OfflineMediaAsset

internal data class StoredEncryptedDownload(
    val fileToken: String,
    val plaintextBytes: Long,
)

internal class EncryptedDownloadStore(context: Context) {
    private val directory = File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }

    fun encryptedBytes(fileToken: String?): Long = fileToken
        ?.takeIf(::isValidToken)
        ?.let { File(directory, "$it$FILE_SUFFIX") }
        ?.takeIf(File::isFile)
        ?.length()
        ?: 0L

    fun availableBytes(): Long = directory.usableSpace.coerceAtLeast(0L)

    suspend fun write(
        userId: String,
        trackId: String,
        input: java.io.InputStream,
        maximumBytes: Long,
        onProgress: suspend (Long) -> Unit,
    ): StoredEncryptedDownload {
        val token = tokenFor(userId, trackId)
        val target = File(directory, "$token$FILE_SUFFIX")
        val temporary = File(directory, "$token$PART_SUFFIX")
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyFor(userId))
        val iv = cipher.iv
        var written = 0L
        try {
            DataOutputStream(BufferedOutputStream(FileOutputStream(temporary))).use { output ->
                output.write(MAGIC)
                output.writeByte(FILE_VERSION)
                output.writeByte(iv.size)
                output.write(iv)
                CipherOutputStream(output, cipher).use { encrypted ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        written += count
                        require(written <= maximumBytes) { "download_too_large" }
                        encrypted.write(buffer, 0, count)
                        onProgress(written)
                    }
                }
            }
            require(written > 0L) { "empty_download" }
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
            return StoredEncryptedDownload(token, written)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    fun open(
        userId: String,
        fileToken: String,
        plaintextLength: Long,
        contentType: String,
    ): OfflineMediaAsset? {
        if (!isValidToken(fileToken) || plaintextLength <= 0L) return null
        val file = File(directory, "$fileToken$FILE_SUFFIX").takeIf(File::isFile) ?: return null
        val source = DataInputStream(BufferedInputStream(FileInputStream(file)))
        return runCatching {
            val magic = ByteArray(MAGIC.size).also(source::readFully)
            require(magic.contentEquals(MAGIC))
            require(source.readUnsignedByte() == FILE_VERSION)
            val ivLength = source.readUnsignedByte()
            require(ivLength == GCM_IV_BYTES)
            val iv = ByteArray(ivLength).also(source::readFully)
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keyFor(userId), GCMParameterSpec(GCM_TAG_BITS, iv))
            OfflineMediaAsset(CipherInputStream(source, cipher), plaintextLength, contentType)
        }.onFailure { source.close() }.getOrNull()
    }

    fun delete(fileToken: String?) {
        fileToken?.takeIf(::isValidToken)?.let { File(directory, "$it$FILE_SUFFIX").delete() }
    }

    fun isValid(
        userId: String,
        fileToken: String?,
        expectedPlaintextBytes: Long?,
    ): Boolean {
        val expected = expectedPlaintextBytes?.takeIf { it > 0L } ?: return false
        val asset = open(userId, fileToken ?: return false, expected, "application/octet-stream")
            ?: return false
        return runCatching {
            asset.input.use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                var count = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    count += read
                    if (count > expected) return@runCatching false
                }
                count == expected
            }
        }.getOrDefault(false)
    }

    private fun keyFor(userId: String): SecretKey {
        val alias = "$KEY_ALIAS_PREFIX${sha256(userId).take(KEY_ALIAS_HASH_LENGTH)}"
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private fun tokenFor(userId: String, trackId: String): String = sha256("$userId:$trackId")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun isValidToken(value: String): Boolean = TOKEN_PATTERN.matches(value)

    private companion object {
        const val DIRECTORY_NAME = "offline_media"
        const val FILE_SUFFIX = ".rakyzu"
        const val PART_SUFFIX = ".part"
        const val FILE_VERSION = 1
        const val BUFFER_SIZE = 32 * 1024
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS_PREFIX = "rakyzu_offline_v1_"
        const val KEY_ALIAS_HASH_LENGTH = 24
        val MAGIC = byteArrayOf(0x52, 0x41, 0x4b, 0x59, 0x5a, 0x55, 0x44, 0x4c)
        val TOKEN_PATTERN = Regex("^[0-9a-f]{64}$")
    }
}
