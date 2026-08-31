package my.id.rakyzumusic.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import my.id.rakyzumusic.core.designsystem.theme.RakyzuSurfaceRaised
import my.id.rakyzumusic.core.model.Album
import my.id.rakyzumusic.core.model.Artist
import my.id.rakyzumusic.core.model.CatalogSnapshot
import my.id.rakyzumusic.core.model.Track

@Composable
fun SearchRoute(
    userId: String,
    repository: CatalogRepository,
    modifier: Modifier = Modifier,
    onTrackPlay: (List<Track>, Int) -> Unit = { _, _ -> },
) {
    val searchViewModel: SearchViewModel = viewModel(
        key = "search-$userId",
        factory = SearchViewModel.factory(repository),
    )
    val state by searchViewModel.uiState.collectAsStateWithLifecycle()
    key(userId) {
        SearchScreen(
            state = state,
            onQueryChange = searchViewModel::updateQuery,
            onClearQuery = searchViewModel::clearQuery,
            onTrackPlay = onTrackPlay,
            modifier = modifier,
        )
    }
}

@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onTrackPlay: (List<Track>, Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 96.dp),
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFF172E3D),
                        0.3f to RakyzuBlack,
                        1f to RakyzuBlack,
                    ),
                ),
            )
            .semantics { isTraversalGroup = true },
        contentPadding = contentPadding,
    ) {
        item(key = "search-header") {
            Text(
                text = "Search",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 28.dp, bottom = 18.dp)
                    .semantics { heading() },
            )
        }
        item(key = "search-input") {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                singleLine = true,
                label = { Text("Artists, albums, or tracks") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                    )
                },
                trailingIcon = if (state.query.isNotEmpty()) {
                    {
                        IconButton(
                            onClick = onClearQuery,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = "Clear search",
                            )
                        }
                    }
                } else {
                    null
                },
            )
            Spacer(Modifier.height(22.dp))
        }

        when {
            !state.hasObservedCatalog -> item(key = "search-loading") {
                SearchStatus(
                    title = "Loading your searchable catalog",
                    message = "Rakyzu Music is preparing your saved artists, albums, and tracks.",
                    showProgress = true,
                )
            }
            state.isCatalogEmpty -> item(key = "search-empty-catalog") {
                SearchStatus(
                    title = "Nothing to search yet",
                    message = "Your verified Rakyzu Music catalog is currently empty.",
                )
            }
            state.isReadyToBrowse -> item(key = "search-browse") {
                BrowseCatalogSummary(state.catalog)
            }
            state.hasNoResults -> item(key = "search-no-results") {
                SearchStatus(
                    title = "No results for “${state.query.trim()}”",
                    message = "Check the spelling or try another artist, album, or track.",
                )
            }
            else -> {
                item(key = "search-result-summary") {
                    SearchResultSummary(state.results)
                }
                if (state.results.artists.isNotEmpty()) {
                    item(key = "artists-heading") { SearchSectionHeading("Artists") }
                    items(
                        items = state.results.artists,
                        key = { "artist-${it.id}" },
                    ) { artist ->
                        MetadataResultRow(
                            icon = Icons.Rounded.Person,
                            title = artist.name,
                            subtitle = "Artist",
                        )
                    }
                }
                if (state.results.albums.isNotEmpty()) {
                    item(key = "albums-heading") { SearchSectionHeading("Albums") }
                    items(
                        items = state.results.albums,
                        key = { "album-${it.album.id}" },
                    ) { result ->
                        MetadataResultRow(
                            icon = Icons.Rounded.Album,
                            title = result.album.title,
                            subtitle = listOfNotNull(
                                "Album",
                                result.artistName.takeIf(String::isNotBlank),
                            ).joinToString(" · "),
                        )
                    }
                }
                if (state.results.tracks.isNotEmpty()) {
                    item(key = "tracks-heading") { SearchSectionHeading("Tracks") }
                    itemsIndexed(
                        items = state.results.tracks,
                        key = { _, track -> "track-${track.id}" },
                    ) { index, track ->
                        TrackSearchResultRow(
                            track = track,
                            traversalOrder = index.toFloat(),
                            onPlay = { onTrackPlay(state.results.tracks, index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseCatalogSummary(catalog: CatalogSnapshot) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Browse your catalog",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "Search your saved offline catalog. Your query stays on this device.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        BrowseMetric(Icons.Rounded.Person, "Artists", catalog.artists.size)
        BrowseMetric(Icons.Rounded.Album, "Albums", catalog.albums.size)
        BrowseMetric(Icons.Rounded.MusicNote, "Tracks", catalog.tracks.size)
    }
}

@Composable
private fun BrowseMetric(icon: ImageVector, label: String, count: Int) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = RakyzuSurfaceRaised,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = RakyzuAqua)
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            )
            Text(
                text = count.toString(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun SearchResultSummary(results: SearchResults) {
    val hiddenCount = results.totalMatches - results.displayedCount
    Text(
        text = if (hiddenCount > 0) {
            "Showing ${results.displayedCount} of ${results.totalMatches} results"
        } else {
            "${results.totalMatches} results"
        },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun SearchSectionHeading(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .padding(top = 22.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun MetadataResultRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ResultIcon(icon)
        ResultText(title, subtitle, Modifier.weight(1f))
    }
}

@Composable
private fun TrackSearchResultRow(
    track: Track,
    traversalOrder: Float,
    onPlay: () -> Unit,
) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(
                onClickLabel = "Play ${track.title.trim()}",
                role = Role.Button,
                onClick = onPlay,
            )
            .semantics { traversalIndex = traversalOrder },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ResultIcon(Icons.Rounded.MusicNote)
            ResultText(
                title = track.title,
                subtitle = listOf(track.artist, track.albumTitle)
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .joinToString(" · "),
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = RakyzuAqua,
            )
        }
    }
}

@Composable
private fun ResultIcon(icon: ImageVector) {
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
private fun ResultText(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 14.dp)) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SearchStatus(
    title: String,
    message: String,
    showProgress: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp)
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

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SearchScreenPreview() {
    val artist = Artist("artist-1", "Rakyzu Sessions")
    val album = Album("album-1", artist.id, "Signal Zero", "2026-08-31")
    val track = Track(
        id = "track-1",
        title = "Midnight Signal",
        artist = artist.name,
        durationMs = 180_000L,
        artistId = artist.id,
        albumId = album.id,
        albumTitle = album.title,
    )
    val catalog = CatalogSnapshot(listOf(artist), listOf(album), listOf(track), 42L)
    RakyzuMusicTheme(darkTheme = true) {
        SearchScreen(
            state = SearchUiState(
                query = "signal",
                catalog = catalog,
                results = catalog.search("signal"),
                hasObservedCatalog = true,
            ),
            onQueryChange = {},
            onClearQuery = {},
            onTrackPlay = { _, _ -> },
        )
    }
}
