package my.id.rakyzumusic.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
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
import my.id.rakyzumusic.core.model.PlaybackQueueItem
import my.id.rakyzumusic.core.model.LyricsKind
import my.id.rakyzumusic.core.model.TrackContext
import my.id.rakyzumusic.core.model.TrackCreditRole
import my.id.rakyzumusic.core.playback.PlaybackSnapshot
import my.id.rakyzumusic.core.playback.PlaybackStatus
import my.id.rakyzumusic.core.playback.toPlaybackTimeLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    snapshot: PlaybackSnapshot,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onQueueItemClick: (Int) -> Unit,
    onQueueItemMove: (Int, Int) -> Unit = { _, _ -> },
    onQueueItemRemove: (Int) -> Unit = {},
    onClearQueue: () -> Unit = {},
    onRetryPlayback: () -> Unit = {},
    modifier: Modifier = Modifier,
    isLiked: Boolean = false,
    isAlbumSaved: Boolean = false,
    isArtistFollowed: Boolean = false,
    isLikePending: Boolean = false,
    isAlbumPending: Boolean = false,
    isArtistPending: Boolean = false,
    onLikeChange: (Boolean) -> Unit = {},
    onAlbumSaveChange: (Boolean) -> Unit = {},
    onArtistFollowChange: (Boolean) -> Unit = {},
    onShareTrack: (PlaybackQueueItem) -> Unit = {},
    trackContext: TrackContext? = null,
    isTrackContextRefreshing: Boolean = false,
    trackContextMessage: String? = null,
    onRetryTrackContext: () -> Unit = {},
) {
    val currentItem = snapshot.queue.getOrNull(snapshot.currentIndex)
    var contextSheet by remember(currentItem?.mediaId) {
        mutableStateOf<TrackContextSheet?>(null)
    }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("now-playing")
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
        currentItem?.let { queueItem ->
            item {
                NowPlayingLibraryActions(
                    item = queueItem,
                    isLiked = isLiked,
                    isAlbumSaved = isAlbumSaved,
                    isArtistFollowed = isArtistFollowed,
                    isLikePending = isLikePending,
                    isAlbumPending = isAlbumPending,
                    isArtistPending = isArtistPending,
                    onLikeChange = onLikeChange,
                    onAlbumSaveChange = onAlbumSaveChange,
                    onArtistFollowChange = onArtistFollowChange,
                    onShareTrack = onShareTrack,
                )
            }
            item {
                TrackContextActions(
                    item = queueItem,
                    context = trackContext,
                    isRefreshing = isTrackContextRefreshing,
                    message = trackContextMessage,
                    onLyrics = { contextSheet = TrackContextSheet.Lyrics },
                    onCredits = { contextSheet = TrackContextSheet.Credits },
                    onRetry = onRetryTrackContext,
                )
            }
        }
        if (snapshot.error != null) {
            item {
                PlaybackErrorCard(snapshot, onRetryPlayback)
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
            QueueHeader(snapshot, onClearQueue)
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
                onMoveUp = if (index > 0) {
                    { onQueueItemMove(index, index - 1) }
                } else {
                    null
                },
                onMoveDown = if (index < snapshot.queue.lastIndex) {
                    { onQueueItemMove(index, index + 1) }
                } else {
                    null
                },
                onRemove = { onQueueItemRemove(index) },
            )
        }
    }
    when (contextSheet) {
        TrackContextSheet.Lyrics -> LyricsSheet(
            context = trackContext,
            playbackPositionMs = snapshot.positionMs,
            onDismiss = { contextSheet = null },
        )
        TrackContextSheet.Credits -> CreditsSheet(
            context = trackContext,
            onDismiss = { contextSheet = null },
        )
        null -> Unit
    }
}

private enum class TrackContextSheet { Lyrics, Credits }

@Composable
private fun TrackContextActions(
    item: PlaybackQueueItem,
    context: TrackContext?,
    isRefreshing: Boolean,
    message: String?,
    onLyrics: () -> Unit,
    onCredits: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onLyrics,
                enabled = context?.lyrics?.isDisplayable == true && !isRefreshing,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Article, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Lyrics")
            }
            Button(
                onClick = onCredits,
                enabled = context?.hasCredits == true && !isRefreshing,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(Icons.Rounded.Badge, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Credits")
            }
        }
        when {
            isRefreshing -> Text(
                "Loading licensed lyrics and credits…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            message != null -> Button(onClick = onRetry) { Text("Retry track details") }
            context != null && !context.lyrics.isDisplayable && !context.hasCredits -> Text(
                "Lyrics and detailed credits are not available for ${item.title}.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricsSheet(
    context: TrackContext?,
    playbackPositionMs: Long,
    onDismiss: () -> Unit,
) {
    val lyrics = context?.lyrics
    val activeIndex = lyrics?.activeLineIndex(playbackPositionMs)
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var followPlayback by remember(context?.trackId) { mutableStateOf(true) }
    LaunchedEffect(activeIndex, followPlayback) {
        if (followPlayback && activeIndex != null) listState.animateScrollToItem(activeIndex)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Lyrics", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                if (lyrics?.kind == LyricsKind.TimeSynced) {
                    Button(onClick = { followPlayback = !followPlayback }) {
                        Text(if (followPlayback) "Following" else "Follow lyrics")
                    }
                }
            }
            if (lyrics?.isDisplayable == true) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(lyrics.lines) { index, line ->
                        Text(
                            text = line.text,
                            color = if (index == activeIndex) RakyzuAqua else
                                MaterialTheme.colorScheme.onSurface,
                            style = if (index == activeIndex) MaterialTheme.typography.titleLarge else
                                MaterialTheme.typography.bodyLarge,
                            fontWeight = if (index == activeIndex) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.semantics {
                                if (index == activeIndex) stateDescription = "Current lyric line"
                            },
                        )
                    }
                }
                lyrics.providerName?.let { Text("Lyrics provided by $it") }
                lyrics.providerNotice?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text("Lyrics are unavailable for this track or your current region.")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreditsSheet(context: TrackContext?, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("Song credits", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            }
            val grouped = context?.credits.orEmpty().groupBy { it.role }
            TrackCreditRole.entries.forEach { role ->
                val credits = grouped[role].orEmpty()
                if (credits.isNotEmpty()) {
                    item(key = role.name) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(role.displayLabel(), color = RakyzuAqua, fontWeight = FontWeight.Bold)
                            credits.forEach { credit ->
                                Text(credit.displayName, style = MaterialTheme.typography.bodyLarge)
                                credit.sourceName?.let {
                                    Text("Source: $it", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            if (context?.hasCredits != true) item { Text("Detailed credits are unavailable.") }
        }
    }
}

private fun TrackCreditRole.displayLabel(): String = when (this) {
    TrackCreditRole.PrimaryArtist -> "Primary Artist"
    TrackCreditRole.FeaturedArtist -> "Featured Artist"
    TrackCreditRole.Songwriter -> "Songwriter"
    TrackCreditRole.Producer -> "Producer"
    TrackCreditRole.Performer -> "Performer"
}

@Composable
private fun NowPlayingLibraryActions(
    item: PlaybackQueueItem,
    isLiked: Boolean,
    isAlbumSaved: Boolean,
    isArtistFollowed: Boolean,
    isLikePending: Boolean,
    isAlbumPending: Boolean,
    isArtistPending: Boolean,
    onLikeChange: (Boolean) -> Unit,
    onAlbumSaveChange: (Boolean) -> Unit,
    onArtistFollowChange: (Boolean) -> Unit,
    onShareTrack: (PlaybackQueueItem) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        maxItemsInEachRow = if (maxWidth < 380.dp) 2 else 4,
        horizontalArrangement = Arrangement.SpaceEvenly,
      ) {
        LibraryActionButton(
            icon = Icons.Rounded.Favorite,
            label = if (isLiked) "Liked" else "Like",
            actionDescription = if (isLiked) {
                "Remove ${item.title} from Liked Songs"
            } else {
                "Add ${item.title} to Liked Songs"
            },
            selected = isLiked,
            pending = isLikePending,
            enabled = item.mediaId.isNotBlank(),
            onClick = { onLikeChange(!isLiked) },
        )
        LibraryActionButton(
            icon = Icons.Rounded.Bookmark,
            label = if (isAlbumSaved) "Saved" else "Save album",
            actionDescription = if (isAlbumSaved) {
                "Remove ${item.albumTitle} from Library"
            } else {
                "Save ${item.albumTitle} to Library"
            },
            selected = isAlbumSaved,
            pending = isAlbumPending,
            enabled = item.albumId.isNotBlank(),
            onClick = { onAlbumSaveChange(!isAlbumSaved) },
        )
        LibraryActionButton(
            icon = Icons.Rounded.Person,
            label = if (isArtistFollowed) "Following" else "Follow",
            actionDescription = if (isArtistFollowed) {
                "Unfollow ${item.artist}"
            } else {
                "Follow ${item.artist}"
            },
            selected = isArtistFollowed,
            pending = isArtistPending,
            enabled = item.artistId.isNotBlank(),
            onClick = { onArtistFollowChange(!isArtistFollowed) },
        )
        LibraryActionButton(
            icon = Icons.Rounded.Share,
            label = "Share",
            actionDescription = "Share ${item.title} by ${item.artist}",
            selected = false,
            pending = false,
            enabled = item.mediaId.isNotBlank(),
            onClick = { onShareTrack(item) },
        )
      }
    }
}

@Composable
private fun LibraryActionButton(
    icon: ImageVector,
    label: String,
    actionDescription: String,
    selected: Boolean,
    pending: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.widthIn(min = 88.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled && !pending,
            modifier = Modifier.size(48.dp),
        ) {
            if (pending) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = actionDescription,
                    tint = if (selected) RakyzuAqua else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = label,
            modifier = Modifier.heightIn(min = 20.dp),
            color = if (selected) RakyzuAqua else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
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
        Text(
            text = "Playing on ${snapshot.device.name}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun PlaybackErrorCard(snapshot: PlaybackSnapshot, onRetryPlayback: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (snapshot.recovery.canRetry) {
                    "Playback was interrupted. Your ${snapshot.recovery.retainedQueueSize}-track " +
                        "queue is kept on this device."
                } else {
                    "This track is unavailable. Choose another item from the queue."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (snapshot.recovery.canRetry) {
                Button(
                    onClick = onRetryPlayback,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Retry playback")
                }
            }
        }
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
private fun QueueHeader(snapshot: PlaybackSnapshot, onClearQueue: () -> Unit) {
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
                IconButton(onClick = onClearQueue) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Clear queue",
                    )
                }
            }
        }
        Text(
            text = "Autoplay recommendations are off. Playback ends after this queue.",
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun QueueItem(
    item: PlaybackQueueItem,
    index: Int,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .semantics {
                stateDescription = if (isCurrent) "Current track" else "Queued track"
            },
        color = if (isCurrent) RakyzuSurfaceRaised else Color.Transparent,
        shape = RoundedCornerShape(12.dp),
    ) {
        BoxWithConstraints {
            val compact = shouldUseCompactQueueLayout(
                availableWidthDp = maxWidth.value,
                fontScale = LocalDensity.current.fontScale,
            )
            if (compact) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QueueMetadata(item, index, isCurrent, titleMaxLines = 2)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        QueueActions(item, onMoveUp, onMoveDown, onRemove)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QueueMetadata(item, index, isCurrent, titleMaxLines = 1)
                    QueueActions(item, onMoveUp, onMoveDown, onRemove)
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.QueueMetadata(
    item: PlaybackQueueItem,
    index: Int,
    isCurrent: Boolean,
    titleMaxLines: Int,
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
            maxLines = titleMaxLines,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.artist,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = titleMaxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Text(
        text = item.durationMs.toPlaybackTimeLabel(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
private fun QueueActions(
    item: PlaybackQueueItem,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    IconButton(
        onClick = { onMoveUp?.invoke() },
        enabled = onMoveUp != null,
        modifier = Modifier.size(48.dp),
    ) {
        Icon(Icons.Rounded.ArrowUpward, "Move ${item.title} earlier in queue")
    }
    IconButton(
        onClick = { onMoveDown?.invoke() },
        enabled = onMoveDown != null,
        modifier = Modifier.size(48.dp),
    ) {
        Icon(Icons.Rounded.ArrowDownward, "Move ${item.title} later in queue")
    }
    IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
        Icon(Icons.Rounded.Delete, "Remove ${item.title} from queue")
    }
}

internal fun shouldUseCompactQueueLayout(availableWidthDp: Float, fontScale: Float): Boolean =
    availableWidthDp < 380f || fontScale >= 1.3f

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
