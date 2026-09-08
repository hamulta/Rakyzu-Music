package my.id.rakyzumusic.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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

internal data class LibraryLayoutSpec(
    val horizontalPadding: Dp,
    val rowMinimumHeight: Dp,
    val artworkSize: Dp,
    val textMaxLines: Int,
) {
    companion object {
        val Standard = LibraryLayoutSpec(
            horizontalPadding = 20.dp,
            rowMinimumHeight = 72.dp,
            artworkSize = 48.dp,
            textMaxLines = 1,
        )
    }
}

internal fun resolveLibraryLayoutSpec(
    availableWidth: Dp,
    fontScale: Float,
): LibraryLayoutSpec {
    val isCompactScreen = availableWidth < 360.dp
    val usesLargeText = fontScale >= 1.3f
    if (!isCompactScreen && !usesLargeText) return LibraryLayoutSpec.Standard
    return LibraryLayoutSpec(
        horizontalPadding = if (isCompactScreen) 16.dp else 20.dp,
        rowMinimumHeight = if (usesLargeText) 88.dp else 72.dp,
        artworkSize = if (usesLargeText) 56.dp else 48.dp,
        textMaxLines = 2,
    )
}

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
    BoxWithConstraints(
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
    ) {
        val fontScale = LocalDensity.current.fontScale
        val layoutSpec = remember(maxWidth, fontScale) {
            resolveLibraryLayoutSpec(maxWidth, fontScale)
        }
        val visibleContent = remember(state.library, state.query, state.filter, state.sort) {
            deriveLibraryVisibleContent(state.library, state.query, state.filter, state.sort)
        }
        val hasNoMatchingItems = state.hasObservedLibrary &&
            !state.library.isEmpty && !visibleContent.hasItems

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .semantics { isTraversalGroup = true },
            contentPadding = contentPadding,
        ) {
            item(key = "library-header") {
                LibraryHeader(
                    isRefreshing = state.isRefreshing,
                    pendingMutationCount = state.library.pendingMutationCount,
                    freshness = state.freshness,
                    isOnline = state.isOnline,
                    horizontalPadding = layoutSpec.horizontalPadding,
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
                            .padding(
                                horizontal = layoutSpec.horizontalPadding,
                                vertical = 6.dp,
                            )
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    ) {
                        Text(
                            text = if (state.isShowingStaleSavedLibrary) {
                                "$message Saved Library data may be out of date " +
                                    "(${state.freshness.label.lowercase()})."
                            } else {
                                message
                            },
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
                            horizontalPadding = layoutSpec.horizontalPadding,
                            onQueryChange = onQueryChange,
                            onClearQuery = onClearQuery,
                            onFilterSelected = onFilterSelected,
                            onSortSelected = onSortSelected,
                        )
                    }

                    if (hasNoMatchingItems) {
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
                                onAction = if (state.query.isNotBlank()) {
                                    onClearQuery
                                } else {
                                    onBrowseMusic
                                },
                            )
                        }
                    } else {
                        if (visibleContent.likedTracks.isNotEmpty()) {
                            item(key = "liked-heading") {
                                LibrarySectionHeading(
                                    title = "Liked Songs",
                                    count = visibleContent.likedTracks.size,
                                    playActionDescription =
                                        "Play ${visibleContent.likedTracks.size} liked songs " +
                                        "in ${state.sort.label} order",
                                    onPlay = { onTrackPlay(visibleContent.likedTracks, 0) },
                                    horizontalPadding = layoutSpec.horizontalPadding,
                                )
                            }
                            itemsIndexed(
                                items = visibleContent.likedTracks,
                                key = { _, track -> "liked-${track.id}" },
                            ) { index, track ->
                                LibraryTrackRow(
                                    track = track,
                                    isPending = state.isPending(LibraryItemKind.Track, track.id),
                                    onPlay = { onTrackPlay(visibleContent.likedTracks, index) },
                                    onRemove = { onRemoveTrack(track) },
                                    layoutSpec = layoutSpec,
                                )
                            }
                        }

                        if (visibleContent.savedAlbums.isNotEmpty()) {
                            item(key = "albums-heading") {
                                LibrarySectionHeading(
                                    title = "Saved Albums",
                                    count = visibleContent.savedAlbums.size,
                                    horizontalPadding = layoutSpec.horizontalPadding,
                                )
                            }
                            items(
                                items = visibleContent.savedAlbums,
                                key = { "saved-${it.album.id}" },
                            ) { saved ->
                                LibraryCollectionRow(
                                    title = saved.album.title,
                                    subtitle = saved.artistName.ifBlank { "Unknown artist" },
                                    actionDescription = "Remove ${saved.album.title} from Library",
                                    openDescription = "Open album ${saved.album.title}",
                                    isPending = state.isPending(
                                        LibraryItemKind.Album,
                                        saved.album.id,
                                    ),
                                    onClick = { onAlbumClick(saved.album) },
                                    onRemove = { onRemoveAlbum(saved.album) },
                                    leadingContent = {
                                        LibraryAlbumArtwork(
                                            albumId = saved.album.id,
                                            requestProvider = artworkRequestProvider,
                                            size = layoutSpec.artworkSize,
                                        )
                                    },
                                    layoutSpec = layoutSpec,
                                )
                            }
                        }

                        if (visibleContent.followedArtists.isNotEmpty()) {
                            item(key = "artists-heading") {
                                LibrarySectionHeading(
                                    title = "Followed Artists",
                                    count = visibleContent.followedArtists.size,
                                    horizontalPadding = layoutSpec.horizontalPadding,
                                )
                            }
                            items(
                                items = visibleContent.followedArtists,
                                key = { "followed-${it.id}" },
                            ) { artist ->
                                LibraryCollectionRow(
                                    title = artist.name,
                                    subtitle = "Artist",
                                    actionDescription = "Unfollow ${artist.name}",
                                    openDescription = "Open artist ${artist.name}",
                                    isPending = state.isPending(
                                        LibraryItemKind.Artist,
                                        artist.id,
                                    ),
                                    onClick = { onArtistClick(artist) },
                                    onRemove = { onUnfollowArtist(artist) },
                                    leadingContent = {
                                        LibraryArtistAvatar(
                                            artist.name,
                                            layoutSpec.artworkSize,
                                        )
                                    },
                                    layoutSpec = layoutSpec,
                                )
                            }
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
    freshness: LibraryFreshness,
    isOnline: Boolean,
    horizontalPadding: Dp,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = horizontalPadding,
                end = 8.dp,
                top = 20.dp,
                bottom = 8.dp,
            ),
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
                modifier = Modifier.semantics {
                    stateDescription = if (pendingMutationCount > 0) {
                        "Library synchronization pending"
                    } else {
                        "Library synchronized"
                    }
                },
            )
            Text(
                text = freshness.label,
                color = if (freshness.isStale) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.semantics {
                    stateDescription = if (freshness.isStale) {
                        "Saved Library data is stale"
                    } else {
                        "Saved Library freshness"
                    }
                },
            )
        }
        IconButton(
            onClick = onRefresh,
            enabled = !isRefreshing,
            modifier = Modifier.size(48.dp),
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(24.dp)
                        .semantics { contentDescription = "Refreshing Library" },
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = if (isOnline) {
                        "Refresh Library"
                    } else {
                        "Retry Library sync when online"
                    },
                )
            }
        }
    }
}

@Composable
private fun LibraryControls(
    query: String,
    filter: LibraryFilter,
    sort: LibrarySort,
    horizontalPadding: Dp,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onFilterSelected: (LibraryFilter) -> Unit,
    onSortSelected: (LibrarySort) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { isTraversalGroup = true },
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = 8.dp),
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
                .padding(horizontal = horizontalPadding, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibraryFilter.entries.forEach { option ->
                FilterChip(
                    selected = option == filter,
                    onClick = { onFilterSelected(option) },
                    label = { Text(option.label) },
                    modifier = Modifier.semantics {
                        stateDescription = if (option == filter) {
                            "Selected Library filter"
                        } else {
                            "Library filter"
                        }
                    },
                )
            }
        }
        Text(
            text = "Sort Library",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = 8.dp,
                )
                .semantics { heading() },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibrarySort.entries.forEach { option ->
                FilterChip(
                    selected = option == sort,
                    onClick = { onSortSelected(option) },
                    label = { Text(option.label) },
                    modifier = Modifier.semantics {
                        stateDescription = if (option == sort) {
                            "Selected Library sort order"
                        } else {
                            "Library sort order"
                        }
                    },
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
    horizontalPadding: Dp = LibraryLayoutSpec.Standard.horizontalPadding,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = horizontalPadding, end = 8.dp, top = 18.dp, bottom = 6.dp),
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
    layoutSpec: LibraryLayoutSpec = LibraryLayoutSpec.Standard,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = layoutSpec.rowMinimumHeight)
            .clickable(role = Role.Button, onClickLabel = "Play ${track.title}", onClick = onPlay)
            .padding(
                start = layoutSpec.horizontalPadding,
                end = 8.dp,
                top = 8.dp,
                bottom = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibraryIcon(Icons.Rounded.Favorite, layoutSpec.artworkSize)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = track.title,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                maxLines = layoutSpec.textMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${track.artist} · ${track.formattedDuration()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = layoutSpec.textMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onRemove,
            enabled = !isPending,
            modifier = Modifier.size(48.dp),
        ) {
            if (isPending) {
                CircularProgressIndicator(
                    Modifier
                        .size(20.dp)
                        .semantics {
                            contentDescription = "Updating ${track.title} in Liked Songs"
                        },
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    Icons.Rounded.Favorite,
                    contentDescription = "Remove ${track.title} from Liked Songs",
                )
            }
        }
    }
}

@Composable
private fun LibraryCollectionRow(
    title: String,
    subtitle: String,
    actionDescription: String,
    openDescription: String,
    isPending: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    leadingContent: @Composable () -> Unit,
    layoutSpec: LibraryLayoutSpec = LibraryLayoutSpec.Standard,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = layoutSpec.rowMinimumHeight)
            .clickable(role = Role.Button, onClickLabel = openDescription, onClick = onClick)
            .padding(
                start = layoutSpec.horizontalPadding,
                end = 8.dp,
                top = 8.dp,
                bottom = 8.dp,
            ),
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
                maxLines = layoutSpec.textMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = layoutSpec.textMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRemove, enabled = !isPending, modifier = Modifier.size(48.dp)) {
            if (isPending) {
                CircularProgressIndicator(
                    Modifier
                        .size(20.dp)
                        .semantics { contentDescription = "$actionDescription pending" },
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(Icons.Rounded.Bookmark, contentDescription = actionDescription)
            }
        }
    }
}

@Composable
private fun LibraryArtistAvatar(name: String, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(Brush.linearGradient(listOf(RakyzuPurple, RakyzuAqua)), CircleShape)
            .clearAndSetSemantics { },
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
private fun LibraryIcon(icon: ImageVector, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(
                Brush.linearGradient(listOf(RakyzuPurple, RakyzuAqua)),
                CircleShape,
            )
            .clearAndSetSemantics { },
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
            modifier = Modifier.semantics { heading() },
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
