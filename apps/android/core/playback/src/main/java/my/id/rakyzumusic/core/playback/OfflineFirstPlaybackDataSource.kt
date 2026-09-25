package my.id.rakyzumusic.core.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.EOFException
import my.id.rakyzumusic.core.model.OfflineMediaAsset

fun interface OfflinePlaybackAssetProvider {
    fun open(trackId: String): OfflineMediaAsset?
}

@UnstableApi
internal class OfflineFirstPlaybackDataSource private constructor(
    private val networkFactory: DataSource.Factory,
    private val offlineProvider: OfflinePlaybackAssetProvider,
) : DataSource {
    private var delegate: DataSource? = null
    private val transferListeners = mutableListOf<TransferListener>()

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners += transferListener
        delegate?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        check(delegate == null)
        val trackId = dataSpec.uri.toString().trackIdOrNull()
        val asset = trackId?.let(offlineProvider::open)
        val source = if (asset != null) {
            OfflineAssetDataSource(asset)
        } else {
            networkFactory.createDataSource()
        }
        transferListeners.forEach(source::addTransferListener)
        delegate = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        checkNotNull(delegate).read(buffer, offset, length)

    override fun getUri(): Uri? = delegate?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        delegate?.responseHeaders.orEmpty()

    override fun close() {
        delegate?.close()
        delegate = null
    }

    class Factory(
        private val networkFactory: DataSource.Factory,
        private val offlineProvider: OfflinePlaybackAssetProvider,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            OfflineFirstPlaybackDataSource(networkFactory, offlineProvider)
    }
}

@UnstableApi
private class OfflineAssetDataSource(
    private val asset: OfflineMediaAsset,
) : BaseDataSource(false) {
    private var openedUri: Uri? = null
    private var bytesRemaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        if (dataSpec.position > asset.plaintextLength) throw EOFException()
        var skipped = 0L
        while (skipped < dataSpec.position) {
            val count = asset.input.skip(dataSpec.position - skipped)
            if (count <= 0L) {
                if (asset.input.read() < 0) throw EOFException()
                skipped += 1L
            } else skipped += count
        }
        val available = asset.plaintextLength - dataSpec.position
        bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            available
        } else {
            dataSpec.length.coerceAtMost(available)
        }
        openedUri = dataSpec.uri
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val read = asset.input.read(buffer, offset, length.coerceAtMost(bytesRemaining.toInt()))
        if (read < 0) return C.RESULT_END_OF_INPUT
        bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = openedUri

    override fun close() {
        openedUri = null
        runCatching(asset.input::close)
        if (opened) {
            opened = false
            transferEnded()
        }
    }
}
