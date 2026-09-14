package my.id.rakyzumusic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import my.id.rakyzumusic.core.data.auth.AuthActionResult
import my.id.rakyzumusic.core.data.auth.AuthFailure
import my.id.rakyzumusic.core.data.auth.AuthRepository
import my.id.rakyzumusic.core.data.auth.AuthSessionState
import my.id.rakyzumusic.core.data.catalog.CatalogRepository
import my.id.rakyzumusic.core.data.library.LibraryRepository
import my.id.rakyzumusic.core.data.media.MediaDeliveryRepository
import my.id.rakyzumusic.core.data.network.ConnectivityMonitor
import my.id.rakyzumusic.core.data.playlist.PlaylistRepository
import my.id.rakyzumusic.core.data.profile.ProfileRepository
import my.id.rakyzumusic.core.data.search.RecentSearchRepository
import my.id.rakyzumusic.core.designsystem.theme.RakyzuAqua
import my.id.rakyzumusic.core.designsystem.theme.RakyzuBlack
import my.id.rakyzumusic.core.designsystem.theme.RakyzuPurple
import my.id.rakyzumusic.core.designsystem.theme.RakyzuSurface
import my.id.rakyzumusic.core.model.LibraryItemKind
import my.id.rakyzumusic.core.playback.RakyzuPlaybackController
import my.id.rakyzumusic.core.playback.PlaybackSnapshot
import my.id.rakyzumusic.core.playback.PlaybackStatus
import my.id.rakyzumusic.core.playback.PlaybackPreferences
import my.id.rakyzumusic.core.playback.PlaybackQuality
import my.id.rakyzumusic.feature.auth.AuthRoute
import my.id.rakyzumusic.feature.home.HomeRoute
import my.id.rakyzumusic.feature.library.LibraryRoute
import my.id.rakyzumusic.feature.library.LibraryViewModel
import my.id.rakyzumusic.feature.player.NowPlayingScreen
import my.id.rakyzumusic.feature.playlist.PlaylistRoute
import my.id.rakyzumusic.feature.playlist.PlaylistDetailRoute
import my.id.rakyzumusic.feature.playlist.PlaylistDetailViewModel
import my.id.rakyzumusic.feature.playlist.PlaylistViewModel
import my.id.rakyzumusic.feature.profile.OnboardingScreen
import my.id.rakyzumusic.feature.profile.ProfileLoadingScreen
import my.id.rakyzumusic.feature.profile.ProfileUnavailableScreen
import my.id.rakyzumusic.feature.profile.ProfileViewModel
import my.id.rakyzumusic.feature.search.AlbumDetailRoute
import my.id.rakyzumusic.feature.search.AlbumDetailViewModel
import my.id.rakyzumusic.feature.search.ArtistDetailRoute
import my.id.rakyzumusic.feature.search.ArtistDetailViewModel
import my.id.rakyzumusic.feature.search.SearchRoute
import my.id.rakyzumusic.feature.search.SearchViewModel
import my.id.rakyzumusic.navigation.RakyzuRoute
import my.id.rakyzumusic.navigation.dismissAlbumDetail
import my.id.rakyzumusic.navigation.dismissArtistDetail
import my.id.rakyzumusic.navigation.dismissNowPlaying
import my.id.rakyzumusic.navigation.openAlbumDetail
import my.id.rakyzumusic.navigation.openArtistDetail
import my.id.rakyzumusic.navigation.openNowPlaying
import my.id.rakyzumusic.navigation.selectTopLevelRoute
import kotlinx.coroutines.launch

private data class TopLevelDestination(
    val route: RakyzuRoute,
    val label: String,
    val icon: ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(RakyzuRoute.Home, "Home", Icons.Rounded.Home),
    TopLevelDestination(RakyzuRoute.Search, "Search", Icons.Rounded.Search),
    TopLevelDestination(RakyzuRoute.Library, "Your Library", Icons.Rounded.LibraryMusic),
    TopLevelDestination(RakyzuRoute.Create, "Create", Icons.Rounded.AddCircle),
)

@Composable
fun RakyzuMusicApp(
    versionName: String,
    authRepository: AuthRepository,
    profileRepository: ProfileRepository,
    catalogRepository: CatalogRepository,
    libraryRepository: LibraryRepository,
    playlistRepository: PlaylistRepository,
    mediaDeliveryRepository: MediaDeliveryRepository,
    connectivityMonitor: ConnectivityMonitor,
    recentSearchRepository: RecentSearchRepository,
    playbackController: RakyzuPlaybackController,
    playbackPreferences: AndroidPlaybackPreferences,
    modifier: Modifier = Modifier,
) {
    val sessionState by authRepository.sessionState.collectAsStateWithLifecycle()

    LaunchedEffect(sessionState) {
        if (sessionState !is AuthSessionState.SignedIn) {
            playbackController.stopAndClear()
        }
    }

    when (val state = sessionState) {
        AuthSessionState.Initializing -> SessionLoadingScreen(modifier)
        AuthSessionState.SignedOut -> AuthRoute(
            repository = authRepository,
            modifier = modifier,
        )
        is AuthSessionState.RecoveryRequired -> AuthRoute(
            repository = authRepository,
            sessionMessage = state.failure.toSessionMessage(),
            modifier = modifier,
        )
        is AuthSessionState.PasswordRecovery -> AuthRoute(
            repository = authRepository,
            passwordRecoveryRequired = true,
            modifier = modifier,
        )
        is AuthSessionState.SignedIn -> ProfileGatedRakyzuMusicApp(
            versionName = versionName,
            userId = state.userId,
            email = state.email,
            authRepository = authRepository,
            profileRepository = profileRepository,
            catalogRepository = catalogRepository,
            libraryRepository = libraryRepository,
            playlistRepository = playlistRepository,
            mediaDeliveryRepository = mediaDeliveryRepository,
            connectivityMonitor = connectivityMonitor,
            recentSearchRepository = recentSearchRepository,
            playbackController = playbackController,
            playbackPreferences = playbackPreferences,
            modifier = modifier,
        )
    }
}

@Composable
private fun ProfileGatedRakyzuMusicApp(
    versionName: String,
    userId: String,
    email: String?,
    authRepository: AuthRepository,
    profileRepository: ProfileRepository,
    catalogRepository: CatalogRepository,
    libraryRepository: LibraryRepository,
    playlistRepository: PlaylistRepository,
    mediaDeliveryRepository: MediaDeliveryRepository,
    connectivityMonitor: ConnectivityMonitor,
    recentSearchRepository: RecentSearchRepository,
    playbackController: RakyzuPlaybackController,
    playbackPreferences: AndroidPlaybackPreferences,
    modifier: Modifier = Modifier,
) {
    val profileViewModel: ProfileViewModel = viewModel(
        key = "profile-$userId",
        factory = ProfileViewModel.factory(profileRepository),
    )
    val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()
    val profile = profileState.profile

    when {
        profile == null && profileState.isLoading -> ProfileLoadingScreen(modifier)
        profile == null -> ProfileUnavailableScreen(
            message = profileState.message ?: "Your Rakyzu Music profile is temporarily unavailable.",
            onRetry = profileViewModel::loadProfile,
            modifier = modifier,
        )
        !profile.onboardingCompleted -> OnboardingScreen(
            state = profileState,
            onDisplayNameChanged = profileViewModel::updateDisplayName,
            onContinue = { profileViewModel.saveProfile(completeOnboarding = true) },
            modifier = modifier,
        )
        else -> AuthenticatedRakyzuMusicApp(
            versionName = versionName,
            userId = userId,
            email = email,
            displayName = profile.displayName,
            profileDisplayNameDraft = profileState.displayName,
            isSavingProfile = profileState.isSaving,
            profileMessage = profileState.message,
            profileMessageIsError = profileState.messageIsError,
            onProfileDisplayNameChanged = profileViewModel::updateDisplayName,
            onSaveProfile = { profileViewModel.saveProfile(completeOnboarding = false) },
            onResetProfileDraft = profileViewModel::resetDraft,
            authRepository = authRepository,
            catalogRepository = catalogRepository,
            libraryRepository = libraryRepository,
            playlistRepository = playlistRepository,
            mediaDeliveryRepository = mediaDeliveryRepository,
            connectivityMonitor = connectivityMonitor,
            recentSearchRepository = recentSearchRepository,
            playbackController = playbackController,
            playbackPreferences = playbackPreferences,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3AdaptiveNavigationSuiteApi::class)
@Composable
private fun AuthenticatedRakyzuMusicApp(
    versionName: String,
    userId: String,
    email: String?,
    displayName: String,
    profileDisplayNameDraft: String,
    isSavingProfile: Boolean,
    profileMessage: String?,
    profileMessageIsError: Boolean,
    onProfileDisplayNameChanged: (String) -> Unit,
    onSaveProfile: () -> Unit,
    onResetProfileDraft: () -> Unit,
    authRepository: AuthRepository,
    catalogRepository: CatalogRepository,
    libraryRepository: LibraryRepository,
    playlistRepository: PlaylistRepository,
    mediaDeliveryRepository: MediaDeliveryRepository,
    connectivityMonitor: ConnectivityMonitor,
    recentSearchRepository: RecentSearchRepository,
    playbackController: RakyzuPlaybackController,
    playbackPreferences: AndroidPlaybackPreferences,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(RakyzuRoute.Home)
    val currentRoute = backStack.lastOrNull()
    val playbackSnapshot by playbackController.snapshot.collectAsStateWithLifecycle()
    val playbackPreferenceState by playbackPreferences.state.collectAsStateWithLifecycle()
    val searchViewModel: SearchViewModel = viewModel(
        key = "search-$userId",
        factory = SearchViewModel.factory(
            userId = userId,
            repository = catalogRepository,
            recentSearchRepository = recentSearchRepository,
            connectivityMonitor = connectivityMonitor,
        ),
    )
    val libraryViewModel: LibraryViewModel = viewModel(
        key = "library-$userId",
        factory = LibraryViewModel.factory(
            userId = userId,
            repository = libraryRepository,
            connectivityMonitor = connectivityMonitor,
        ),
    )
    val playlistViewModel: PlaylistViewModel = viewModel(
        key = "playlists-$userId",
        factory = PlaylistViewModel.factory(userId, playlistRepository),
    )
    val libraryState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var showAccount by remember { mutableStateOf(false) }
    var isSigningOut by remember { mutableStateOf(false) }
    var signOutMessage by remember { mutableStateOf<String?>(null) }

    if (showAccount) {
        AccountSheet(
            email = email,
            displayName = displayName,
            displayNameDraft = profileDisplayNameDraft,
            isSavingProfile = isSavingProfile,
            profileMessage = profileMessage,
            profileMessageIsError = profileMessageIsError,
            isSigningOut = isSigningOut,
            message = signOutMessage,
            onDisplayNameChanged = onProfileDisplayNameChanged,
            onSaveProfile = onSaveProfile,
            playbackPreferences = playbackPreferenceState,
            onWifiQualityChanged = playbackPreferences::setWifiQuality,
            onMobileQualityChanged = playbackPreferences::setMobileQuality,
            onDataSaverChanged = playbackPreferences::setDataSaverEnabled,
            onDismiss = {
                if (!isSigningOut && !isSavingProfile) {
                    onResetProfileDraft()
                    showAccount = false
                }
            },
            onSignOut = {
                if (!isSigningOut) {
                    coroutineScope.launch {
                        isSigningOut = true
                        signOutMessage = null
                        when (val result = authRepository.signOut()) {
                            AuthActionResult.Success -> showAccount = false
                            is AuthActionResult.ConfirmationRequired -> showAccount = false
                            is AuthActionResult.RecoveryEmailSent -> showAccount = false
                            is AuthActionResult.Failure -> {
                                signOutMessage = result.reason.toSignOutMessage()
                            }
                        }
                        isSigningOut = false
                    }
                }
            },
        )
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            topLevelDestinations.forEach { destination ->
                item(
                    selected = currentRoute == destination.route ||
                        (currentRoute is RakyzuRoute.ArtistDetail ||
                            currentRoute is RakyzuRoute.AlbumDetail ||
                            currentRoute is RakyzuRoute.PlaylistDetail) &&
                        destination.route == backStack.firstOrNull(),
                    onClick = { selectTopLevelRoute(backStack, destination.route) },
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = null,
                        )
                    },
                    label = { Text(destination.label) },
                )
            }
        },
        modifier = modifier.fillMaxSize(),
        containerColor = RakyzuBlack,
        navigationSuiteColors = androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults.colors(
            navigationBarContainerColor = RakyzuSurface,
            navigationRailContainerColor = RakyzuSurface,
            navigationDrawerContainerColor = RakyzuSurface,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavDisplay(
                backStack = backStack,
                entryProvider = entryProvider {
                    entry<RakyzuRoute.Home> {
                        HomeRoute(
                            userId = userId,
                            repository = catalogRepository,
                            mediaDeliveryRepository = mediaDeliveryRepository,
                            connectivityMonitor = connectivityMonitor,
                            versionName = versionName,
                            displayName = displayName,
                            onTrackPlay = playbackController::playQueue,
                            onProfileClick = {
                                signOutMessage = null
                                showAccount = true
                            },
                            likedTrackIds = libraryState.likedTrackIds,
                            savedAlbumIds = libraryState.savedAlbumIds,
                            followedArtistIds = libraryState.followedArtistIds,
                            pendingTrackIds = libraryState.pendingTrackIds,
                            pendingAlbumIds = libraryState.pendingAlbumIds,
                            pendingArtistIds = libraryState.pendingArtistIds,
                            onTrackLikeChange = { track, saved ->
                                libraryViewModel.setSaved(LibraryItemKind.Track, track.id, saved)
                            },
                            onAlbumSaveChange = { track, saved ->
                                libraryViewModel.setSaved(LibraryItemKind.Album, track.albumId, saved)
                            },
                            onArtistFollowChange = { track, saved ->
                                libraryViewModel.setSaved(LibraryItemKind.Artist, track.artistId, saved)
                            },
                        )
                    }
                    entry<RakyzuRoute.Search> {
                        SearchRoute(
                            viewModel = searchViewModel,
                            onTrackPlay = playbackController::playQueue,
                            onTrackPlayNext = playbackController::playNext,
                            onTrackAddToQueue = playbackController::addToQueue,
                            onArtistClick = { artist ->
                                openArtistDetail(backStack, artist.id)
                            },
                            onAlbumClick = { album ->
                                openAlbumDetail(backStack, album.id)
                            },
                            onTrackArtistClick = { track ->
                                openArtistDetail(backStack, track.artistId)
                            },
                            onTrackAlbumClick = { track ->
                                openAlbumDetail(backStack, track.albumId)
                            },
                            likedTrackIds = libraryState.likedTrackIds,
                            pendingTrackIds = libraryState.pendingTrackIds,
                            onTrackLikeChange = { track, saved ->
                                libraryViewModel.setSaved(
                                    LibraryItemKind.Track,
                                    track.id,
                                    saved,
                                )
                            },
                        )
                    }
                    entry<RakyzuRoute.ArtistDetail> { route ->
                        val artistViewModel: ArtistDetailViewModel = viewModel(
                            key = "artist-$userId-${route.artistId}",
                            factory = ArtistDetailViewModel.factory(
                                artistId = route.artistId,
                                repository = catalogRepository,
                            ),
                        )
                        ArtistDetailRoute(
                            viewModel = artistViewModel,
                            onBack = { dismissArtistDetail(backStack) },
                            onTrackPlay = playbackController::playQueue,
                            onTrackPlayNext = playbackController::playNext,
                            onTrackAddToQueue = playbackController::addToQueue,
                            onAlbumClick = { album ->
                                openAlbumDetail(backStack, album.id)
                            },
                            onTrackAlbumClick = { track ->
                                openAlbumDetail(backStack, track.albumId)
                            },
                            isFollowed = route.artistId in libraryState.followedArtistIds,
                            isFollowPending = route.artistId in libraryState.pendingArtistIds,
                            onFollowChange = { saved ->
                                libraryViewModel.setSaved(
                                    LibraryItemKind.Artist,
                                    route.artistId,
                                    saved,
                                )
                            },
                            likedTrackIds = libraryState.likedTrackIds,
                            pendingTrackIds = libraryState.pendingTrackIds,
                            onTrackLikeChange = { track, saved ->
                                libraryViewModel.setSaved(
                                    LibraryItemKind.Track,
                                    track.id,
                                    saved,
                                )
                            },
                        )
                    }
                    entry<RakyzuRoute.AlbumDetail> { route ->
                        val albumViewModel: AlbumDetailViewModel = viewModel(
                            key = "album-$userId-${route.albumId}",
                            factory = AlbumDetailViewModel.factory(
                                albumId = route.albumId,
                                repository = catalogRepository,
                            ),
                        )
                        AlbumDetailRoute(
                            viewModel = albumViewModel,
                            onBack = { dismissAlbumDetail(backStack) },
                            onTrackPlay = playbackController::playQueue,
                            onTrackPlayNext = playbackController::playNext,
                            onTrackAddToQueue = playbackController::addToQueue,
                            onTrackArtistClick = { track ->
                                openArtistDetail(backStack, track.artistId)
                            },
                            isSaved = route.albumId in libraryState.savedAlbumIds,
                            isSavePending = route.albumId in libraryState.pendingAlbumIds,
                            onSaveChange = { saved ->
                                libraryViewModel.setSaved(
                                    LibraryItemKind.Album,
                                    route.albumId,
                                    saved,
                                )
                            },
                            likedTrackIds = libraryState.likedTrackIds,
                            pendingTrackIds = libraryState.pendingTrackIds,
                            onTrackLikeChange = { track, saved ->
                                libraryViewModel.setSaved(
                                    LibraryItemKind.Track,
                                    track.id,
                                    saved,
                                )
                            },
                        )
                    }
                    entry<RakyzuRoute.Library> {
                        LibraryRoute(
                            viewModel = libraryViewModel,
                            mediaDeliveryRepository = mediaDeliveryRepository,
                            onTrackPlay = playbackController::playQueue,
                            onAlbumClick = { album ->
                                openAlbumDetail(backStack, album.id)
                            },
                            onArtistClick = { artist ->
                                openArtistDetail(backStack, artist.id)
                            },
                            onBrowseMusic = {
                                selectTopLevelRoute(backStack, RakyzuRoute.Search)
                            },
                        )
                    }
                    entry<RakyzuRoute.Create> {
                        PlaylistRoute(viewModel = playlistViewModel, onPlaylistClick = { playlist ->
                            val route = RakyzuRoute.PlaylistDetail(playlist.id)
                            if (backStack.lastOrNull() != route) backStack.add(route)
                        })
                    }
                    entry<RakyzuRoute.PlaylistDetail> { route ->
                        val detailViewModel: PlaylistDetailViewModel = viewModel(
                            key = "playlist-$userId-${route.playlistId}",
                            factory = PlaylistDetailViewModel.factory(userId, route.playlistId,
                                playlistRepository, catalogRepository),
                        )
                        PlaylistDetailRoute(detailViewModel,
                            onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                            onPlay = playbackController::playQueue)
                    }
                    entry<RakyzuRoute.NowPlaying> {
                        NowPlayingScreen(
                            snapshot = playbackSnapshot,
                            isLiked = playbackSnapshot.mediaId in libraryState.likedTrackIds,
                            isAlbumSaved = playbackSnapshot.queue
                                .getOrNull(playbackSnapshot.currentIndex)
                                ?.albumId in libraryState.savedAlbumIds,
                            isArtistFollowed = playbackSnapshot.queue
                                .getOrNull(playbackSnapshot.currentIndex)
                                ?.artistId in libraryState.followedArtistIds,
                            isLikePending = playbackSnapshot.mediaId in libraryState.pendingTrackIds,
                            isAlbumPending = playbackSnapshot.queue
                                .getOrNull(playbackSnapshot.currentIndex)
                                ?.albumId in libraryState.pendingAlbumIds,
                            isArtistPending = playbackSnapshot.queue
                                .getOrNull(playbackSnapshot.currentIndex)
                                ?.artistId in libraryState.pendingArtistIds,
                            onLikeChange = { saved ->
                                playbackSnapshot.mediaId?.let { trackId ->
                                    libraryViewModel.setSaved(LibraryItemKind.Track, trackId, saved)
                                }
                            },
                            onAlbumSaveChange = { saved ->
                                playbackSnapshot.queue
                                    .getOrNull(playbackSnapshot.currentIndex)
                                    ?.albumId
                                    ?.takeIf(String::isNotBlank)
                                    ?.let { albumId ->
                                        libraryViewModel.setSaved(
                                            LibraryItemKind.Album,
                                            albumId,
                                            saved,
                                        )
                                    }
                            },
                            onArtistFollowChange = { saved ->
                                playbackSnapshot.queue
                                    .getOrNull(playbackSnapshot.currentIndex)
                                    ?.artistId
                                    ?.takeIf(String::isNotBlank)
                                    ?.let { artistId ->
                                        libraryViewModel.setSaved(
                                            LibraryItemKind.Artist,
                                            artistId,
                                            saved,
                                        )
                                    }
                            },
                            onDismiss = { dismissNowPlaying(backStack) },
                            onTogglePlayPause = playbackController::togglePlayPause,
                            onPrevious = playbackController::skipToPrevious,
                            onNext = playbackController::skipToNext,
                            onSeek = playbackController::seekTo,
                            onQueueItemClick = playbackController::skipToQueueItem,
                            onQueueItemMove = playbackController::moveQueueItem,
                            onQueueItemRemove = playbackController::removeQueueItem,
                            onClearQueue = playbackController::clearQueue,
                            onRetryPlayback = playbackController::retryPlayback,
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            if (
                playbackSnapshot.mediaId != null &&
                currentRoute != RakyzuRoute.NowPlaying
            ) {
                PlaybackBar(
                    snapshot = playbackSnapshot,
                    onOpenNowPlaying = { openNowPlaying(backStack) },
                    onTogglePlayPause = playbackController::togglePlayPause,
                    onNext = playbackController::skipToNext,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun SessionLoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF211240), RakyzuBlack))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(RakyzuPurple, RakyzuAqua))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "R",
                    color = RakyzuBlack,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                )
            }
            CircularProgressIndicator(color = RakyzuAqua)
            Text(
                text = "Restoring your secure session",
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSheet(
    email: String?,
    displayName: String,
    displayNameDraft: String,
    isSavingProfile: Boolean,
    profileMessage: String?,
    profileMessageIsError: Boolean,
    isSigningOut: Boolean,
    message: String?,
    onDisplayNameChanged: (String) -> Unit,
    onSaveProfile: () -> Unit,
    playbackPreferences: PlaybackPreferences,
    onWifiQualityChanged: (PlaybackQuality) -> Unit,
    onMobileQualityChanged: (PlaybackQuality) -> Unit,
    onDataSaverChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSignOut: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = RakyzuSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Your account",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = email ?: "Signed in to Rakyzu Music",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
            OutlinedTextField(
                value = displayNameDraft,
                onValueChange = onDisplayNameChanged,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSavingProfile && !isSigningOut,
                label = { Text("Display name") },
                supportingText = { Text("2-60 characters") },
                singleLine = true,
            )
            if (profileMessage != null) {
                Text(
                    text = profileMessage,
                    color = if (profileMessageIsError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        RakyzuAqua
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                onClick = onSaveProfile,
                enabled = !isSavingProfile && !isSigningOut && displayNameDraft != displayName,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RakyzuAqua,
                    contentColor = RakyzuBlack,
                ),
            ) {
                if (isSavingProfile) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = RakyzuBlack,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Save profile", fontWeight = FontWeight.Bold)
                }
            }
            Text(
                text = "Audio quality",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            QualitySelector(
                label = "Wi-Fi streaming",
                selected = playbackPreferences.wifiQuality,
                onSelected = onWifiQualityChanged,
                enabled = !isSigningOut,
            )
            QualitySelector(
                label = "Mobile data streaming",
                selected = playbackPreferences.mobileQuality,
                onSelected = onMobileQualityChanged,
                enabled = !isSigningOut && !playbackPreferences.dataSaverEnabled,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Data Saver", fontWeight = FontWeight.Bold)
                    Text(
                        "Uses Low quality on metered networks.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = playbackPreferences.dataSaverEnabled,
                    onCheckedChange = onDataSaverChanged,
                    enabled = !isSigningOut,
                )
            }
            Text(
                text = "Signing out removes this device's encrypted session. Other devices stay signed in.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (message != null) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                onClick = onSignOut,
                enabled = !isSigningOut && !isSavingProfile,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                if (isSigningOut) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Sign out", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun QualitySelector(
    label: String,
    selected: PlaybackQuality,
    onSelected: (PlaybackQuality) -> Unit,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlaybackQuality.entries.forEach { quality ->
                FilterChip(
                    selected = quality == selected,
                    onClick = { onSelected(quality) },
                    enabled = enabled,
                    label = { Text(quality.name) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                )
            }
        }
    }
}

private fun AuthFailure.toSessionMessage(): String = when (this) {
    AuthFailure.InvalidConfiguration -> "Sign-in is unavailable because this build is missing public configuration."
    AuthFailure.NetworkUnavailable -> "Your saved session could not be refreshed. Check your connection or sign in again."
    AuthFailure.SessionExpired -> "Your session expired. Sign in again to continue."
    else -> "Your secure session could not be restored. Sign in again to continue."
}

private fun AuthFailure.toSignOutMessage(): String = when (this) {
    AuthFailure.NetworkUnavailable -> "Check your connection and try signing out again."
    else -> "Unable to sign out right now. Try again."
}

@Composable
private fun FoundationDestination(
    title: String,
    message: String,
    icon: ImageVector,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF211240), RakyzuBlack, RakyzuBlack),
                ),
            )
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 28.dp)
            .padding(bottom = 76.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = RakyzuAqua,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun PlaybackBar(
    snapshot: PlaybackSnapshot,
    onOpenNowPlaying: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = snapshot.title ?: "Rakyzu Music"
    val isBuffering = snapshot.status == PlaybackStatus.Buffering ||
        snapshot.status == PlaybackStatus.Connecting
    val isPlaying = snapshot.status == PlaybackStatus.Playing

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        color = Color(0xFF302548),
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 8.dp,
    ) {
        Column {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenNowPlaying)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(listOf(RakyzuPurple, RakyzuAqua))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (snapshot.error != null) {
                                "Playback unavailable"
                            } else {
                                snapshot.artist ?: "Rakyzu Music"
                            },
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(12.dp)
                            .size(24.dp),
                        color = RakyzuAqua,
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(
                        onClick = onTogglePlayPause,
                        enabled = snapshot.error == null,
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause $title" else "Play $title",
                            tint = Color.White,
                        )
                    }
                }
                IconButton(
                    onClick = onNext,
                    enabled = snapshot.canSkipNext,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next track",
                        tint = Color.White,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { snapshot.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = RakyzuAqua,
                trackColor = Color.White.copy(alpha = 0.16f),
                drawStopIndicator = {},
            )
        }
    }
}
