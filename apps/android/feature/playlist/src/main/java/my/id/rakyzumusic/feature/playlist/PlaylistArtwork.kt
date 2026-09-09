package my.id.rakyzumusic.feature.playlist

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.id.rakyzumusic.core.data.playlist.MAX_PLAYLIST_ARTWORK_BYTES

/** Bound input reads and decoded pixels; re-encoding strips EXIF/location metadata. */
internal suspend fun preparePlaylistArtwork(resolver: ContentResolver, uri: Uri): ByteArray? =
    withContext(Dispatchers.IO) {
        try {
            val inputBytes = resolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 10 * MAX_PLAYLIST_ARTWORK_BYTES) return@withContext null
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(inputBytes, 0, inputBytes.size, bounds)
            if (bounds.outWidth !in 1..32768 || bounds.outHeight !in 1..32768) return@withContext null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 512) sample *= 2
            val bitmap = BitmapFactory.decodeByteArray(inputBytes, 0, inputBytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@withContext null
            try {
                val output = ByteArrayOutputStream()
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) return@withContext null
                output.toByteArray().takeIf { it.size <= MAX_PLAYLIST_ARTWORK_BYTES }
            } finally { bitmap.recycle() }
        } catch (_: java.io.IOException) { null }
        catch (_: SecurityException) { null }
    }

internal fun decodePlaylistArtwork(bytes: ByteArray): Bitmap? {
    if (bytes.size > MAX_PLAYLIST_ARTWORK_BYTES) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth !in 1..1024 || bounds.outHeight !in 1..1024) return null
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}
