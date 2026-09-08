package my.id.rakyzumusic.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.id.rakyzumusic.core.data.media.ArtworkRequestFailure
import my.id.rakyzumusic.core.data.media.ArtworkRequestResult
import my.id.rakyzumusic.core.data.media.MediaDeliveryRepository
import my.id.rakyzumusic.core.designsystem.theme.RakyzuAqua
import my.id.rakyzumusic.core.designsystem.theme.RakyzuBlack
import my.id.rakyzumusic.core.designsystem.theme.RakyzuPurple
import my.id.rakyzumusic.core.designsystem.theme.RakyzuSurface
import my.id.rakyzumusic.core.model.Album
import my.id.rakyzumusic.core.model.Artist
import my.id.rakyzumusic.core.model.LibraryItemKind
import my.id.rakyzumusic.core.model.Track
import my.id.rakyzumusic.core.model.formattedDuration

@Composable
fun LibraryRoute(
    viewModel: LibraryViewModel,
    mediaDeliveryRepository: MediaDeliveryRepository,
    onTrackPlay: (List<Track>, Int) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onBrowseMusic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val artworkRequestProvider: LibraryArtworkRequestProvider =
        remember(mediaDeliveryRepository) { mediaDeliveryRepository::artworkRequest }
    LibraryScreen(
        state = state,
        onRefresh = viewModel::refresh,
        onQueryChange = viewModel::updateQuery,
        onClearQuery = viewModel::clearQuery,
        onFilterSelected = viewModel::selectFilter,
        onSortSelected = viewModel::selectSort,
        onTrackPlay = onTrackPlay,
        onAlbumClick = onAlbumClick,
        onArtistClick = onArtistClick,
        onBrowseMusic = onBrowseMusic,
        artworkRequestProvider = artworkRequestProvider,
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
    onQueryChange: (String) -> Unit = {},
    onClearQuery: () -> Unit = {},
    onFilterSelected: (LibraryFilter) -> Unit = {},
    onSortSelected: (LibrarySort) -> Unit = {},
    onTrackPlay: (List<Track>, Int) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onRemoveTrack: (Track) -> Unit,
    onRemoveAlbum: (Album) -> Unit,
    onUnfollowArtist: (Artist) -> Unit,
    onBrowseMusic: () -> Unit = {},
    artworkRequestProvider: LibraryArtworkRequestProvider = {
        ArtworkRequestResult.Failure(ArtworkRequestFailure.InvalidConfiguration)
    },
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 96.dp),
) {
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
                pendingMutationCount = state.library.pendingMutationCount,
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
                    actionLabel = "Browse music",
                    onAction = onBrowseMusic,
                )
            }
            else -> {
                item(key = "library-controls") {
                    LibraryControls(
                        query = state.query,
                        filter = state.filter,
                        sort = state.sort,
                        onQueryChange = onQueryChange,
                        onClearQuery = onClearQuery,
                        onFilterSelected = onFilterSelected,
                        onSortSelected = onSortSelected,
                    )
                }

                if (state.hasNoMatchingItems) {
                    item(key = "library-no-results") {
                        val emptyCopy = state.emptyStateCopy()
                        LibraryStatus(
                            icon = emptyCopy.icon,
                            title = emptyCopy.title,
                            message = emptyCopy.message,
                            actionLabel = if (state.query.isNotBlank()) {
                                "Clear Library search"
                            } else {
                                "Browse music"
                            },
                            onAction = if (state.query.isNotBlank()) onClearQuery else onBrowseMusic,
                        )
                    }
                } else {
                    if (state.visibleLikedTracks.isNotEmpty()) {
                        item(key = "liked-heading") {
                            LibrarySectionHeading(
                                title = "Liked Songs",
                                count = state.visibleLikedTracks.size,
                                playActionDescription =
                                    "Play ${state.visibleLikedTracks.size} liked songs " +
                                    "in ${state.sort.label} order",
                                onPlay = { onTrackPlay(state.visibleLikedTracks, 0) },
                            )
                        }
                        itemsIndexed(
                            items = state.visibleLikedTracks,
                            key = { _, track -> "liked-${track.id}" },
                        ) { index, track ->
                            LibraryTrackRow(
                                track = track,
                                isPending = state.isPending(LibraryItemKind.Track, track.id),
                                onPlay = { onTrackPlay(state.visibleLikedTracks, index) },
                                onRemove = { onRemoveTrack(track) },
                            )
                        }
                    }

                    if (state.visibleSavedAlbums.isNotEmpty()) {
                        item(key = "albums-heading") {
                            LibrarySectionHeading("Saved Albums", state.visibleSavedAlbums.size)
                        }
                        items(
                            items = state.visibleSavedAlbums,
                            key = { "saved-${it.album.id}" },
                        ) { saved ->
                            LibraryCollectionRow(
                                title = saved.album.title,
                                subtitle = saved.artistName.ifBlank { "Unknown artist" },
                                actionDescription = "Remove ${saved.album.title} from Library",
                                isPending = state.isPending(LibraryItemKind.Album, saved.album.id),
                                onClick = { onAlbumClick(saved.album) },
                                onRemove = { onRemoveAlbum(saved.album) },
                                leadingContent = {
                                    LibraryAlbumArtwork(
                                        albumId = saved.album.id,
                                        requestProvider = artworkRequestProvider,
                                    )
                                },
                            )
                        }
                    }

                    if (state.visibleFollowedArtists.isNotEmpty()) {
                        item(key = "artists-heading") {
                            LibrarySectionHeading(
                                "Followed Artists",
                                state.visibleFollowedArtists.size,
                            )
                        }
                        items(
                            items = state.visibleFollowedArtists,
                            key = { "followed-${it.id}" },
                        ) { artist ->
                            LibraryCollectionRow(
                                title = artist.name,
                                subtitle = "Artist",
                                actionDescription = "Unfollow ${artist.name}",
                                isPending = state.isPending(LibraryItemKind.Artist, artist.id),
                                onClick = { onArtistClick(artist) },
                                onRemove = { onUnfollowArtist(artist) },
                                leadingContent = { LibraryArtistAvatar(artist.name) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(
    isRefreshing: Boolean,
    pendingMutationCount: Int,
    onRefresh: () -> Unit,
) {
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
                text = if (pendingMutationCount > 0) {
                    "$pendingMutationCount offline change${if (pendingMutationCount == 1) "" else "s"} waiting to sync"
                } else {
                    "Saved for your Rakyzu Music account"
                },
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
private fun LibraryControls(
    query: String,
    filter: LibraryFilter,
    sort: LibrarySort,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onFilterSelected: (LibraryFilter) -> Unit,
    onSortSelected: (LibrarySort) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            label = { Text("Search your Library") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = if (query.isNotBlank()) {
                {
                    IconButton(onClick = onClearQuery) {
                        Icon(Icons.Rounded.Clear, contentDescription = "Clear Library search")
                    }
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibraryFilter.entries.forEach { option ->
                FilterChip(
                    selected = option == filter,
                    onClick = { onFilterSelected(option) },
                    label = { Text(option.label) },
                )
            }
        }
        Text(
            text = "Sort Library",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibrarySort.entries.forEach { option ->
                FilterChip(
                    selected = option == sort,
                    onClick = { onSortSelected(option) },
                    label = { Text(option.label) },
                )
            }
        }
    }
}

@Composable
private fun LibrarySectionHeading(
    title: String,
    count: Int,
    playActionDescription: String = "Play all liked songs",
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
                Icon(Icons.Rounded.PlayArrow, contentDescription = playActionDescription)
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
    title: String,
    subtitle: String,
    actionDescription: String,
    isPending: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    leadingContent: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingContent()
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
private fun LibraryArtistAvatar(name: String) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(Brush.linearGradient(listOf(RakyzuPurple, RakyzuAqua)), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().firstOrNull()?.uppercase() ?: "R",
            color = RakyzuBlack,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
        )
    }
}

private data class LibraryEmptyCopy(
    val icon: ImageVector,
    val title: String,
    val message: String,
)

private fun LibraryUiState.emptyStateCopy(): LibraryEmptyCopy = when {
    query.isNotBlank() -> LibraryEmptyCopy(
        Icons.Rounded.Search,
        "No Library matches",
        "Try another title, artist, album, or Library filter.",
    )
    filter == LibraryFilter.Songs -> LibraryEmptyCopy(
        Icons.Rounded.Favorite,
        "No liked songs yet",
        "Find a track you love and add it to Liked Songs.",
    )
    filter == LibraryFilter.Albums -> LibraryEmptyCopy(
        Icons.Rounded.Album,
        "No saved albums yet",
        "Save an album to keep the whole release close.",
    )
    filter == LibraryFilter.Artists -> LibraryEmptyCopy(
        Icons.Rounded.Person,
        "No followed artists yet",
        "Follow an artist to see them here.",
    )
    else -> LibraryEmptyCopy(
        Icons.Rounded.LibraryMusic,
        "Nothing to show",
        "Browse Rakyzu Music to grow your Library.",
    )
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
    actionLabel: String? = null,
    onAction: () -> Unit = {},
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
        actionLabel?.let { label ->
            Button(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(label)
            }
        }
    }
}
