package my.id.rakyzumusic.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import my.id.rakyzumusic.core.data.media.ArtworkRequest
import my.id.rakyzumusic.core.data.media.ArtworkRequestResult
import my.id.rakyzumusic.core.designsystem.theme.RakyzuAqua
import my.id.rakyzumusic.core.designsystem.theme.RakyzuBlack
import my.id.rakyzumusic.core.designsystem.theme.RakyzuPurple

internal typealias LibraryArtworkRequestProvider = (String) -> ArtworkRequestResult

private enum class LibraryArtworkState {
    Loading,
    Loaded,
    Failed,
}

@Composable
internal fun LibraryAlbumArtwork(
    albumId: String,
    requestProvider: LibraryArtworkRequestProvider,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
) {
    val delivery = remember(albumId, requestProvider) { requestProvider(albumId) }
    val request = (delivery as? ArtworkRequestResult.Ready)?.request
    var state by remember(albumId, delivery) {
        mutableStateOf(if (request == null) LibraryArtworkState.Failed else LibraryArtworkState.Loading)
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.linearGradient(listOf(RakyzuPurple, RakyzuAqua)))
            .testTag("library-album-artwork-${state.name.lowercase()}-$albumId"),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            LibraryArtworkState.Loading -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = RakyzuAqua,
                strokeWidth = 2.dp,
            )
            LibraryArtworkState.Failed -> Icon(
                Icons.Rounded.BrokenImage,
                contentDescription = null,
                tint = RakyzuBlack,
            )
            LibraryArtworkState.Loaded -> Unit
        }
        request?.let {
            val context = LocalContext.current
            val model = remember(context, it) { it.toLibraryImageRequest(context) }
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onLoading = { state = LibraryArtworkState.Loading },
                onSuccess = { state = LibraryArtworkState.Loaded },
                onError = { state = LibraryArtworkState.Failed },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun ArtworkRequest.toLibraryImageRequest(context: android.content.Context): ImageRequest {
    val headers = NetworkHeaders.Builder().apply {
        requestHeaders().forEach { (name, value) -> set(name, value) }
    }.build()
    val cacheKey = "rakyzu-album-artwork:${albumId.lowercase()}"
    return ImageRequest.Builder(context)
        .data(url)
        .httpHeaders(headers)
        .memoryCacheKey(cacheKey)
        .diskCacheKey(cacheKey)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .networkCachePolicy(CachePolicy.ENABLED)
        .build()
}
