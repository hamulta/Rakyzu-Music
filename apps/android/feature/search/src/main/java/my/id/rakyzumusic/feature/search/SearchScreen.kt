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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    viewModel: SearchViewModel,
    modifier: Modifier = Modifier,
    onTrackPlay: (List<Track>, Int) -> Unit = { _, _ -> },
    onArtistClick: ((Artist) -> Unit)? = null,
    onAlbumClick: ((Album) -> Unit)? = null,
    onTrackArtistClick: ((Track) -> Unit)? = null,
    onTrackAlbumClick: ((Track) -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SearchScreen(
        state = state,
        onQueryChange = viewModel::updateQuery,
        onClearQuery = viewModel::clearQuery,
        onSearchSubmit = viewModel::submitSearch,
        onBrowseCategoryClick = viewModel::openBrowseCategory,
        onCloseBrowseCategory = viewModel::closeBrowseCategory,
        onLoadMore = viewModel::loadNextPage,
        onRetrySearch = viewModel::retrySearch,
        onRecentSearchClick = viewModel::selectRecentSearch,
        onRecentSearchesEnabledChange = viewModel::setRecentSearchesEnabled,
        onClearRecentSearches = viewModel::clearRecentSearches,
        onTrackPlay = onTrackPlay,
        onArtistClick = onArtistClick,
        onAlbumClick = onAlbumClick,
        onTrackArtistClick = onTrackArtistClick,
        onTrackAlbumClick = onTrackAlbumClick,
        modifier = modifier,
    )
}

@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onTrackPlay: (List<Track>, Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 96.dp),
    onSearchSubmit: () -> Unit = {},
    onBrowseCategoryClick: (String) -> Unit = {},
    onCloseBrowseCategory: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onRetrySearch: () -> Unit = {},
    onRecentSearchClick: (String) -> Unit = {},
    onRecentSearchesEnabledChange: (Boolean) -> Unit = {},
    onClearRecentSearches: () -> Unit = {},
    onArtistClick: ((Artist) -> Unit)? = null,
    onAlbumClick: ((Album) -> Unit)? = null,
    onTrackArtistClick: ((Track) -> Unit)? = null,
    onTrackAlbumClick: ((Track) -> Unit)? = null,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var contextualTrack by remember { mutableStateOf<Track?>(null) }

    contextualTrack?.let { track ->
        val queueIndex = state.results.tracks.indexOfFirst { it.id == track.id }
        TrackContextSheet(
            track = track,
            onDismiss = { contextualTrack = null },
            onPlay = if (queueIndex >= 0) {
                {
                    contextualTrack = null
                    onTrackPlay(state.results.tracks, queueIndex)
                }
            } else {
                null
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
        )
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

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
                    .padding(horizontal = 20.dp)
                    .focusRequester(focusRequester)
                    .testTag(SEARCH_FIELD_TAG),
                singleLine = true,
                label = { Text("Artists, albums, or tracks") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onSearchSubmit()
                    },
                ),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                    )
                },
                trailingIcon = if (state.query.isNotEmpty()) {
                    {
                        IconButton(
                            onClick = {
                                onClearQuery()
                                focusRequester.requestFocus()
                                keyboardController?.show()
                            },
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

        if (state.query.isNotBlank()) {
            when (state.remoteSearchStatus) {
                RemoteSearchStatus.Loading -> item(key = "remote-search-loading") {
                    SearchRemoteStatus(
                        icon = Icons.Rounded.Search,
                        title = "Searching the full catalog",
                        message = "Saved results stay visible while the authenticated search completes.",
                        showProgress = true,
                    )
                }
                RemoteSearchStatus.Offline -> item(key = "remote-search-offline") {
                    SearchRemoteStatus(
                        icon = Icons.Rounded.CloudOff,
                        title = "Offline results",
                        message = "Showing saved catalog matches. Full search resumes automatically when validated internet returns.",
                    )
                }
                RemoteSearchStatus.Failed -> item(key = "remote-search-failed") {
                    SearchRemoteStatus(
                        icon = Icons.Rounded.Refresh,
                        title = "Full search is temporarily unavailable",
                        message = "Saved catalog matches are still available.",
                        actionLabel = "Retry full search",
                        onAction = onRetrySearch,
                    )
                }
                RemoteSearchStatus.Idle,
                RemoteSearchStatus.Loaded,
                -> Unit
            }
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
                BrowseCatalogSummary(
                    catalog = state.catalog,
                    categories = state.browseCategories,
                    recentSearchesEnabled = state.recentSearchesEnabled,
                    recentSearches = state.recentSearches,
                    onCategoryClick = onBrowseCategoryClick,
                    onRecentSearchClick = onRecentSearchClick,
                    onRecentSearchesEnabledChange = onRecentSearchesEnabledChange,
                    onClearRecentSearches = onClearRecentSearches,
                )
            }
            state.isBrowsingCategory -> {
                val category = requireNotNull(state.selectedBrowseCategory)
                item(key = "browse-category-header") {
                    BrowseCategoryHeader(category, onCloseBrowseCategory)
                }
                itemsIndexed(
                    items = category.tracks,
                    key = { _, track -> "browse-track-${track.id}" },
                ) { index, track ->
                    TrackSearchResultRow(
                        track = track,
                        traversalOrder = index.toFloat(),
                        onPlay = { onTrackPlay(category.tracks, index) },
                        onMoreClick = { contextualTrack = track },
                    )
                }
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
                            onClickLabel = onArtistClick?.let {
                                "Open ${artist.name.trim()} artist"
                            },
                            onClick = onArtistClick?.let { callback ->
                                { callback(artist) }
                            },
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
                            onClickLabel = onAlbumClick?.let {
                                "Open ${result.album.title.trim()} album"
                            },
                            onClick = onAlbumClick?.let { callback ->
                                { callback(result.album) }
                            },
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
                            onMoreClick = { contextualTrack = track },
                        )
                    }
                }
                if (state.nextRemoteOffset != null || state.isLoadingMore) {
                    item(key = "search-load-more") {
                        LoadMoreSearchResults(
                            isLoading = state.isLoadingMore,
                            enabled = state.canLoadMore,
                            onClick = onLoadMore,
                        )
                    }
                }
            }
        }
    }
}

internal const val SEARCH_FIELD_TAG = "search_field"

@Composable
private fun BrowseCatalogSummary(
    catalog: CatalogSnapshot,
    categories: List<BrowseCategory>,
    recentSearchesEnabled: Boolean,
    recentSearches: List<String>,
    onCategoryClick: (String) -> Unit,
    onRecentSearchClick: (String) -> Unit,
    onRecentSearchesEnabledChange: (Boolean) -> Unit,
    onClearRecentSearches: () -> Unit,
) {
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
            text = "Explore curated collections offline, or submit a query for the authenticated full catalog.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        BrowseMetric(Icons.Rounded.Person, "Artists", catalog.artists.size)
        BrowseMetric(Icons.Rounded.Album, "Albums", catalog.albums.size)
        BrowseMetric(Icons.Rounded.MusicNote, "Tracks", catalog.tracks.size)

        if (categories.isNotEmpty()) {
            Text(
                text = "Explore categories",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .semantics { heading() },
            )
            categories.forEach { category ->
                BrowseCategoryRow(category, onCategoryClick)
            }
        }

        RecentSearchControls(
            enabled = recentSearchesEnabled,
            queries = recentSearches,
            onQueryClick = onRecentSearchClick,
            onEnabledChange = onRecentSearchesEnabledChange,
            onClear = onClearRecentSearches,
        )
    }
}

@Composable
private fun BrowseCategoryRow(
    category: BrowseCategory,
    onClick: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = RakyzuSurfaceRaised,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(
                onClickLabel = "Open ${category.title} category",
                role = Role.Button,
                onClick = { onClick(category.id) },
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Explore, contentDescription = null, tint = RakyzuAqua)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            ) {
                Text(
                    text = category.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = category.subtitle ?: "${category.tracks.size} curated tracks",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = category.tracks.size.toString(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun BrowseCategoryHeader(
    category: BrowseCategory,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back to browse",
                )
            }
            Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(
                    text = category.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                category.subtitle?.let { subtitle ->
                    Text(
                        text = subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        Text(
            text = "${category.tracks.size} curated tracks · available from your saved catalog",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun RecentSearchControls(
    enabled: Boolean,
    queries: List<String>,
    onQueryClick: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onClear: () -> Unit,
) {
    Text(
        text = "Recent searches",
        color = MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier
            .padding(top = 12.dp)
            .semantics { heading() },
    )
    Surface(shape = RoundedCornerShape(18.dp), color = RakyzuSurfaceRaised) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.History, contentDescription = null, tint = RakyzuAqua)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp),
                ) {
                    Text("Save recent searches", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Off by default · encrypted and isolated to this listener",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "Save recent searches" },
                )
            }
            if (enabled && queries.isEmpty()) {
                Text(
                    text = "No saved searches yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            queries.forEach { query ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(
                            onClickLabel = "Search again for $query",
                            role = Role.Button,
                            onClick = { onQueryClick(query) },
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = RakyzuAqua)
                    Text(
                        text = query,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 14.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (queries.isNotEmpty()) {
                TextButton(onClick = onClear, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Clear recent searches")
                }
            }
        }
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
    onClickLabel: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val interactionModifier = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(
            onClickLabel = onClickLabel,
            role = Role.Button,
            onClick = onClick,
        )
    }
    Row(
        modifier = interactionModifier
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
    onMoreClick: () -> Unit,
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
private fun SearchRemoteStatus(
    icon: ImageVector,
    title: String,
    message: String,
    showProgress: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = RakyzuSurfaceRaised,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showProgress) {
                CircularProgressIndicator(
                    color = RakyzuAqua,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                Icon(icon, contentDescription = null, tint = RakyzuAqua)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            ) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            actionLabel?.let { label ->
                TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun LoadMoreSearchResults(
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .heightIn(min = 48.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(10.dp))
        }
        Text(if (isLoading) "Loading more results" else "Load more results")
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
