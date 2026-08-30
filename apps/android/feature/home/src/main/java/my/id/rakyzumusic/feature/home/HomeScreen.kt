package my.id.rakyzumusic.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import my.id.rakyzumusic.core.data.catalog.CatalogRepository
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

@Composable
fun HomeRoute(
    userId: String,
    repository: CatalogRepository,
    versionName: String,
    displayName: String,
    modifier: Modifier = Modifier,
    onTrackPlay: (List<Track>, Int) -> Unit = { _, _ -> },
    onProfileClick: () -> Unit = {},
) {
    val homeViewModel: HomeViewModel = viewModel(
        key = "home-$userId",
        factory = HomeViewModel.factory(userId, repository),
    )
    val state by homeViewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        versionName = versionName,
        displayName = displayName,
        state = state,
        modifier = modifier,
        onRetryCatalog = homeViewModel::refresh,
        onTrackPlay = onTrackPlay,
        onProfileClick = onProfileClick,
    )
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
) {
    var selectedFilter by remember { mutableStateOf("Music") }
    val newReleaseTracks = remember(state.catalog.albums, state.catalog.tracks) {
        val tracksByAlbum = state.catalog.tracks.groupBy(Track::albumId)
        state.catalog.albums
            .sortedWith(compareByDescending { it.releaseDate })
            .flatMap { album -> tracksByAlbum[album.id].orEmpty() }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFF211240),
                        0.28f to RakyzuBlack,
                        1f to RakyzuBlack,
                    ),
                ),
            ),
        contentPadding = contentPadding,
    ) {
        item {
            HomeHeader(
                versionName = versionName,
                displayName = displayName,
                onProfileClick = onProfileClick,
            )
        }
        if (state.hasPlayableContent) {
            item {
                FilterRow(
                    selectedFilter = selectedFilter,
                    onFilterSelected = { selectedFilter = it },
                )
            }
            item {
                FeaturedCard(
                    track = state.catalog.editorialShelves.firstOrNull()?.tracks?.firstOrNull()
                        ?: state.catalog.tracks.first(),
                    onTrackPlay = {
                        val queue = state.catalog.editorialShelves.firstOrNull()?.tracks
                            .orEmpty()
                            .ifEmpty { state.catalog.tracks }
                        onTrackPlay(queue, 0)
                    },
                )
            }
        }
        if (state.isRefreshing || state.refreshMessage != null) {
            item {
                CatalogStatus(
                    state = state,
                    onRetry = onRetryCatalog,
                )
            }
        }
        if (state.isEmptyAfterRefresh) {
            item {
                EmptyHomeState(onRefresh = onRetryCatalog)
            }
        }
        if (selectedFilter == "Music" && state.recentlyPlayed.isNotEmpty()) {
            item {
                TrackShelf(
                    title = "Recently played",
                    subtitle = "Continue from your latest listening on this device.",
                    tracks = state.recentlyPlayed,
                    onTrackPlay = onTrackPlay,
                )
            }
        }
        if (selectedFilter == "Music") {
            items(
                items = state.catalog.editorialShelves,
                key = { "editorial-${it.id}" },
            ) { shelf ->
                TrackShelf(
                    title = shelf.title,
                    subtitle = shelf.subtitle,
                    tracks = shelf.tracks,
                    onTrackPlay = onTrackPlay,
                )
            }
        }
        if (newReleaseTracks.isNotEmpty()) {
            item {
                TrackShelf(
                    title = "New releases",
                    subtitle = "The latest published sounds on Rakyzu Music.",
                    tracks = newReleaseTracks,
                    onTrackPlay = onTrackPlay,
                )
            }
        }
        if (selectedFilter == "Music" && state.catalog.tracks.isNotEmpty()) {
            item {
                TrackShelf(
                    title = "All tracks",
                    subtitle = "Explore the complete verified catalog.",
                    tracks = state.catalog.tracks,
                    onTrackPlay = onTrackPlay,
                )
            }
        }
    }
}

@Composable
private fun CatalogStatus(
    state: HomeUiState,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
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
            if (state.isRefreshing) {
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
                        state.isRefreshing && !state.hasPlayableContent -> "Loading your Home feed"
                        state.isRefreshing -> "Updating your saved catalog"
                        state.isShowingSavedCatalog -> "Showing your saved catalog"
                        else -> "Catalog unavailable"
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                state.refreshMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (!state.isRefreshing) {
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
    onRefresh: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 22.dp)
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
    onProfileClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 18.dp),
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
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
) {
    val filters = listOf("Music", "New releases")
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(filters) { filter ->
            val selected = filter == selectedFilter
            FilterChip(
                selected = selected,
                onClick = { onFilterSelected(filter) },
                modifier = Modifier.heightIn(min = 48.dp),
                label = { Text(filter) },
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
    onTrackPlay: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 22.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF4B248A), Color(0xFF1E6572)),
                    ),
                )
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AlbumCover(
                colors = listOf(RakyzuPurpleSoft, RakyzuAqua),
                modifier = Modifier.size(112.dp),
            )
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
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
                    maxLines = 2,
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
    }
}

@Composable
internal fun TrackShelf(
    title: String,
    subtitle: String?,
    tracks: List<Track>,
    onTrackPlay: (List<Track>, Int) -> Unit,
) {
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
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .semantics { heading() },
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 10.dp),
            )
        }
        LazyRow(
            modifier = Modifier.semantics { isTraversalGroup = true },
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(
                items = tracks,
                key = { _, track -> track.id },
            ) { index, track ->
                Card(
                    modifier = Modifier
                        .width(156.dp)
                        .heightIn(min = 48.dp)
                        .clickable(
                            onClickLabel = track.homePlayActionLabel(),
                            role = Role.Button,
                            onClick = { onTrackPlay(tracks, index) },
                        )
                        .semantics { traversalIndex = index.toFloat() },
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                ) {
                    Column {
                        AlbumCover(
                            colors = catalogGradients[index % catalogGradients.size],
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = track.title,
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = listOf(track.artist, track.albumTitle)
                                .filter(String::isNotBlank)
                                .joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

internal fun Track.homePlayActionLabel(): String = "Play ${title.trim()}"

@Composable
private fun AlbumCover(
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(RakyzuBlack.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(34.dp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenPreview() {
    RakyzuMusicTheme(darkTheme = true) {
        HomeScreen(versionName = "0.1.2")
    }
}
