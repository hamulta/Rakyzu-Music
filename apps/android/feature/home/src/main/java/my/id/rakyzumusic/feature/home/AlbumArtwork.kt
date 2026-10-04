package my.id.rakyzumusic.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.MusicNote
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import my.id.rakyzumusic.core.data.media.ArtworkRequest
import my.id.rakyzumusic.core.data.media.ArtworkRequestFailure
import my.id.rakyzumusic.core.data.media.ArtworkRequestResult
import my.id.rakyzumusic.core.designsystem.theme.RakyzuAqua
import my.id.rakyzumusic.core.designsystem.theme.RakyzuBlack

typealias ArtworkRequestProvider = (String) -> ArtworkRequestResult

internal val unavailableArtworkRequestProvider: ArtworkRequestProvider = {
    ArtworkRequestResult.Failure(ArtworkRequestFailure.InvalidConfiguration)
}

internal enum class ArtworkVisualState(val testTagSegment: String) {
    Placeholder("placeholder"),
    Loading("loading"),
    Loaded("loaded"),
    Failed("failed"),
}

internal fun initialArtworkVisualState(
    albumId: String,
    hasReadyRequest: Boolean,
): ArtworkVisualState = when {
    albumId.isBlank() -> ArtworkVisualState.Placeholder
    hasReadyRequest -> ArtworkVisualState.Loading
    else -> ArtworkVisualState.Failed
}

internal fun artworkCacheKey(albumId: String): String = "rakyzu-album-artwork:${albumId.lowercase()}"

@Composable
internal fun AlbumArtwork(
    albumId: String,
    colors: List<Color>,
    artworkRequestProvider: ArtworkRequestProvider,
    artworkRevision: Long = 0L,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
) {
    val deliveryResult = remember(albumId, artworkRequestProvider, artworkRevision) {
        if (albumId.isBlank()) {
            ArtworkRequestResult.Failure(ArtworkRequestFailure.InvalidAlbumId)
        } else {
            artworkRequestProvider(albumId)
        }
    }
    val artworkRequest = (deliveryResult as? ArtworkRequestResult.Ready)?.request
    var visualState by remember(albumId, deliveryResult, artworkRevision) {
        mutableStateOf(
            initialArtworkVisualState(
                albumId = albumId,
                hasReadyRequest = artworkRequest != null,
            ),
        )
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(colors))
            .testTag("album-artwork-${visualState.testTagSegment}-$albumId"),
        contentAlignment = Alignment.Center,
    ) {
        ArtworkFallback(visualState)
        artworkRequest?.let { request ->
            val context = LocalContext.current
            val imageRequest = remember(context, request, artworkRevision) {
                request.toCoilRequest(context)
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onLoading = { visualState = ArtworkVisualState.Loading },
                onSuccess = { visualState = ArtworkVisualState.Loaded },
                onError = { visualState = ArtworkVisualState.Failed },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ArtworkFallback(state: ArtworkVisualState) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(RakyzuBlack.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            ArtworkVisualState.Loading -> CircularProgressIndicator(
                modifier = Modifier.size(30.dp),
                color = RakyzuAqua,
                strokeWidth = 2.dp,
            )
            ArtworkVisualState.Failed -> Icon(
                imageVector = Icons.Rounded.BrokenImage,
                contentDescription = null,
                tint = Color(0xFFFFC857),
                modifier = Modifier.size(34.dp),
            )
            ArtworkVisualState.Placeholder,
            ArtworkVisualState.Loaded,
            -> Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(34.dp),
            )
        }
    }
}

private fun ArtworkRequest.toCoilRequest(context: android.content.Context): ImageRequest {
    val networkHeaders = NetworkHeaders.Builder().apply {
        requestHeaders().forEach { (name, value) -> set(name, value) }
    }.build()
    val cacheKey = artworkCacheKey(albumId)
    val cachePolicy = if (url.contains("/v1/profiles/") ||
        url.contains("/v1/recommendations/")) CachePolicy.DISABLED else CachePolicy.ENABLED
    return ImageRequest.Builder(context)
        .data(url)
        .httpHeaders(networkHeaders)
        .memoryCacheKey(cacheKey)
        .diskCacheKey(cacheKey)
        .memoryCachePolicy(cachePolicy)
        .diskCachePolicy(cachePolicy)
        .networkCachePolicy(cachePolicy)
        .build()
}
