package my.id.rakyzumusic.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.id.rakyzumusic.core.designsystem.theme.RakyzuAqua
import my.id.rakyzumusic.core.designsystem.theme.RakyzuBlack
import my.id.rakyzumusic.core.designsystem.theme.RakyzuMusicTheme
import my.id.rakyzumusic.core.designsystem.theme.RakyzuPurple
import my.id.rakyzumusic.core.model.Album
import my.id.rakyzumusic.core.model.Artist
import my.id.rakyzumusic.core.model.Track
import my.id.rakyzumusic.core.model.OfflineDownloadItem
import my.id.rakyzumusic.core.model.OfflineDownloadStatus
import my.id.rakyzumusic.core.model.formattedDuration

@Composable
fun AlbumDetailRoute(
    viewModel: AlbumDetailViewModel,
    onBack: () -> Unit,
    onTrackPlay: (List<Track>, Int) -> Unit,
    onTrackPlayNext: ((Track) -> Unit)? = null,
    onTrackAddToQueue: ((Track) -> Unit)? = null,
    onTrackArtistClick: ((Track) -> Unit)? = null,
    onTrackAlbumClick: ((Track) -> Unit)? = null,
    isSaved: Boolean = false,
    isSavePending: Boolean = false,
    onSaveChange: ((Boolean) -> Unit)? = null,
    likedTrackIds: Set<String> = emptySet(),
    pendingTrackIds: Set<String> = emptySet(),
    onTrackLikeChange: ((Track, Boolean) -> Unit)? = null,
    downloadItems: List<OfflineDownloadItem> = emptyList(),
    onDownloadAlbum: ((Album, List<Track>) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AlbumDetailScreen(
        state = state,
        onBack = onBack,
        onTrackPlay = onTrackPlay,
        onTrackPlayNext = onTrackPlayNext,
        onTrackAddToQueue = onTrackAddToQueue,
        onTrackArtistClick = onTrackArtistClick,
        onTrackAlbumClick = onTrackAlbumClick,
        isSaved = isSaved,
        isSavePending = isSavePending,
        onSaveChange = onSaveChange,
        likedTrackIds = likedTrackIds,
        pendingTrackIds = pendingTrackIds,
        onTrackLikeChange = onTrackLikeChange,
        downloadItems = downloadItems,
        onDownloadAlbum = onDownloadAlbum,
        modifier = modifier,
    )
}

@Composable
fun AlbumDetailScreen(
    state: AlbumDetailUiState,
    onBack: () -> Unit,
    onTrackPlay: (List<Track>, Int) -> Unit,
    onTrackPlayNext: ((Track) -> Unit)? = null,
    onTrackAddToQueue: ((Track) -> Unit)? = null,
    onTrackArtistClick: ((Track) -> Unit)? = null,
    onTrackAlbumClick: ((Track) -> Unit)? = null,
    isSaved: Boolean = false,
    isSavePending: Boolean = false,
    onSaveChange: ((Boolean) -> Unit)? = null,
    likedTrackIds: Set<String> = emptySet(),
    pendingTrackIds: Set<String> = emptySet(),
    onTrackLikeChange: ((Track, Boolean) -> Unit)? = null,
    downloadItems: List<OfflineDownloadItem> = emptyList(),
    onDownloadAlbum: ((Album, List<Track>) -> Unit)? = null,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 96.dp),
) {
    var contextualTrack by remember { mutableStateOf<Track?>(null) }

    contextualTrack?.let { track ->
        val queueIndex = state.tracks.indexOfFirst { it.id == track.id }
        TrackContextSheet(
            track = track,
            onDismiss = { contextualTrack = null },
            onPlay = if (queueIndex >= 0) {
                {
                    contextualTrack = null
                    onTrackPlay(state.tracks, queueIndex)
                }
            } else {
                null
            },
            onPlayNext = onTrackPlayNext?.let { callback ->
                { contextualTrack = null; callback(track) }
            },
            onAddToQueue = onTrackAddToQueue?.let { callback ->
                { contextualTrack = null; callback(track) }
            },
            onViewArtist = onTrackArtistClick?.let { callback ->
                {
                    contextualTrack = null
                    callback(track)
                }
            },
            onViewAlbum = onTrackAlbumClick?.let { callback ->
                {
                    contextualTrack = null
                    callback(track)
                }
            },
            isLiked = track.id in likedTrackIds,
            isLikePending = track.id in pendingTrackIds,
            onLikeChange = onTrackLikeChange?.let { callback ->
                { saved -> callback(track, saved) }
            },
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFF19384C),
                        0.34f to RakyzuBlack,
                        1f to RakyzuBlack,
                    ),
                ),
            ),
        contentPadding = contentPadding,
    ) {
        item(key = "album-back") {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .padding(start = 8.dp, top = 12.dp)
                    .size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        when {
            !state.hasObservedCatalog -> item(key = "album-loading") {
                AlbumDetailStatus(
                    title = "Loading album",
                    message = "Reading the latest verified offline catalog.",
                    showProgress = true,
                )
            }
            state.isUnavailable -> item(key = "album-unavailable") {
                AlbumDetailStatus(
                    title = "Album unavailable",
                    message = "This album is no longer available in your verified catalog.",
                )
            }
            else -> {
                val album = requireNotNull(state.album)
                val artistName = state.artist?.name
                    ?: state.tracks.firstOrNull()?.artist
                    ?: "Unknown artist"
                item(key = "album-header") {
                    AlbumHeader(
                        album = album,
                        artistName = artistName,
                        trackCount = state.tracks.size,
                        discCount = state.discCount,
                        onPlayAll = { onTrackPlay(state.tracks, 0) },
                        isSaved = isSaved,
                        isSavePending = isSavePending,
                        onSaveChange = onSaveChange,
                        downloadItems = downloadItems,
                        onDownload = onDownloadAlbum?.let { action ->
                            { action(album, state.tracks) }
                        },
                    )
                }

                if (state.tracks.isEmpty()) {
                    item(key = "album-empty") {
                        AlbumDetailStatus(
                            title = "No tracks yet",
                            message = "This album has no published tracks in your catalog.",
                        )
                    }
                } else {
                    item(key = "album-tracks-heading") {
                        AlbumSectionHeading("Tracks")
                    }
                    itemsIndexed(
                        items = state.tracks,
                        key = { _, track -> "album-track-${track.id}" },
                    ) { index, track ->
                        val discNumber = track.discNumber.coerceAtLeast(1)
                        val previousDisc = state.tracks.getOrNull(index - 1)
                            ?.discNumber
                            ?.coerceAtLeast(1)
                        Column {
                            if (state.discCount > 1 && discNumber != previousDisc) {
                                AlbumDiscHeading(discNumber)
                            }
                            AlbumTrackRow(
                                track = track,
                                onPlay = { onTrackPlay(state.tracks, index) },
                                onMoreClick = { contextualTrack = track },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumHeader(
    album: Album,
    artistName: String,
    trackCount: Int,
    discCount: Int,
    onPlayAll: () -> Unit,
    isSaved: Boolean,
    isSavePending: Boolean,
    onSaveChange: ((Boolean) -> Unit)?,
    downloadItems: List<OfflineDownloadItem>,
    onDownload: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .background(
                    Brush.linearGradient(listOf(RakyzuPurple, RakyzuAqua)),
                    RoundedCornerShape(24.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Album,
                contentDescription = null,
                tint = RakyzuBlack,
                modifier = Modifier.size(88.dp),
            )
        }
        Text(
            text = album.title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = artistName,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = buildList {
                album.releaseDate?.take(4)?.takeIf(String::isNotBlank)?.let(::add)
                add("$trackCount ${trackCount.albumUnit("track")}")
                if (discCount > 1) add("$discCount discs")
            }.joinToString(" · "),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        onSaveChange?.let { action ->
            OutlinedButton(
                onClick = { action(!isSaved) },
                enabled = !isSavePending,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(
                    if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    contentDescription = null,
                )
                Text(
                    if (isSaved) "Saved" else "Save album",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        onDownload?.let { action ->
            val completed = downloadItems.count { it.status == OfflineDownloadStatus.Completed }
            val active = downloadItems.any {
                it.status == OfflineDownloadStatus.Queued ||
                    it.status == OfflineDownloadStatus.Downloading
            }
            val hasFailure = downloadItems.any { it.status == OfflineDownloadStatus.Failed }
            OutlinedButton(
                onClick = action,
                enabled = trackCount > 0 && !active && completed < trackCount,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(
                    if (completed == trackCount && trackCount > 0) {
                        Icons.Rounded.CheckCircle
                    } else {
                        Icons.Rounded.Download
                    },
                    contentDescription = null,
                )
                Text(
                    when {
                        completed == trackCount && trackCount > 0 -> "Downloaded"
                        active -> "Downloading $completed of $trackCount"
                        hasFailure -> "Retry album download"
                        else -> "Download album"
                    },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        if (trackCount > 0) {
            Button(
                onClick = onPlayAll,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RakyzuAqua,
                    contentColor = RakyzuBlack,
                ),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Text("Play album", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun AlbumSectionHeading(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun AlbumDiscHeading(discNumber: Int) {
    Text(
        text = "Disc $discNumber",
        color = RakyzuAqua,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun AlbumTrackRow(
    track: Track,
    onPlay: () -> Unit,
    onMoreClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(
                onClickLabel = "Play ${track.title.trim()}",
                role = Role.Button,
                onClick = onPlay,
            )
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = track.trackNumber.coerceAtLeast(1).toString(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = 16.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    "Explicit".takeIf { track.isExplicit },
                    track.formattedDuration(),
                ).joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = RakyzuAqua)
        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "More options for ${track.title.trim()}",
            )
        }
    }
}

@Composable
private fun AlbumDetailStatus(
    title: String,
    message: String,
    showProgress: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 48.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showProgress) CircularProgressIndicator(color = RakyzuAqua)
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun Int.albumUnit(singular: String): String = if (this == 1) singular else "${singular}s"

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun AlbumDetailScreenPreview() {
    val artist = Artist("artist-1", "Rakyzu Sessions")
    val album = Album("album-1", artist.id, "Signal Zero", "2026-08-31")
    val tracks = listOf(
        Track(
            id = "track-1",
            title = "Midnight Signal",
            artist = artist.name,
            durationMs = 180_000L,
            artistId = artist.id,
            albumId = album.id,
            albumTitle = album.title,
        ),
    )
    RakyzuMusicTheme(darkTheme = true) {
        AlbumDetailScreen(
            state = AlbumDetailUiState(
                albumId = album.id,
                hasObservedCatalog = true,
                album = album,
                artist = artist,
                tracks = tracks,
            ),
            onBack = {},
            onTrackPlay = { _, _ -> },
        )
    }
}
