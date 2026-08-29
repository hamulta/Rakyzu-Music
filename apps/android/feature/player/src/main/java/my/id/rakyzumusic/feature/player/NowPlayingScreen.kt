package my.id.rakyzumusic.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import my.id.rakyzumusic.core.designsystem.theme.RakyzuAqua
import my.id.rakyzumusic.core.designsystem.theme.RakyzuBlack
import my.id.rakyzumusic.core.designsystem.theme.RakyzuMusicTheme
import my.id.rakyzumusic.core.designsystem.theme.RakyzuPurple
import my.id.rakyzumusic.core.designsystem.theme.RakyzuPurpleSoft
import my.id.rakyzumusic.core.designsystem.theme.RakyzuSurfaceRaised
import my.id.rakyzumusic.core.playback.PlaybackQueueItem
import my.id.rakyzumusic.core.playback.PlaybackSnapshot
import my.id.rakyzumusic.core.playback.PlaybackStatus
import my.id.rakyzumusic.core.playback.toPlaybackTimeLabel

@Composable
fun NowPlayingScreen(
    snapshot: PlaybackSnapshot,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onQueueItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF392065), RakyzuBlack, RakyzuBlack),
                ),
            ),
        contentPadding = PaddingValues(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            NowPlayingHeader(onDismiss = onDismiss)
        }
        item {
            NowPlayingArtwork()
        }
        item {
            TrackDetails(snapshot)
        }
        if (snapshot.error != null) {
            item {
                PlaybackErrorCard()
            }
        }
        item {
            PlaybackProgress(
                snapshot = snapshot,
                onSeek = onSeek,
            )
        }
        item {
            TransportControls(
                snapshot = snapshot,
                onPrevious = onPrevious,
                onTogglePlayPause = onTogglePlayPause,
                onNext = onNext,
            )
        }
        item {
            QueueHeader(snapshot)
        }
        itemsIndexed(
            items = snapshot.queue,
            key = { index, item -> "${item.mediaId}-$index" },
        ) { index, item ->
            QueueItem(
                item = item,
                index = index,
                isCurrent = index == snapshot.currentIndex,
                onClick = { onQueueItemClick(index) },
            )
        }
    }
}

@Composable
private fun NowPlayingHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Close Now Playing",
            )
        }
        Text(
            text = "Now Playing",
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.size(48.dp))
    }
}

@Composable
private fun NowPlayingArtwork() {
    Box(
        modifier = Modifier
            .padding(horizontal = 28.dp, vertical = 18.dp)
            .fillMaxWidth()
            .widthIn(max = 420.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(RakyzuPurple, RakyzuPurpleSoft, RakyzuAqua),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(148.dp)
                .clip(CircleShape)
                .background(RakyzuBlack.copy(alpha = 0.76f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun TrackDetails(snapshot: PlaybackSnapshot) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp),
    ) {
        Text(
            text = snapshot.title ?: "Rakyzu Music",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = listOfNotNull(snapshot.artist, snapshot.albumTitle)
                .filter(String::isNotBlank)
                .joinToString(" · ")
                .ifBlank { "Rakyzu Music" },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlaybackErrorCard() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(
            text = "This track is unavailable. Choose another item from the queue.",
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PlaybackProgress(
    snapshot: PlaybackSnapshot,
    onSeek: (Long) -> Unit,
) {
    var draggedPosition by remember(snapshot.mediaId) { mutableStateOf<Float?>(null) }
    val maximum = snapshot.durationMs.coerceAtLeast(1L).toFloat()
    val displayedPosition = draggedPosition ?: snapshot.positionMs
        .coerceIn(0L, maximum.toLong())
        .toFloat()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 18.dp),
    ) {
        Slider(
            value = displayedPosition,
            onValueChange = { draggedPosition = it },
            onValueChangeFinished = {
                draggedPosition?.let { onSeek(it.toLong()) }
                draggedPosition = null
            },
            enabled = snapshot.durationMs > 0L && snapshot.error == null,
            valueRange = 0f..maximum,
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = displayedPosition.toLong().toPlaybackTimeLabel(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = snapshot.durationMs.toPlaybackTimeLabel(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun TransportControls(
    snapshot: PlaybackSnapshot,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val isPlaying = snapshot.status == PlaybackStatus.Playing
    val isBuffering = snapshot.status == PlaybackStatus.Buffering ||
        snapshot.status == PlaybackStatus.Connecting

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onPrevious,
            enabled = snapshot.canSkipPrevious,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Previous track",
                modifier = Modifier.size(36.dp),
            )
        }
        Surface(
            onClick = onTogglePlayPause,
            enabled = snapshot.error == null && !isBuffering,
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            color = RakyzuAqua,
            contentColor = RakyzuBlack,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(30.dp),
                        color = RakyzuBlack,
                        strokeWidth = 3.dp,
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(42.dp),
                    )
                }
            }
        }
        IconButton(
            onClick = onNext,
            enabled = snapshot.canSkipNext,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Next track",
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun QueueHeader(snapshot: PlaybackSnapshot) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
    ) {
        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = null,
                tint = RakyzuAqua,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Queue",
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (snapshot.queue.isNotEmpty()) {
                Text(
                    text = "${snapshot.currentIndex + 1} of ${snapshot.queue.size}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun QueueItem(
    item: PlaybackQueueItem,
    index: Int,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        color = if (isCurrent) RakyzuSurfaceRaised else Color.Transparent,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = (index + 1).toString(),
                modifier = Modifier.width(28.dp),
                color = if (isCurrent) RakyzuAqua else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = if (isCurrent) RakyzuAqua else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.artist,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = item.durationMs.toPlaybackTimeLabel(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun NowPlayingPreview() {
    RakyzuMusicTheme(darkTheme = true) {
        NowPlayingScreen(
            snapshot = previewSnapshot,
            onDismiss = {},
            onTogglePlayPause = {},
            onPrevious = {},
            onNext = {},
            onSeek = {},
            onQueueItemClick = {},
        )
    }
}

private val previewSnapshot = PlaybackSnapshot(
    mediaId = "a3000000-0000-4000-8000-000000000001",
    title = "Midnight Signal",
    artist = "Rakyzu Sessions",
    albumTitle = "Signal Zero",
    status = PlaybackStatus.Playing,
    positionMs = 72_000L,
    bufferedPositionMs = 120_000L,
    durationMs = 185_900L,
    currentIndex = 0,
    queue = listOf(
        PlaybackQueueItem(
            mediaId = "a3000000-0000-4000-8000-000000000001",
            title = "Midnight Signal",
            artist = "Rakyzu Sessions",
            albumTitle = "Signal Zero",
            durationMs = 185_900L,
        ),
        PlaybackQueueItem(
            mediaId = "a3000000-0000-4000-8000-000000000002",
            title = "Neon Current",
            artist = "Rakyzu Sessions",
            albumTitle = "Signal Zero",
            durationMs = 204_000L,
        ),
    ),
    canSkipPrevious = true,
    canSkipNext = true,
)
