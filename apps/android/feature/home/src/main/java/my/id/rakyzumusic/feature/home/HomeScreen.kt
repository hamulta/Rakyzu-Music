package my.id.rakyzumusic.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import my.id.rakyzumusic.core.data.catalog.CatalogRepository
import my.id.rakyzumusic.core.data.media.MediaDeliveryRepository
import my.id.rakyzumusic.core.data.network.ConnectivityMonitor
import my.id.rakyzumusic.core.designsystem.theme.RakyzuAqua
import my.id.rakyzumusic.core.designsystem.theme.RakyzuBlack
import my.id.rakyzumusic.core.designsystem.theme.RakyzuMusicTheme
import my.id.rakyzumusic.core.designsystem.theme.RakyzuPurple
import my.id.rakyzumusic.core.designsystem.theme.RakyzuPurpleSoft
import my.id.rakyzumusic.core.designsystem.theme.RakyzuSurfaceRaised
import my.id.rakyzumusic.core.model.Track

private val catalogGradients = listOf(
    listOf(Color(0xFF3B1B75), RakyzuAqua),
    listOf(Color(0xFF7A2457), RakyzuPurpleSoft),
    listOf(Color(0xFF123C5A), Color(0xFF59C7F1)),
)

internal enum class HomeFilter(
    val storageKey: String,
    val label: String,
) {
    Music(storageKey = "music", label = "Music"),
    NewReleases(storageKey = "new-releases", label = "New releases"),
    ;

    companion object {
        fun restore(storageKey: String?): HomeFilter = entries
            .firstOrNull { it.storageKey == storageKey }
            ?: Music
    }
}

private val homeFilters = HomeFilter.entries

internal data class HomeSectionVisibility(
    val recentlyPlayed: Boolean,
    val editorialShelves: Boolean,
    val newReleases: Boolean,
    val allTracks: Boolean,
)

internal fun HomeUiState.toHomeSectionVisibility(
    filter: HomeFilter,
): HomeSectionVisibility {
    val showsMusicSections = filter == HomeFilter.Music
    return HomeSectionVisibility(
        recentlyPlayed = showsMusicSections && recentlyPlayed.isNotEmpty(),
        editorialShelves = showsMusicSections && catalog.editorialShelves.isNotEmpty(),
        newReleases = derivedSections.newReleaseTracks.isNotEmpty(),
        allTracks = showsMusicSections && catalog.tracks.isNotEmpty(),
    )
}

internal data class HomeLayoutSpec(
    val horizontalPadding: Dp,
    val trackCardWidth: Dp,
    val trackTextMaxLines: Int,
    val useStackedFeaturedCard: Boolean,
) {
    companion object {
        val Standard = HomeLayoutSpec(
            horizontalPadding = 20.dp,
            trackCardWidth = 156.dp,
            trackTextMaxLines = 1,
            useStackedFeaturedCard = false,
        )
    }
}

internal fun resolveHomeLayoutSpec(
    availableWidth: Dp,
    fontScale: Float,
): HomeLayoutSpec {
    val isCompactScreen = availableWidth < 360.dp
    val usesLargeText = fontScale >= 1.3f
    if (!isCompactScreen && !usesLargeText) return HomeLayoutSpec.Standard

    val horizontalPadding = if (isCompactScreen) 16.dp else 20.dp
    val availableCardWidth = availableWidth - (horizontalPadding * 2)
    return HomeLayoutSpec(
        horizontalPadding = horizontalPadding,
        trackCardWidth = availableCardWidth.coerceIn(156.dp, 220.dp),
        trackTextMaxLines = 2,
        useStackedFeaturedCard = true,
    )
}

@Composable
fun HomeRoute(
    userId: String,
    repository: CatalogRepository,
    mediaDeliveryRepository: MediaDeliveryRepository,
    connectivityMonitor: ConnectivityMonitor,
    versionName: String,
    displayName: String,
    modifier: Modifier = Modifier,
    onTrackPlay: (List<Track>, Int) -> Unit = { _, _ -> },
    onProfileClick: () -> Unit = {},
) {
    val homeViewModel: HomeViewModel = viewModel(
        key = "home-$userId",
        factory = HomeViewModel.factory(userId, repository, connectivityMonitor),
    )
    val state by homeViewModel.uiState.collectAsStateWithLifecycle()
    val artworkRequestProvider: ArtworkRequestProvider = remember(mediaDeliveryRepository) {
        mediaDeliveryRepository::artworkRequest
    }
    key(userId) {
        HomeScreen(
            versionName = versionName,
            displayName = displayName,
            state = state,
            artworkRequestProvider = artworkRequestProvider,
            modifier = modifier,
            onRetryCatalog = homeViewModel::refresh,
            onTrackPlay = onTrackPlay,
            onProfileClick = onProfileClick,
        )
    }
}

@Composable
fun HomeScreen(
    versionName: String,
    displayName: String = "Rakyzu Listener",
    state: HomeUiState = HomeUiState(),
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 84.dp),
    onRetryCatalog: () -> Unit = {},
    onTrackPlay: (List<Track>, Int) -> Unit = { _, _ -> },
    onProfileClick: () -> Unit = {},
    artworkRequestProvider: ArtworkRequestProvider = unavailableArtworkRequestProvider,
) {
    var selectedFilterKey by rememberSaveable {
        mutableStateOf(HomeFilter.Music.storageKey)
    }
    val selectedFilter = HomeFilter.restore(selectedFilterKey)
    val sectionVisibility = state.toHomeSectionVisibility(selectedFilter)
    val homeListState = rememberLazyListState()
    val featuredQueue = state.derivedSections.featuredQueue
    val onFeaturedTrackPlay = remember(featuredQueue, onTrackPlay) {
        { onTrackPlay(featuredQueue, 0) }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val fontScale = LocalDensity.current.fontScale
        val layoutSpec = remember(maxWidth, fontScale) {
            resolveHomeLayoutSpec(
                availableWidth = maxWidth,
                fontScale = fontScale,
            )
        }
        LazyColumn(
            state = homeListState,
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color(0xFF211240),
                            0.28f to RakyzuBlack,
                            1f to RakyzuBlack,
                        ),
                    ),
                )
                .testTag("home-list"),
            contentPadding = contentPadding,
        ) {
            item(key = "home-header") {
                HomeHeader(
                    versionName = versionName,
                    displayName = displayName,
                    horizontalPadding = layoutSpec.horizontalPadding,
                    onProfileClick = onProfileClick,
                )
            }
            if (state.hasPlayableContent) {
                item(key = "home-filters") {
                    FilterRow(
                        selectedFilter = selectedFilter,
                        horizontalPadding = layoutSpec.horizontalPadding,
                        onFilterSelected = { selectedFilterKey = it.storageKey },
                    )
                }
                item(key = "catalog-freshness") {
                    CatalogFreshnessMetadata(
                        freshness = state.catalogFreshness,
                        horizontalPadding = layoutSpec.horizontalPadding,
                    )
                }
                item(key = "featured-track") {
                    FeaturedCard(
                        track = state.derivedSections.featuredTrack,
                        layoutSpec = layoutSpec,
                        artworkRequestProvider = artworkRequestProvider,
                        onTrackPlay = onFeaturedTrackPlay,
                    )
                }
            }
            if (state.isRefreshing || state.refreshMessage != null) {
                item(key = "catalog-status") {
                    CatalogStatus(
                        isRefreshing = state.isRefreshing,
                        isWaitingForConnection = state.isWaitingForConnection,
                        hasPlayableContent = state.hasPlayableContent,
                        isShowingStaleSavedCatalog = state.isShowingStaleSavedCatalog,
                        isShowingSavedCatalog = state.isShowingSavedCatalog,
                        refreshMessage = state.refreshMessage,
                        horizontalPadding = layoutSpec.horizontalPadding,
                        onRetry = onRetryCatalog,
                    )
                }
            }
            if (state.isEmptyAfterRefresh) {
                item(key = "empty-home") {
                    EmptyHomeState(
                        horizontalPadding = layoutSpec.horizontalPadding,
                        onRefresh = onRetryCatalog,
                    )
                }
            }
            if (sectionVisibility.recentlyPlayed) {
                item(key = "recently-played") {
                    TrackShelf(
                        title = "Recently played",
                        subtitle = "Continue from your latest listening on this device.",
                        tracks = state.recentlyPlayed,
                        layoutSpec = layoutSpec,
                        artworkRequestProvider = artworkRequestProvider,
                        onTrackPlay = onTrackPlay,
                    )
                }
            }
            if (sectionVisibility.editorialShelves) {
                items(
                    items = state.catalog.editorialShelves,
                    key = { "editorial-${it.id}" },
                ) { shelf ->
                    TrackShelf(
                        title = shelf.title,
                        subtitle = shelf.subtitle,
                        tracks = shelf.tracks,
                        layoutSpec = layoutSpec,
                        artworkRequestProvider = artworkRequestProvider,
                        onTrackPlay = onTrackPlay,
                    )
                }
            }
            if (sectionVisibility.newReleases) {
                item(key = "new-releases") {
                    TrackShelf(
                        title = "New releases",
                        subtitle = "The latest published sounds on Rakyzu Music.",
                        tracks = state.derivedSections.newReleaseTracks,
                        layoutSpec = layoutSpec,
                        artworkRequestProvider = artworkRequestProvider,
                        onTrackPlay = onTrackPlay,
                    )
                }
            }
            if (sectionVisibility.allTracks) {
                item(key = "all-tracks") {
                    TrackShelf(
                        title = "All tracks",
                        subtitle = "Explore the complete verified catalog.",
                        tracks = state.catalog.tracks,
                        layoutSpec = layoutSpec,
                        artworkRequestProvider = artworkRequestProvider,
                        onTrackPlay = onTrackPlay,
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogFreshnessMetadata(
    freshness: CatalogFreshness,
    horizontalPadding: Dp,
) {
    val statusColor = if (freshness.isStale) Color(0xFFFFC857) else RakyzuAqua
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .padding(top = 8.dp)
            .semantics {
                contentDescription = "Catalog freshness: ${freshness.label}"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(statusColor),
        )
        Text(
            text = freshness.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun CatalogStatus(
    isRefreshing: Boolean,
    isWaitingForConnection: Boolean,
    hasPlayableContent: Boolean,
    isShowingStaleSavedCatalog: Boolean,
    isShowingSavedCatalog: Boolean,
    refreshMessage: String?,
    horizontalPadding: Dp,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .padding(bottom = 18.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(18.dp),
        color = RakyzuSurfaceRaised.copy(alpha = 0.92f),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = RakyzuAqua,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = RakyzuAqua,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        isRefreshing && !hasPlayableContent -> "Loading your Home feed"
                        isRefreshing -> "Updating your saved catalog"
                        isWaitingForConnection -> "Waiting for connection"
                        isShowingStaleSavedCatalog -> "Saved catalog may be out of date"
                        isShowingSavedCatalog -> "Showing your saved catalog"
                        else -> "Catalog unavailable"
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                refreshMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (!isRefreshing) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RakyzuAqua,
                        contentColor = RakyzuBlack,
                    ),
                ) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
private fun EmptyHomeState(
    horizontalPadding: Dp,
    onRefresh: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 22.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(24.dp),
        color = RakyzuSurfaceRaised.copy(alpha = 0.92f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = RakyzuAqua,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = "Your Home feed is empty",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "Published music will appear here when it is available.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onRefresh,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RakyzuAqua,
                    contentColor = RakyzuBlack,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Refresh")
            }
        }
    }
}

@Composable
private fun HomeHeader(
    versionName: String,
    displayName: String,
    horizontalPadding: Dp,
    onProfileClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = horizontalPadding, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(RakyzuPurple, RakyzuAqua)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "R",
                color = RakyzuBlack,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Good evening, ${displayName.substringBefore(' ')}",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "Rakyzu Music · v$versionName",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        IconButton(
            onClick = onProfileClick,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = "Profile",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun FilterRow(
    selectedFilter: HomeFilter,
    horizontalPadding: Dp,
    onFilterSelected: (HomeFilter) -> Unit,
) {
    val filterListState = rememberLazyListState()
    LazyRow(
        state = filterListState,
        contentPadding = PaddingValues(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = homeFilters,
            key = HomeFilter::storageKey,
        ) { filter ->
            val selected = filter == selectedFilter
            FilterChip(
                selected = selected,
                onClick = { onFilterSelected(filter) },
                modifier = Modifier.heightIn(min = 48.dp),
                label = { Text(filter.label) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = RakyzuSurfaceRaised.copy(alpha = 0.82f),
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    selectedContainerColor = RakyzuAqua,
                    selectedLabelColor = RakyzuBlack,
                ),
                border = null,
            )
        }
    }
}

@Composable
private fun FeaturedCard(
    track: Track?,
    layoutSpec: HomeLayoutSpec,
    artworkRequestProvider: ArtworkRequestProvider,
    onTrackPlay: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = layoutSpec.horizontalPadding, vertical = 22.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        val contentModifier = Modifier
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF4B248A), Color(0xFF1E6572)),
                ),
            )
            .padding(18.dp)
        if (layoutSpec.useStackedFeaturedCard) {
            Column(modifier = contentModifier) {
                AlbumArtwork(
                    albumId = track?.albumId.orEmpty(),
                    colors = listOf(RakyzuPurpleSoft, RakyzuAqua),
                    artworkRequestProvider = artworkRequestProvider,
                    modifier = Modifier.size(128.dp),
                )
                Spacer(Modifier.height(16.dp))
                FeaturedDetails(
                    track = track,
                    onTrackPlay = onTrackPlay,
                )
            }
        } else {
            Row(
                modifier = contentModifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumArtwork(
                    albumId = track?.albumId.orEmpty(),
                    colors = listOf(RakyzuPurpleSoft, RakyzuAqua),
                    artworkRequestProvider = artworkRequestProvider,
                    modifier = Modifier.size(112.dp),
                )
                Spacer(Modifier.width(18.dp))
                FeaturedDetails(
                    track = track,
                    onTrackPlay = onTrackPlay,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FeaturedDetails(
    track: Track?,
    onTrackPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "RAKYZU ORIGINAL",
            color = RakyzuAqua,
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Sound without limits",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "Your new listening space starts here.",
            color = Color.White.copy(alpha = 0.78f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(10.dp))
        Surface(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable(
                    enabled = track != null,
                    onClickLabel = track?.homePlayActionLabel(),
                    role = Role.Button,
                    onClick = onTrackPlay,
                ),
            shape = CircleShape,
            color = RakyzuAqua,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = RakyzuBlack,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Play",
                    color = RakyzuBlack,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
internal fun TrackShelf(
    title: String,
    subtitle: String?,
    tracks: List<Track>,
    layoutSpec: HomeLayoutSpec = HomeLayoutSpec.Standard,
    artworkRequestProvider: ArtworkRequestProvider = unavailableArtworkRequestProvider,
    onTrackPlay: (List<Track>, Int) -> Unit,
) {
    val shelfListState = rememberLazyListState()
    Column(
        modifier = Modifier
            .padding(bottom = 28.dp)
            .semantics { isTraversalGroup = true },
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(horizontal = layoutSpec.horizontalPadding, vertical = 10.dp)
                .semantics { heading() },
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(horizontal = layoutSpec.horizontalPadding)
                    .padding(bottom = 10.dp),
            )
        }
        LazyRow(
            state = shelfListState,
            modifier = Modifier
                .semantics { isTraversalGroup = true }
                .testTag("track-shelf-list-$title"),
            contentPadding = PaddingValues(horizontal = layoutSpec.horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(
                items = tracks,
                key = { _, track -> track.id },
            ) { index, track ->
                val playActionLabel = remember(track.id, track.title) {
                    track.homePlayActionLabel()
                }
                val subtitle = remember(track.artist, track.albumTitle) {
                    track.homeSubtitle()
                }
                Card(
                    modifier = Modifier
                        .width(layoutSpec.trackCardWidth)
                        .heightIn(min = 48.dp)
                        .clickable(
                            onClickLabel = playActionLabel,
                            role = Role.Button,
                            onClick = { onTrackPlay(tracks, index) },
                        )
                        .semantics { traversalIndex = index.toFloat() },
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                ) {
                    Column {
                        AlbumArtwork(
                            albumId = track.albumId,
                            colors = catalogGradients[index % catalogGradients.size],
                            artworkRequestProvider = artworkRequestProvider,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = track.title,
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = layoutSpec.trackTextMaxLines,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = subtitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = layoutSpec.trackTextMaxLines,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

internal fun Track.homePlayActionLabel(): String = "Play ${title.trim()}"

internal fun Track.homeSubtitle(): String {
    val artistLabel = artist.trim()
    val albumLabel = albumTitle.trim()
    return when {
        artistLabel.isEmpty() -> albumLabel
        albumLabel.isEmpty() -> artistLabel
        else -> "$artistLabel · $albumLabel"
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenPreview() {
    RakyzuMusicTheme(darkTheme = true) {
        HomeScreen(versionName = "0.2.4")
    }
}
