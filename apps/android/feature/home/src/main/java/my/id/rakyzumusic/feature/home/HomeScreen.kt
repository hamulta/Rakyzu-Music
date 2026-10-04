package my.id.rakyzumusic.feature.home

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.math.absoluteValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.id.rakyzumusic.core.data.catalog.CatalogRepository
import my.id.rakyzumusic.core.data.media.MediaDeliveryRepository
import my.id.rakyzumusic.core.data.network.ConnectivityMonitor
import my.id.rakyzumusic.core.designsystem.theme.RakyzuMusicTheme
import my.id.rakyzumusic.core.model.DiscoveryMode
import my.id.rakyzumusic.core.model.EditorialGroupMutation
import my.id.rakyzumusic.core.model.EditorialPlacement
import my.id.rakyzumusic.core.model.EditorialShelf
import my.id.rakyzumusic.core.model.ListeningHistoryItem
import my.id.rakyzumusic.core.model.PersonalizedCollection
import my.id.rakyzumusic.core.model.Track

private val HomeBackground = Color(0xFF121111)
private val HomeAqua = Color(0xFF00C2CB)
private val CompactCard = Color(0xFF436369).copy(alpha = 0.20f)
private val MixAccents = listOf(
    Color(0xFFFF7777), Color(0xFFFFB677), Color(0xFFFFFA77), Color(0xFF77FF95),
    Color(0xFF77D5FF), Color(0xFF777DFF), Color(0xFFC077FF),
)
private val artworkGradients = listOf(
    listOf(Color(0xFFEF5350), Color(0xFF8E2430)),
    listOf(Color(0xFFFFB74D), Color(0xFF7E4A10)),
    listOf(Color(0xFFFDD835), Color(0xFF77721D)),
    listOf(Color(0xFF43A047), Color(0xFF163F2A)),
    listOf(Color(0xFF29B6F6), Color(0xFF12445D)),
    listOf(Color(0xFF5C6BC0), Color(0xFF20284D)),
    listOf(Color(0xFFAB47BC), Color(0xFF4A1E52)),
)

internal enum class HomeFilter(val storageKey: String, val label: String) {
    Music("music", "Music"),
    MadeForYou("made-for-you", "Made for you"),
    NewReleases("new-releases", "New releases");

    companion object {
        fun restore(storageKey: String?): HomeFilter = entries
            .firstOrNull { it.storageKey == storageKey }
            ?: Music
    }
}

internal data class HomeSectionVisibility(
    val recentlyPlayed: Boolean,
    val editorialShelves: Boolean,
    val newReleases: Boolean,
    val allTracks: Boolean,
    val smartRecommendations: Boolean = false,
    val mixes: Boolean = false,
    val radioStations: Boolean = false,
    val personalizationControls: Boolean = false,
)

internal fun HomeUiState.toHomeSectionVisibility(filter: HomeFilter): HomeSectionVisibility {
    val music = filter == HomeFilter.Music
    val personalized = filter != HomeFilter.NewReleases
    return HomeSectionVisibility(
        recentlyPlayed = music && recentlyPlayed.isNotEmpty(),
        editorialShelves = music && catalog.editorialShelves.isNotEmpty(),
        newReleases = filter != HomeFilter.MadeForYou && derivedSections.newReleaseTracks.isNotEmpty(),
        allTracks = music && catalog.tracks.isNotEmpty(),
        smartRecommendations = personalized && recommendations.isNotEmpty(),
        mixes = personalized && mixes.isNotEmpty(),
        radioStations = personalized && radioStations.isNotEmpty(),
        personalizationControls = personalized && catalog.tracks.isNotEmpty(),
    )
}

internal data class HomeLayoutSpec(
    val horizontalPadding: Dp,
    val trackCardWidth: Dp,
    val trackTextMaxLines: Int,
    val useStackedFeaturedCard: Boolean,
) {
    companion object {
        val Standard = HomeLayoutSpec(20.dp, 156.dp, 1, false)
    }
}

internal fun resolveHomeLayoutSpec(availableWidth: Dp, fontScale: Float): HomeLayoutSpec {
    val compact = availableWidth < 360.dp
    val largeText = fontScale >= 1.3f
    return when {
        availableWidth >= 840.dp && !largeText -> HomeLayoutSpec(48.dp, 196.dp, 2, false)
        availableWidth >= 600.dp && !largeText -> HomeLayoutSpec(32.dp, 180.dp, 2, false)
        compact || largeText -> HomeLayoutSpec(
            if (compact) 16.dp else 20.dp,
            (availableWidth - if (compact) 32.dp else 40.dp).coerceIn(156.dp, 220.dp),
            2,
            true,
        )
        else -> HomeLayoutSpec.Standard
    }
}

@Composable
fun HomeRoute(
    userId: String,
    repository: CatalogRepository,
    mediaDeliveryRepository: MediaDeliveryRepository,
    connectivityMonitor: ConnectivityMonitor,
    versionName: String,
    displayName: String,
    avatarAvailable: Boolean = false,
    avatarRevision: String? = null,
    artworkRevision: Long = 0L,
    modifier: Modifier = Modifier,
    onTrackPlay: (List<Track>, Int) -> Unit = { _, _ -> },
    onProfileClick: () -> Unit = {},
    onSettingsClick: () -> Unit = onProfileClick,
    canManageEditorial: Boolean = false,
    onDeleteGlobalCard: (EditorialShelf) -> Unit = {},
    onDeleteGlobalCardArtwork: (EditorialShelf) -> Unit = {},
    onSaveGlobalCard: (EditorialGroupMutation, ByteArray?) -> Unit = { _, _ -> },
    likedTrackIds: Set<String> = emptySet(),
    savedAlbumIds: Set<String> = emptySet(),
    followedArtistIds: Set<String> = emptySet(),
    pendingTrackIds: Set<String> = emptySet(),
    pendingAlbumIds: Set<String> = emptySet(),
    pendingArtistIds: Set<String> = emptySet(),
    onTrackLikeChange: (Track, Boolean) -> Unit = { _, _ -> },
    onAlbumSaveChange: (Track, Boolean) -> Unit = { _, _ -> },
    onArtistFollowChange: (Track, Boolean) -> Unit = { _, _ -> },
) {
    val homeViewModel: HomeViewModel = viewModel(
        key = "home-$userId",
        factory = HomeViewModel.factory(userId, repository, connectivityMonitor),
    )
    val state by homeViewModel.uiState.collectAsStateWithLifecycle()
    val artworkRequestProvider: ArtworkRequestProvider = remember(mediaDeliveryRepository) {
        mediaDeliveryRepository::artworkRequest
    }
    val recommendationArtworkRequestProvider: ArtworkRequestProvider = remember(mediaDeliveryRepository, artworkRevision) {
        mediaDeliveryRepository::recommendationArtworkRequest
    }
    val profileArtworkRequestProvider: ArtworkRequestProvider = remember(mediaDeliveryRepository, avatarRevision) {
        { profileId -> mediaDeliveryRepository.profileAvatarRequest(profileId) }
    }
    key(userId) {
        HomeScreen(
            versionName = versionName,
            displayName = displayName,
            personalizationSeed = userId,
            avatarId = userId.takeIf { avatarAvailable },
            artworkRevision = artworkRevision,
            profileArtworkRequestProvider = profileArtworkRequestProvider,
            state = state,
            artworkRequestProvider = artworkRequestProvider,
            recommendationArtworkRequestProvider = recommendationArtworkRequestProvider,
            modifier = modifier,
            onRetryCatalog = homeViewModel::refresh,
            onTrackPlay = onTrackPlay,
            onEditorialGroupOpen = homeViewModel::recordEditorialGroupOpen,
            onProfileClick = onProfileClick,
            onSettingsClick = onSettingsClick,
            canManageEditorial = canManageEditorial,
            onDeleteGlobalCard = onDeleteGlobalCard,
            onDeleteGlobalCardArtwork = onDeleteGlobalCardArtwork,
            onSaveGlobalCard = onSaveGlobalCard,
            likedTrackIds = likedTrackIds,
            savedAlbumIds = savedAlbumIds,
            followedArtistIds = followedArtistIds,
            pendingTrackIds = pendingTrackIds,
            pendingAlbumIds = pendingAlbumIds,
            pendingArtistIds = pendingArtistIds,
            onTrackLikeChange = onTrackLikeChange,
            onAlbumSaveChange = onAlbumSaveChange,
            onArtistFollowChange = onArtistFollowChange,
        )
    }
}

private enum class HomeSection { Continue, TopMixes, Recent, Shows }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    @Suppress("UNUSED_PARAMETER") versionName: String,
    displayName: String = "Rakyzu Listener",
    personalizationSeed: String = "preview",
    avatarId: String? = null,
    artworkRevision: Long = 0L,
    state: HomeUiState = HomeUiState(),
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 28.dp),
    onRetryCatalog: () -> Unit = {},
    onTrackPlay: (List<Track>, Int) -> Unit = { _, _ -> },
    onEditorialGroupOpen: (String) -> Unit = {},
    onProfileClick: () -> Unit = {},
    onSettingsClick: () -> Unit = onProfileClick,
    artworkRequestProvider: ArtworkRequestProvider = unavailableArtworkRequestProvider,
    recommendationArtworkRequestProvider: ArtworkRequestProvider = unavailableArtworkRequestProvider,
    profileArtworkRequestProvider: ArtworkRequestProvider = unavailableArtworkRequestProvider,
    canManageEditorial: Boolean = false,
    onDeleteGlobalCard: (EditorialShelf) -> Unit = {},
    onDeleteGlobalCardArtwork: (EditorialShelf) -> Unit = {},
    onSaveGlobalCard: (EditorialGroupMutation, ByteArray?) -> Unit = { _, _ -> },
    @Suppress("UNUSED_PARAMETER") likedTrackIds: Set<String> = emptySet(),
    @Suppress("UNUSED_PARAMETER") savedAlbumIds: Set<String> = emptySet(),
    @Suppress("UNUSED_PARAMETER") followedArtistIds: Set<String> = emptySet(),
    @Suppress("UNUSED_PARAMETER") pendingTrackIds: Set<String> = emptySet(),
    @Suppress("UNUSED_PARAMETER") pendingAlbumIds: Set<String> = emptySet(),
    @Suppress("UNUSED_PARAMETER") pendingArtistIds: Set<String> = emptySet(),
    @Suppress("UNUSED_PARAMETER") onTrackLikeChange: (Track, Boolean) -> Unit = { _, _ -> },
    @Suppress("UNUSED_PARAMETER") onAlbumSaveChange: (Track, Boolean) -> Unit = { _, _ -> },
    @Suppress("UNUSED_PARAMETER") onArtistFollowChange: (Track, Boolean) -> Unit = { _, _ -> },
    @Suppress("UNUSED_PARAMETER") onPersonalizationEnabledChange: (Boolean) -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onDiscoveryModeChange: (DiscoveryMode) -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onRecommendationHidden: (Track) -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onTasteSignalExcluded: (Track, Boolean) -> Unit = { _, _ -> },
    @Suppress("UNUSED_PARAMETER") onClearPersonalizationData: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onPersonalizationMessageConsumed: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    var showAnalytics by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var notificationsViewed by remember { mutableStateOf(false) }
    val continueTracks = remember(state.listeningHistory, state.recommendations) {
        buildList {
            addAll(state.listeningHistory.sortedByDescending { it.lastPlayedAtEpochMillis }.map { it.track })
            addAll(state.recommendations.map { it.track })
        }.distinctBy(Track::id).take(MAX_CONTINUE_TRACKS)
    }
    val publishedShelves = remember(state.catalog.editorialShelves) {
        state.catalog.editorialShelves.filter { it.tracks.isNotEmpty() }
    }
    val topMixes = remember(publishedShelves) {
        publishedShelves.filter { it.position in TOP_MIX_POSITION_RANGE }
            .globalSmartOrder()
            .take(MAX_TOP_MIXES)
    }
    val recentGroups = remember(publishedShelves) {
        publishedShelves.filter { it.position in RECENT_GROUP_POSITION_RANGE }
            .globalSmartOrder()
            .take(MAX_RECENT_GROUPS)
    }
    val shows = remember(state.radioStations) {
        state.radioStations.filter { it.tracks.isNotEmpty() }.take(MAX_SHOWS)
    }
    val visibleSections = remember(
        continueTracks, topMixes, recentGroups, shows, personalizationSeed, state.tasteProfile.signalCount,
    ) {
        buildList {
            if (continueTracks.isNotEmpty()) add(HomeSection.Continue)
            if (topMixes.isNotEmpty()) add(HomeSection.TopMixes)
            if (recentGroups.isNotEmpty()) add(HomeSection.Recent)
            if (shows.isNotEmpty()) add(HomeSection.Shows)
        }.smartOrderFor(
            seed = personalizationSeed,
            history = state.listeningHistory,
            topMixes = topMixes,
            listeningGroups = recentGroups,
            shows = shows,
        )
    }

    if (showAnalytics) {
        ListeningAnalyticsSheet(state.listeningHistory) { showAnalytics = false }
    }
    if (showNotifications) {
        HomeNotificationsSheet(state.derivedSections.newReleaseTracks) {
            showNotifications = false
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val horizontalPadding = if (maxWidth < 360.dp) 16.dp else 20.dp
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().background(HomeBackground).testTag("home-list"),
            contentPadding = contentPadding,
        ) {
            item(key = "home-header") {
                ModernHomeHeader(
                    displayName = displayName,
                    avatarId = avatarId,
                    profileArtworkRequestProvider = profileArtworkRequestProvider,
                    artworkRevision = artworkRevision,
                    horizontalPadding = horizontalPadding,
                    hasNotification = !notificationsViewed && state.derivedSections.newReleaseTracks.isNotEmpty(),
                    onProfileClick = onProfileClick,
                    onAnalyticsClick = { showAnalytics = true },
                    onNotificationsClick = {
                        notificationsViewed = true
                        showNotifications = true
                    },
                    onSettingsClick = onSettingsClick,
                )
            }
            if (state.isRefreshing && !state.hasPlayableContent) {
                item(key = "home-loading") {
                    HomeStatus(
                        "Loading your Home feed",
                        "Syncing your private listening signals and Rakyzu catalog.",
                        true,
                        horizontalPadding,
                    )
                }
            } else if (state.isEmptyAfterRefresh) {
                item(key = "home-empty") {
                    HomeStatus(
                        "Your Home feed is empty",
                        "Published music will appear here as soon as it is available.",
                        false,
                        horizontalPadding,
                        onRetryCatalog,
                    )
                }
            } else {
                items(visibleSections, key = { "home-section-${it.name}" }) { section ->
                    when (section) {
                        HomeSection.Continue -> CompactSongSection(
                            if (state.listeningHistory.isEmpty()) "Recomendation For You" else "Continue Listening",
                            continueTracks,
                            artworkRequestProvider,
                            horizontalPadding,
                            onTrackPlay,
                        )
                        HomeSection.TopMixes -> GlobalGroupSection(
                            title = "Top Mixes",
                            shelves = topMixes,
                            cardSize = 150.dp,
                            horizontalPadding = horizontalPadding,
                            imageOnly = false,
                            cardStyle = HomeEditorialCardStyle.TopMix,
                            artworkRevision = artworkRevision,
                            artworkRequestProvider = artworkRequestProvider,
                            recommendationArtworkRequestProvider = recommendationArtworkRequestProvider,
                            canManageEditorial = canManageEditorial,
                            catalogTracks = state.catalog.tracks,
                            placement = EditorialPlacement.HomeTopMix,
                            onPlay = {
                                onEditorialGroupOpen(it.id)
                                onTrackPlay(it.tracks.take(MAX_GROUP_TRACKS), 0)
                            },
                            onDelete = onDeleteGlobalCard,
                            onDeleteArtwork = onDeleteGlobalCardArtwork,
                            onSave = onSaveGlobalCard,
                        )
                        HomeSection.Recent -> GlobalGroupSection(
                            title = "Based on your recent listening",
                            shelves = recentGroups,
                            cardSize = 182.dp,
                            horizontalPadding = horizontalPadding,
                            imageOnly = true,
                            cardStyle = HomeEditorialCardStyle.RecentListening,
                            artworkRevision = artworkRevision,
                            artworkRequestProvider = artworkRequestProvider,
                            recommendationArtworkRequestProvider = recommendationArtworkRequestProvider,
                            canManageEditorial = canManageEditorial,
                            catalogTracks = state.catalog.tracks,
                            placement = EditorialPlacement.HomeListening,
                            onPlay = {
                                onEditorialGroupOpen(it.id)
                                onTrackPlay(it.tracks.take(MAX_GROUP_TRACKS), 0)
                            },
                            onDelete = onDeleteGlobalCard,
                            onDeleteArtwork = onDeleteGlobalCardArtwork,
                            onSave = onSaveGlobalCard,
                        )
                        HomeSection.Shows -> ShowsSection(
                            shows, horizontalPadding, artworkRequestProvider, onTrackPlay,
                        )
                    }
                }
            }
            if (state.refreshMessage != null && state.hasPlayableContent) {
                item(key = "home-refresh-message") {
                    HomeStatus(
                        if (state.isWaitingForConnection) "Waiting for connection" else "Saved catalog",
                        state.refreshMessage,
                        state.isRefreshing,
                        horizontalPadding,
                        onRetryCatalog,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernHomeHeader(
    displayName: String,
    avatarId: String?,
    profileArtworkRequestProvider: ArtworkRequestProvider,
    artworkRevision: Long,
    horizontalPadding: Dp,
    hasNotification: Boolean,
    onProfileClick: () -> Unit,
    onAnalyticsClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().background(
            Brush.verticalGradient(listOf(Color(0xFF0E5660).copy(alpha = 0.56f), Color.Transparent)),
        ).statusBarsPadding().padding(horizontal = horizontalPadding).padding(top = 12.dp, bottom = 24.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onProfileClick,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "Profile" },
            ) {
                if (avatarId != null) {
                    AlbumArtwork(
                        albumId = avatarId,
                        colors = listOf(Color(0xFF155C65), HomeAqua),
                        artworkRequestProvider = profileArtworkRequestProvider,
                        artworkRevision = artworkRevision,
                        shape = CircleShape,
                        modifier = Modifier.size(34.dp),
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(34.dp),
                        shape = CircleShape,
                        color = Color(0xFF254A50),
                        border = androidx.compose.foundation.BorderStroke(2.5.dp, HomeAqua),
                    ) {
                        Icon(
                            Icons.Rounded.Person,
                            contentDescription = "Profile",
                            tint = Color.White,
                            modifier = Modifier.padding(6.dp),
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Welcome back !",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    displayName,
                    color = Color.White.copy(alpha = 0.58f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HeaderAction(Icons.Rounded.Analytics, "Listening analytics", onAnalyticsClick)
            Box {
                HeaderAction(Icons.Rounded.NotificationsNone, "Notifications", onNotificationsClick)
                if (hasNotification) {
                    Box(
                        modifier = Modifier.align(Alignment.TopEnd).size(7.dp).clip(CircleShape).background(HomeAqua),
                    )
                }
            }
            HeaderAction(Icons.Rounded.Settings, "Settings", onSettingsClick)
        }
    }
}

@Composable
private fun HeaderAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun CompactSongSection(
    title: String,
    tracks: List<Track>,
    artworkRequestProvider: ArtworkRequestProvider,
    horizontalPadding: Dp,
    onTrackPlay: (List<Track>, Int) -> Unit,
) {
    HomeSectionTitle(title, horizontalPadding)
    val columns = remember(tracks) { tracks.chunked(3) }
    LazyRow(
        state = rememberLazyListState(),
        modifier = Modifier.padding(bottom = 24.dp).semantics { isTraversalGroup = true }
            .testTag("track-shelf-list-$title"),
        contentPadding = PaddingValues(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(columns, key = { index, chunk -> "compact-$index-${chunk.first().id}" }) { column, chunk ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                chunk.forEachIndexed { row, track ->
                    val trackIndex = column * 3 + row
                    CompactSongCard(
                        track,
                        trackIndex,
                        artworkRequestProvider,
                    ) { onTrackPlay(tracks, trackIndex) }
                }
            }
        }
    }
}

@Composable
private fun CompactSongCard(
    track: Track,
    index: Int,
    artworkRequestProvider: ArtworkRequestProvider,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.width(182.dp).height(55.dp).clip(RoundedCornerShape(10.dp))
            .background(CompactCard)
            .clickable(role = Role.Button, onClickLabel = track.homePlayActionLabel(), onClick = onClick)
            .semantics { traversalIndex = index.toFloat() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArtwork(
            albumId = track.albumId,
            colors = artworkGradients[index % artworkGradients.size],
            artworkRequestProvider = artworkRequestProvider,
            shape = RoundedCornerShape(3.dp),
            modifier = Modifier.size(54.dp),
        )
        Text(
            track.title,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.35.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GlobalGroupSection(
    title: String,
    shelves: List<EditorialShelf>,
    cardSize: Dp,
    horizontalPadding: Dp,
    imageOnly: Boolean,
    cardStyle: HomeEditorialCardStyle,
    artworkRevision: Long,
    artworkRequestProvider: ArtworkRequestProvider,
    recommendationArtworkRequestProvider: ArtworkRequestProvider,
    canManageEditorial: Boolean,
    catalogTracks: List<Track>,
    placement: EditorialPlacement,
    onPlay: (EditorialShelf) -> Unit,
    onDelete: (EditorialShelf) -> Unit,
    onDeleteArtwork: (EditorialShelf) -> Unit,
    onSave: (EditorialGroupMutation, ByteArray?) -> Unit,
) {
    var editing by remember { mutableStateOf<EditorialShelf?>(null) }
    var adding by remember { mutableStateOf(false) }
    HomeSectionTitle(title, horizontalPadding)
    LazyRow(
        state = rememberLazyListState(),
        modifier = Modifier.padding(bottom = 26.dp).semantics { isTraversalGroup = true },
        contentPadding = PaddingValues(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(shelves, key = EditorialShelf::id) { shelf ->
            GlobalGroupCard(
                shelf = shelf,
                size = cardSize,
                imageOnly = imageOnly,
                cardStyle = cardStyle,
                artworkRevision = artworkRevision,
                artworkRequestProvider = artworkRequestProvider,
                recommendationArtworkRequestProvider = recommendationArtworkRequestProvider,
                modifier = Modifier.combinedClickable(
                    role = Role.Button,
                    onClickLabel = "Play ${shelf.title}",
                    onLongClickLabel = if (canManageEditorial) "Manage ${shelf.title}" else null,
                    onClick = { onPlay(shelf) },
                    onLongClick = { if (canManageEditorial) editing = shelf },
                ),
            )
        }
        if (canManageEditorial && shelves.size < placement.maximumCards) {
            item(key = "add-${placement.name}") {
                AddHomeGroupCard(size = cardSize, onClick = { adding = true })
            }
        }
    }
    editing?.let { shelf ->
        HomeEditorialGroupEditor(
            existing = shelf,
            placement = placement,
            catalogTracks = catalogTracks,
            onDismiss = { editing = null },
            onSave = { mutation, bytes -> editing = null; onSave(mutation, bytes) },
            onDelete = { editing = null; onDelete(shelf) },
            onDeleteArtwork = if (shelf.hasCustomArtwork) {
                { editing = null; onDeleteArtwork(shelf) }
            } else null,
        )
    }
    if (adding) {
        HomeEditorialGroupEditor(
            existing = null,
            placement = placement,
            catalogTracks = catalogTracks,
            initialDisplayPosition = shelves.size + 1,
            onDismiss = { adding = false },
            onSave = { mutation, bytes -> adding = false; onSave(mutation, bytes) },
        )
    }
}

@Composable
private fun AddHomeGroupCard(size: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(10.dp)).drawBehind {
            drawRoundRect(
                color = HomeAqua.copy(alpha = 0.82f),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
            )
        }.clickable(role = Role.Button, onClickLabel = "Add Card Group", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = HomeAqua, modifier = Modifier.size(36.dp))
    }
}

@Composable
private fun HomeEditorialGroupEditor(
    existing: EditorialShelf?,
    placement: EditorialPlacement,
    catalogTracks: List<Track>,
    initialDisplayPosition: Int = 1,
    onDismiss: () -> Unit,
    onSave: (EditorialGroupMutation, ByteArray?) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDeleteArtwork: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var cardLabel by remember(existing?.id) { mutableStateOf(existing?.cardLabel.orEmpty()) }
    var subtitle by remember(existing?.id) { mutableStateOf(existing?.subtitle.orEmpty()) }
    var colorHex by remember(existing?.id) { mutableStateOf(existing?.colorHex ?: "#4A558F") }
    var position by remember(existing?.id) {
        mutableStateOf(
            existing?.let { placement.displayPosition(it.position).toString() }
                ?: initialDisplayPosition.toString(),
        )
    }
    var published by remember(existing?.id) { mutableStateOf(true) }
    var selectedTrackIds by remember(existing?.id) {
        mutableStateOf(existing?.tracks?.map(Track::id)?.toSet().orEmpty())
    }
    var artworkBytes by remember(existing?.id) { mutableStateOf<ByteArray?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var pickAfterPermission by remember { mutableStateOf(false) }
    val artworkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) { readHomeArtwork(context, uri) }
            if (result != null) {
                artworkBytes = result.first
                colorHex = result.second
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pickAfterPermission) {
            artworkPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
        }
        pickAfterPermission = false
    }
    fun pickArtwork() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            artworkPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
        } else {
            pickAfterPermission = true
            permissionLauncher.launch(permission)
        }
    }
    if (showDeleteConfirmation && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete ${existing?.title}?") },
            text = { Text("This removes the global Card Group for every listener. Songs stay in the catalog.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete") } },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            },
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add Card Group" else "Edit Card Group") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 570.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(80) },
                    label = { Text("Card Group name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (placement == EditorialPlacement.HomeTopMix) {
                    OutlinedTextField(
                        value = cardLabel,
                        onValueChange = { cardLabel = it.take(40) },
                        label = { Text("Header in front of image") },
                        supportingText = { Text("Example: Pop Mix. The group name remains synchronized inside.") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it.take(160) },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = colorHex,
                    onValueChange = { colorHex = it.take(7).uppercase() },
                    label = { Text("Color #RRGGBB") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MixAccents.forEach { color ->
                        val hex = color.toHomeHex()
                        Surface(
                            color = color,
                            shape = CircleShape,
                            onClick = { colorHex = hex },
                            modifier = Modifier.size(42.dp),
                        ) {
                            if (colorHex.equals(hex, true)) {
                                Icon(Icons.Rounded.Check, null, tint = Color.Black, modifier = Modifier.padding(10.dp))
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = position,
                    onValueChange = { position = it.filter(Char::isDigit).take(2) },
                    label = { Text("Position (1-${placement.maximumCards})") },
                    supportingText = { Text("Cards with higher global engagement are promoted automatically.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Visible globally", modifier = Modifier.weight(1f))
                    Switch(checked = published, onCheckedChange = { published = it })
                }
                Text("Songs (${selectedTrackIds.size}/50)", fontWeight = FontWeight.Bold)
                if (catalogTracks.isEmpty()) {
                    Text("Upload and publish songs before creating a Card Group.")
                }
                catalogTracks.take(80).forEach { track ->
                    FilterChip(
                        selected = track.id in selectedTrackIds,
                        onClick = {
                            selectedTrackIds = if (track.id in selectedTrackIds) {
                                selectedTrackIds - track.id
                            } else if (selectedTrackIds.size < MAX_GROUP_TRACKS) {
                                selectedTrackIds + track.id
                            } else {
                                selectedTrackIds
                            }
                        },
                        label = { Text("${track.title} · ${track.artist}", maxLines = 1) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Button(onClick = ::pickArtwork, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        if (existing?.hasCustomArtwork == true || artworkBytes != null) {
                            Icons.Rounded.Edit
                        } else {
                            Icons.Rounded.Image
                        },
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (existing?.hasCustomArtwork == true || artworkBytes != null) {
                            "Change image"
                        } else {
                            "Add image"
                        },
                    )
                }
                if (artworkBytes != null) {
                    TextButton(onClick = { artworkBytes = null }, modifier = Modifier.fillMaxWidth()) {
                        Text("Remove selected image")
                    }
                }
                onDeleteArtwork?.let { callback ->
                    TextButton(onClick = callback, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Delete, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete current image")
                    }
                }
                onDelete?.let {
                    TextButton(
                        onClick = { showDeleteConfirmation = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Delete, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete Card Group")
                    }
                }
            }
        },
        confirmButton = {
            val displayPosition = position.toIntOrNull()
            TextButton(
                enabled = title.isNotBlank() &&
                    selectedTrackIds.isNotEmpty() &&
                    displayPosition != null && displayPosition in 1..placement.maximumCards &&
                    HOME_COLOR_HEX.matches(colorHex) &&
                    (placement != EditorialPlacement.HomeTopMix || cardLabel.isNotBlank()),
                onClick = {
                    onSave(
                        EditorialGroupMutation(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            title = title.trim(),
                            subtitle = subtitle.trim().ifBlank { null },
                            placement = placement,
                            displayPosition = requireNotNull(displayPosition),
                            cardLabel = cardLabel.trim().takeIf {
                                it.isNotBlank() && placement == EditorialPlacement.HomeTopMix
                            },
                            colorHex = colorHex.uppercase(),
                            published = published,
                            trackIds = selectedTrackIds.toList(),
                        ),
                        artworkBytes,
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private enum class HomeEditorialCardStyle { TopMix, RecentListening }

@Composable
private fun GlobalGroupCard(
    shelf: EditorialShelf,
    size: Dp,
    imageOnly: Boolean,
    cardStyle: HomeEditorialCardStyle,
    artworkRevision: Long,
    artworkRequestProvider: ArtworkRequestProvider,
    recommendationArtworkRequestProvider: ArtworkRequestProvider,
    modifier: Modifier = Modifier,
) {
    val accent = shelf.colorHex.toHomeColor()
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(if (cardStyle == HomeEditorialCardStyle.TopMix) 2.dp else 4.dp))
            .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.55f)))),
    ) {
        val firstTrack = shelf.tracks.first()
        AlbumArtwork(
            albumId = if (shelf.hasCustomArtwork) shelf.id else firstTrack.albumId,
            colors = listOf(accent, accent.copy(alpha = 0.55f)),
            artworkRequestProvider = if (shelf.hasCustomArtwork) {
                recommendationArtworkRequestProvider
            } else {
                artworkRequestProvider
            },
            artworkRevision = artworkRevision,
            shape = RoundedCornerShape(if (cardStyle == HomeEditorialCardStyle.TopMix) 2.dp else 4.dp),
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = if (cardStyle == HomeEditorialCardStyle.TopMix) 0.22f else 0.04f),
                        Color.Black.copy(alpha = if (imageOnly) 0.74f else 0.44f),
                    ),
                ),
            ),
        )
        if (cardStyle == HomeEditorialCardStyle.TopMix) {
            // The Home Top Mix treatment is still the same editorial Card Group data,
            // but its presentation is the blueprint's direct-play cover: title, circles,
            // and genre accent stripe. It must not look like the Explore landing card.
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-18).dp, y = (-18).dp)
                    .size(44.dp)
                    .background(Color.White.copy(alpha = 0.78f), CircleShape),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 23.dp)
                    .size(72.dp)
                    .background(Color.White.copy(alpha = 0.82f), CircleShape),
            )
            Text(
                shelf.cardLabel ?: shelf.title,
                color = Color.White,
                fontSize = 15.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            )
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(6.dp)
                    .background(accent),
            )
        } else if (!imageOnly) {
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(6.dp)
                    .background(accent),
            )
        }
    }
}

@Composable
private fun ShowsSection(
    shows: List<PersonalizedCollection>,
    horizontalPadding: Dp,
    artworkRequestProvider: ArtworkRequestProvider,
    onTrackPlay: (List<Track>, Int) -> Unit,
) {
    HomeSectionTitle("Your Shows", horizontalPadding)
    LazyRow(
        state = rememberLazyListState(),
        modifier = Modifier.padding(bottom = 26.dp).semantics { isTraversalGroup = true },
        contentPadding = PaddingValues(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(shows, key = { _, show -> show.id }) { index, show ->
            Box(
                modifier = Modifier.size(182.dp).clip(RoundedCornerShape(10.dp)).clickable(
                    role = Role.Button,
                    onClickLabel = "Play ${show.title}",
                    onClick = { onTrackPlay(show.tracks, 0) },
                ),
            ) {
                AlbumArtwork(
                    albumId = show.tracks.first().albumId,
                    colors = artworkGradients[(index + 3) % artworkGradients.size],
                    artworkRequestProvider = artworkRequestProvider,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.76f))),
                    ),
                )
                Column(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                    Text(
                        show.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        show.subtitle,
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeSectionTitle(title: String, horizontalPadding: Dp) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding)
            .padding(top = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = Color.White,
            fontSize = if (title == "Your Shows") 22.sp else 20.sp,
            lineHeight = if (title == "Your Shows") 26.sp else 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        if (title == "Top Mixes") {
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.34f)))
        }
    }
}

@Composable
private fun HomeStatus(
    title: String,
    detail: String?,
    loading: Boolean,
    horizontalPadding: Dp,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 28.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (loading) CircularProgressIndicator(color = HomeAqua, modifier = Modifier.size(26.dp))
        Text(
            title,
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        detail?.let { Text(it, color = Color.White.copy(alpha = 0.62f), textAlign = TextAlign.Center) }
        onRetry?.let {
            Button(onClick = it, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Refresh")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListeningAnalyticsSheet(
    history: List<ListeningHistoryItem>,
    onDismiss: () -> Unit,
) {
    var selected by remember(history) { mutableStateOf(history.maxByOrNull { it.playCount }) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF172124)) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 22.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.ShowChart, contentDescription = null, tint = HomeAqua)
                Spacer(Modifier.width(10.dp))
                Text("Your listening analytics", color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
            if (history.isEmpty()) {
                Text(
                    "Your private graph will appear after you listen to music.",
                    color = Color.White.copy(alpha = 0.68f),
                )
            } else {
                val chartItems = history.sortedByDescending { it.playCount }.take(7)
                Text(
                    "${history.sumOf { it.playCount }} plays · ${history.size} unique tracks",
                    color = Color.White.copy(alpha = 0.68f),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    val maxPlayCount = chartItems.maxOf { it.playCount }.coerceAtLeast(1)
                    chartItems.forEach { item ->
                        val selectedBar = selected?.track?.id == item.track.id
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight().clickable { selected = item },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .height((132f * item.playCount / maxPlayCount).dp.coerceAtLeast(12.dp))
                                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                    .background(if (selectedBar) HomeAqua else HomeAqua.copy(alpha = 0.38f)),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                item.track.title.take(3).uppercase(),
                                color = Color.White.copy(alpha = 0.62f),
                                fontSize = 9.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
                selected?.let {
                    Surface(color = Color.White.copy(alpha = 0.06f), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(it.track.title, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(
                                "${it.track.artist} · ${it.playCount} ${if (it.playCount == 1) "play" else "plays"}",
                                color = Color.White.copy(alpha = 0.66f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeNotificationsSheet(
    releases: List<Track>,
    onDismiss: () -> Unit,
) {
    val notificationItems = remember(releases) {
        buildList {
            releases.take(4).forEach { add("New song · ${it.title}" to it.artist) }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF172124)) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("Notifications", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text(
                    "Events, app updates, song releases, and radio releases.",
                    color = Color.White.copy(alpha = 0.62f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            }
            if (notificationItems.isEmpty()) {
                item {
                    Text(
                        "No new notifications.",
                        color = Color.White.copy(alpha = 0.64f),
                        modifier = Modifier.padding(vertical = 18.dp),
                    )
                }
            }
            items(notificationItems, key = { it.first + it.second }) { notification ->
                Surface(color = Color.White.copy(alpha = 0.06f), shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.NotificationsNone, contentDescription = null, tint = HomeAqua)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(notification.first, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(notification.second, color = Color.White.copy(alpha = 0.64f), fontSize = 12.sp)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
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
    @Suppress("UNUSED_PARAMETER") featuredArtworkId: String? = null,
    @Suppress("UNUSED_PARAMETER") featuredArtworkRequestProvider: ArtworkRequestProvider = unavailableArtworkRequestProvider,
    onTrackPlay: (List<Track>, Int) -> Unit,
    @Suppress("UNUSED_PARAMETER") likedTrackIds: Set<String> = emptySet(),
    @Suppress("UNUSED_PARAMETER") savedAlbumIds: Set<String> = emptySet(),
    @Suppress("UNUSED_PARAMETER") followedArtistIds: Set<String> = emptySet(),
    @Suppress("UNUSED_PARAMETER") pendingTrackIds: Set<String> = emptySet(),
    @Suppress("UNUSED_PARAMETER") pendingAlbumIds: Set<String> = emptySet(),
    @Suppress("UNUSED_PARAMETER") pendingArtistIds: Set<String> = emptySet(),
    recommendationReasons: Map<String, String> = emptyMap(),
    @Suppress("UNUSED_PARAMETER") excludedTasteTrackIds: Set<String> = emptySet(),
    @Suppress("UNUSED_PARAMETER") allowRecommendationHide: Boolean = false,
    @Suppress("UNUSED_PARAMETER") allowTasteExclusion: Boolean = false,
    @Suppress("UNUSED_PARAMETER") onTrackLikeChange: (Track, Boolean) -> Unit = { _, _ -> },
    @Suppress("UNUSED_PARAMETER") onAlbumSaveChange: (Track, Boolean) -> Unit = { _, _ -> },
    @Suppress("UNUSED_PARAMETER") onArtistFollowChange: (Track, Boolean) -> Unit = { _, _ -> },
    @Suppress("UNUSED_PARAMETER") onRecommendationHidden: (Track) -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onTasteSignalExcluded: (Track, Boolean) -> Unit = { _, _ -> },
) {
    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        HomeSectionTitle(title, layoutSpec.horizontalPadding)
        subtitle?.takeIf(String::isNotBlank)?.let {
            Text(
                it,
                color = Color.White.copy(alpha = 0.62f),
                modifier = Modifier.padding(horizontal = layoutSpec.horizontalPadding).padding(bottom = 10.dp),
            )
        }
        LazyRow(
            state = rememberLazyListState(),
            modifier = Modifier.testTag("track-shelf-list-$title"),
            contentPadding = PaddingValues(horizontal = layoutSpec.horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                Column(
                    modifier = Modifier.width(layoutSpec.trackCardWidth).heightIn(min = 48.dp).clickable(
                        role = Role.Button,
                        onClickLabel = track.homePlayActionLabel(),
                        onClick = { onTrackPlay(tracks, index) },
                    ).semantics { traversalIndex = index.toFloat() },
                ) {
                    AlbumArtwork(
                        albumId = track.albumId,
                        colors = artworkGradients[index % artworkGradients.size],
                        artworkRequestProvider = artworkRequestProvider,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(layoutSpec.trackCardWidth),
                    )
                    Text(
                        track.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = layoutSpec.trackTextMaxLines,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        recommendationReasons[track.id] ?: track.homeSubtitle(),
                        color = Color.White.copy(alpha = 0.58f),
                        maxLines = layoutSpec.trackTextMaxLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

internal fun Track.homePlayActionLabel(): String = "Play ${title.trim()}"

internal fun Track.homeSubtitle(): String = when {
    artist.isNotBlank() && albumTitle.isNotBlank() -> "$artist · $albumTitle"
    artist.isNotBlank() -> artist
    albumTitle.isNotBlank() -> albumTitle
    else -> "Rakyzu Music"
}

private fun List<EditorialShelf>.globalSmartOrder(): List<EditorialShelf> =
    sortedWith(compareByDescending<EditorialShelf> { it.globalScore }.thenBy { it.position })

private fun List<HomeSection>.smartOrderFor(
    seed: String,
    history: List<ListeningHistoryItem>,
    topMixes: List<EditorialShelf>,
    listeningGroups: List<EditorialShelf>,
    shows: List<PersonalizedCollection>,
): List<HomeSection> {
    if (history.isEmpty()) return this
    val plays = history.associate { it.track.id to it.playCount }
    fun shelfSignal(shelves: List<EditorialShelf>): Int = shelves.maxOfOrNull { shelf ->
        shelf.tracks.sumOf { plays[it.id] ?: 0 }
    } ?: 0
    fun showSignal(): Int = shows.maxOfOrNull { show ->
        show.tracks.sumOf { plays[it.id] ?: 0 }
    } ?: 0
    val scores = mapOf(
        HomeSection.Continue to history.sumOf(ListeningHistoryItem::playCount),
        HomeSection.TopMixes to shelfSignal(topMixes),
        HomeSection.Recent to shelfSignal(listeningGroups),
        HomeSection.Shows to showSignal(),
    )
    return sortedWith(
        compareByDescending<HomeSection> { scores[it] ?: 0 }
            .thenBy { stablePersonalizedScore(seed, it.name, history.size) },
    )
}

private fun String.toHomeColor(): Color = runCatching {
    Color(android.graphics.Color.parseColor(this))
}.getOrDefault(Color(0xFF4A558F))

private fun Color.toHomeHex(): String = String.format(
    "#%02X%02X%02X",
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)

private fun Bitmap.homeDominantColorHex(): String {
    val stepX = (width / 32).coerceAtLeast(1)
    val stepY = (height / 32).coerceAtLeast(1)
    val buckets = linkedMapOf<Int, Int>()
    for (y in 0 until height step stepY) {
        for (x in 0 until width step stepX) {
            val pixel = getPixel(x, y)
            if (android.graphics.Color.alpha(pixel) < 160) continue
            val red = android.graphics.Color.red(pixel)
            val green = android.graphics.Color.green(pixel)
            val blue = android.graphics.Color.blue(pixel)
            if (red + green + blue < 42 || red + green + blue > 720) continue
            val key = ((red / 32) shl 10) or ((green / 32) shl 5) or (blue / 32)
            buckets[key] = (buckets[key] ?: 0) + 1
        }
    }
    val key = buckets.maxByOrNull { it.value }?.key ?: return "#4A558F"
    val red = (((key shr 10) and 31) * 32 + 16).coerceAtMost(255)
    val green = (((key shr 5) and 31) * 32 + 16).coerceAtMost(255)
    val blue = ((key and 31) * 32 + 16).coerceAtMost(255)
    return String.format("#%02X%02X%02X", red, green, blue)
}

private fun stablePersonalizedScore(seed: String, value: String, signal: Int): Int =
    "$seed:$value:$signal".hashCode().absoluteValue

private fun readHomeArtwork(context: android.content.Context, uri: Uri): Pair<ByteArray, String>? {
    val source = context.contentResolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (output.size() + read > MAX_ARTWORK_SOURCE_BYTES) return@use null
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    } ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
    if (bounds.outWidth !in 1..12_000 || bounds.outHeight !in 1..12_000) return null
    var sample = 1
    while (bounds.outWidth / sample > 2_048 || bounds.outHeight / sample > 2_048) sample *= 2
    val bitmap = BitmapFactory.decodeByteArray(
        source, 0, source.size, BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return null
    return try {
        val colorHex = bitmap.homeDominantColorHex()
        val output = ByteArrayOutputStream()
        if (!bitmap.compress(webpFormat(), 82, output)) null else
            output.toByteArray().takeIf { it.size in 12..MAX_ARTWORK_BYTES }?.let { it to colorHex }
    } finally {
        bitmap.recycle()
    }
}

@Suppress("DEPRECATION")
private fun webpFormat(): Bitmap.CompressFormat =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY
    else Bitmap.CompressFormat.WEBP

private const val MAX_CONTINUE_TRACKS = 18
private const val MAX_TOP_MIXES = 7
private const val MAX_RECENT_GROUPS = 7
private const val MAX_SHOWS = 7
private val TOP_MIX_POSITION_RANGE = 0..6
private val RECENT_GROUP_POSITION_RANGE = 100..106
private const val MAX_GROUP_TRACKS = 50
private const val MAX_ARTWORK_BYTES = 5 * 1024 * 1024
private const val MAX_ARTWORK_SOURCE_BYTES = 15 * 1024 * 1024
private val HOME_COLOR_HEX = Regex("^#[0-9A-Fa-f]{6}$")

@Preview(showBackground = true, widthDp = 422, heightDp = 922)
@Composable
private fun HomeScreenPreview() {
    RakyzuMusicTheme(darkTheme = true) {
        HomeScreen(versionName = "0.8.8.3")
    }
}
