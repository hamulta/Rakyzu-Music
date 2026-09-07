package my.id.rakyzumusic.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.id.rakyzumusic.core.designsystem.theme.RakyzuAqua
import my.id.rakyzumusic.core.designsystem.theme.RakyzuBlack
import my.id.rakyzumusic.core.designsystem.theme.RakyzuPurple
import my.id.rakyzumusic.core.designsystem.theme.RakyzuSurface
import my.id.rakyzumusic.core.model.Album
import my.id.rakyzumusic.core.model.Artist
import my.id.rakyzumusic.core.model.LibraryItemKind
import my.id.rakyzumusic.core.model.Track
import my.id.rakyzumusic.core.model.formattedDuration

private enum class LibraryFilter(val label: String) {
    All("All"),
    Songs("Liked Songs"),
    Albums("Albums"),
    Artists("Artists"),
}

@Composable
fun LibraryRoute(
    viewModel: LibraryViewModel,
    onTrackPlay: (List<Track>, Int) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryScreen(
        state = state,
        onRefresh = viewModel::refresh,
        onTrackPlay = onTrackPlay,
        onAlbumClick = onAlbumClick,
        onArtistClick = onArtistClick,
        onRemoveTrack = { viewModel.setSaved(LibraryItemKind.Track, it.id, false) },
        onRemoveAlbum = { viewModel.setSaved(LibraryItemKind.Album, it.id, false) },
        onUnfollowArtist = { viewModel.setSaved(LibraryItemKind.Artist, it.id, false) },
        modifier = modifier,
    )
}

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onRefresh: () -> Unit,
    onTrackPlay: (List<Track>, Int) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onRemoveTrack: (Track) -> Unit,
    onRemoveAlbum: (Album) -> Unit,
    onUnfollowArtist: (Artist) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 96.dp),
) {
    var filterName by rememberSaveable { mutableStateOf(LibraryFilter.All.name) }
    val filter = LibraryFilter.entries.firstOrNull { it.name == filterName } ?: LibraryFilter.All

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFF24163E),
                        0.3f to RakyzuBlack,
                        1f to RakyzuBlack,
                    ),
                ),
            ),
        contentPadding = contentPadding,
    ) {
        item(key = "library-header") {
            LibraryHeader(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
            )
        }

        state.message?.let { message ->
            item(key = "library-message") {
                Surface(
                    color = if (state.messageIsError) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        RakyzuSurface
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Text(
                        text = message,
                        color = if (state.messageIsError) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        when {
            !state.hasObservedLibrary -> item(key = "library-loading") {
                LibraryStatus(
                    icon = Icons.Rounded.LibraryMusic,
                    title = "Loading your Library",
                    message = "Reading your saved music from this device.",
                    showProgress = true,
                )
            }
            state.library.isEmpty -> item(key = "library-empty") {
                LibraryStatus(
                    icon = Icons.Rounded.LibraryMusic,
                    title = "Your Library is ready",
                    message = "Like tracks, save albums, or follow artists from Search to keep them here.",
                )
            }
            else -> {
                item(key = "library-filters") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LibraryFilter.entries.forEach { option ->
                            AssistChip(
                                onClick = { filterName = option.name },
                                label = { Text(option.label) },
                                leadingIcon = if (option == filter) {
                                    { Icon(Icons.Rounded.Favorite, contentDescription = null) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }

                if (filter == LibraryFilter.All || filter == LibraryFilter.Songs) {
                    if (state.library.likedTracks.isNotEmpty()) {
                        item(key = "liked-heading") {
                            LibrarySectionHeading(
                                title = "Liked Songs",
                                count = state.library.likedTracks.size,
                                onPlay = { onTrackPlay(state.library.likedTracks, 0) },
                            )
                        }
                        itemsIndexed(
                            items = state.library.likedTracks,
                            key = { _, track -> "liked-${track.id}" },
                        ) { index, track ->
                            LibraryTrackRow(
                                track = track,
                                isPending = state.isPending(LibraryItemKind.Track, track.id),
                                onPlay = { onTrackPlay(state.library.likedTracks, index) },
                                onRemove = { onRemoveTrack(track) },
                            )
                        }
                    }
                }

                if (filter == LibraryFilter.All || filter == LibraryFilter.Albums) {
                    if (state.library.savedAlbums.isNotEmpty()) {
                        item(key = "albums-heading") {
                            LibrarySectionHeading("Saved Albums", state.library.savedAlbums.size)
                        }
                        items(
                            items = state.library.savedAlbums,
                            key = { "saved-${it.album.id}" },
                        ) { saved ->
                            LibraryCollectionRow(
                                icon = Icons.Rounded.Album,
                                title = saved.album.title,
                                subtitle = saved.artistName.ifBlank { "Unknown artist" },
                                actionDescription = "Remove ${saved.album.title} from Library",
                                isPending = state.isPending(LibraryItemKind.Album, saved.album.id),
                                onClick = { onAlbumClick(saved.album) },
                                onRemove = { onRemoveAlbum(saved.album) },
                            )
                        }
                    }
                }

                if (filter == LibraryFilter.All || filter == LibraryFilter.Artists) {
                    if (state.library.followedArtists.isNotEmpty()) {
                        item(key = "artists-heading") {
                            LibrarySectionHeading(
                                "Followed Artists",
                                state.library.followedArtists.size,
                            )
                        }
                        items(
                            items = state.library.followedArtists,
                            key = { "followed-${it.id}" },
                        ) { artist ->
                            LibraryCollectionRow(
                                icon = Icons.Rounded.Person,
                                title = artist.name,
                                subtitle = "Artist",
                                actionDescription = "Unfollow ${artist.name}",
                                isPending = state.isPending(LibraryItemKind.Artist, artist.id),
                                onClick = { onArtistClick(artist) },
                                onRemove = { onUnfollowArtist(artist) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(isRefreshing: Boolean, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Your Library",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "Saved for your Rakyzu Music account",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        IconButton(
            onClick = onRefresh,
            enabled = !isRefreshing,
            modifier = Modifier.size(48.dp),
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Library")
            }
        }
    }
}

@Composable
private fun LibrarySectionHeading(
    title: String,
    count: Int,
    onPlay: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$title · $count",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        onPlay?.let {
            IconButton(onClick = it, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play all liked songs")
            }
        }
    }
}

@Composable
private fun LibraryTrackRow(
    track: Track,
    isPending: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClickLabel = "Play ${track.title}", onClick = onPlay)
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibraryIcon(Icons.Rounded.Favorite)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = track.title,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${track.artist} · ${track.formattedDuration()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onRemove,
            enabled = !isPending,
            modifier = Modifier.size(48.dp),
        ) {
            if (isPending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Rounded.Favorite, contentDescription = "Remove ${track.title} from Liked Songs")
        }
    }
}

@Composable
private fun LibraryCollectionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionDescription: String,
    isPending: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibraryIcon(icon)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRemove, enabled = !isPending, modifier = Modifier.size(48.dp)) {
            if (isPending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Rounded.Bookmark, contentDescription = actionDescription)
        }
    }
}

@Composable
private fun LibraryIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(
                Brush.linearGradient(listOf(RakyzuPurple, RakyzuAqua)),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = RakyzuBlack)
    }
}

@Composable
private fun LibraryStatus(
    icon: ImageVector,
    title: String,
    message: String,
    showProgress: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 72.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = RakyzuAqua, modifier = Modifier.size(56.dp))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (showProgress) CircularProgressIndicator(modifier = Modifier.size(28.dp))
    }
}
