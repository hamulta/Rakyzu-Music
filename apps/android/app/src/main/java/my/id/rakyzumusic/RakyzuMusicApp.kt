package my.id.rakyzumusic

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import java.io.ByteArrayOutputStream
import my.id.rakyzumusic.core.data.auth.AuthActionResult
import my.id.rakyzumusic.core.data.artist.ArtistWorkspaceRepository
import my.id.rakyzumusic.core.data.admin.AdminRepository
import my.id.rakyzumusic.core.data.admin.StaffPermission
import my.id.rakyzumusic.core.data.auth.AuthFailure
import my.id.rakyzumusic.core.data.auth.AuthRepository
import my.id.rakyzumusic.core.data.auth.AuthSessionState
import my.id.rakyzumusic.core.data.catalog.CatalogRepository
import my.id.rakyzumusic.core.data.context.TrackContextRepository
import my.id.rakyzumusic.core.data.context.NotificationSettingsResult
import my.id.rakyzumusic.core.data.library.LibraryRepository
import my.id.rakyzumusic.core.data.download.OfflineDownloadRepository
import my.id.rakyzumusic.core.data.media.MediaDeliveryRepository
import my.id.rakyzumusic.core.data.media.ArtworkRequestResult
import my.id.rakyzumusic.core.data.network.ConnectivityMonitor
import my.id.rakyzumusic.core.data.playlist.PlaylistRepository
import my.id.rakyzumusic.core.data.profile.ProfileRepository
import my.id.rakyzumusic.core.data.profile.ListenerProfile
import my.id.rakyzumusic.core.data.profile.ProfileAppearance
import my.id.rakyzumusic.core.data.search.RecentSearchRepository
import my.id.rakyzumusic.core.designsystem.theme.RakyzuAqua
import my.id.rakyzumusic.core.designsystem.theme.RakyzuBlack
import my.id.rakyzumusic.core.designsystem.theme.RakyzuPurple
import my.id.rakyzumusic.core.designsystem.theme.RakyzuSurface
import my.id.rakyzumusic.core.model.LibraryItemKind
import my.id.rakyzumusic.core.model.DownloadCollectionKind
import my.id.rakyzumusic.core.model.rakyzuTrackShareUri
import my.id.rakyzumusic.core.model.ReleaseNotificationPreference
import my.id.rakyzumusic.core.model.TrackContext
import my.id.rakyzumusic.core.playback.RakyzuPlaybackController
import my.id.rakyzumusic.core.playback.PlaybackSnapshot
import my.id.rakyzumusic.core.playback.PlaybackStatus
import my.id.rakyzumusic.core.playback.PlaybackPreferences
import my.id.rakyzumusic.core.playback.PlaybackQuality
import my.id.rakyzumusic.feature.auth.AuthRoute
import my.id.rakyzumusic.feature.auth.WelcomeScreen
import my.id.rakyzumusic.feature.auth.clearNativeCredentialState
import my.id.rakyzumusic.feature.admin.AdminRoute
import my.id.rakyzumusic.feature.admin.AdminViewModel
import my.id.rakyzumusic.feature.home.HomeRoute
import my.id.rakyzumusic.feature.library.LibraryRoute
import my.id.rakyzumusic.feature.library.LibraryViewModel
import my.id.rakyzumusic.feature.library.OfflineDownloadsViewModel
import my.id.rakyzumusic.feature.player.NowPlayingScreen
import my.id.rakyzumusic.feature.playlist.PlaylistRoute
import my.id.rakyzumusic.feature.playlist.PlaylistDetailRoute
import my.id.rakyzumusic.feature.playlist.PlaylistDetailViewModel
import my.id.rakyzumusic.feature.playlist.PlaylistViewModel
import my.id.rakyzumusic.feature.profile.OnboardingScreen
import my.id.rakyzumusic.feature.profile.ProfileLoadingScreen
import my.id.rakyzumusic.feature.profile.ProfileUnavailableScreen
import my.id.rakyzumusic.feature.profile.ProfileViewModel
import my.id.rakyzumusic.feature.profile.ArtistWelcomeDialog
import my.id.rakyzumusic.feature.profile.ArtistWorkspaceRoute
import my.id.rakyzumusic.feature.profile.ArtistWorkspaceViewModel
import my.id.rakyzumusic.feature.profile.IdentityName
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class TopLevelDestination(
    val route: RakyzuRoute,
    val label: String,
    val icon: ImageVector,
)

private val listenerDestinations = listOf(
    TopLevelDestination(RakyzuRoute.Home, "Home", Icons.Rounded.Home),
    TopLevelDestination(RakyzuRoute.Search, "Explore", Icons.Rounded.Search),
    TopLevelDestination(RakyzuRoute.Library, "Library", Icons.Rounded.LibraryMusic),
)

private const val ExperiencePreferences = "rakyzu_experience"
private const val WelcomeCompletedKey = "welcome_completed"
private const val NotificationPromptedPrefix = "notification_prompted_"

@Composable
fun RakyzuMusicApp(
    versionName: String,
    googleWebClientId: String,
    authRepository: AuthRepository,
    profileRepository: ProfileRepository,
    catalogRepository: CatalogRepository,
    trackContextRepository: TrackContextRepository,
    libraryRepository: LibraryRepository,
    playlistRepository: PlaylistRepository,
    mediaDeliveryRepository: MediaDeliveryRepository,
    connectivityMonitor: ConnectivityMonitor,
    recentSearchRepository: RecentSearchRepository,
    playbackControllerProvider: () -> RakyzuPlaybackController,
    onSessionEnded: () -> Unit,
    playbackPreferences: AndroidPlaybackPreferences,
    adminRepository: AdminRepository,
    artistWorkspaceRepository: ArtistWorkspaceRepository,
    offlineDownloadRepository: OfflineDownloadRepository,
    pendingTrackLink: StateFlow<String?>,
    onTrackLinkConsumed: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sessionState by authRepository.sessionState.collectAsStateWithLifecycle()

    LaunchedEffect(sessionState) {
        if (sessionState !is AuthSessionState.SignedIn) {
            onSessionEnded()
        }
    }

    when (val state = sessionState) {
        AuthSessionState.Initializing -> SessionLoadingScreen(modifier)
        AuthSessionState.SignedOut -> SignedOutExperience(
            authRepository = authRepository,
            googleWebClientId = googleWebClientId,
            modifier = modifier,
        )
        is AuthSessionState.RecoveryRequired -> AuthRoute(
            repository = authRepository,
            googleWebClientId = googleWebClientId,
            sessionMessage = state.failure.toSessionMessage(),
            modifier = modifier,
        )
        is AuthSessionState.PasswordRecovery -> AuthRoute(
            repository = authRepository,
            googleWebClientId = googleWebClientId,
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
            trackContextRepository = trackContextRepository,
            libraryRepository = libraryRepository,
            playlistRepository = playlistRepository,
            mediaDeliveryRepository = mediaDeliveryRepository,
            connectivityMonitor = connectivityMonitor,
            recentSearchRepository = recentSearchRepository,
            playbackControllerProvider = playbackControllerProvider,
            playbackPreferences = playbackPreferences,
            adminRepository = adminRepository,
            artistWorkspaceRepository = artistWorkspaceRepository,
            offlineDownloadRepository = offlineDownloadRepository,
            pendingTrackLink = pendingTrackLink,
            onTrackLinkConsumed = onTrackLinkConsumed,
            modifier = modifier,
        )
    }
}

@Composable
private fun SignedOutExperience(
    authRepository: AuthRepository,
    googleWebClientId: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences(ExperiencePreferences, Context.MODE_PRIVATE)
    }
    var shouldShowWelcome by remember(preferences) {
        mutableStateOf(!preferences.getBoolean(WelcomeCompletedKey, false))
    }
    if (shouldShowWelcome) {
        WelcomeScreen(
            onGetStarted = {
                preferences.edit()
                    .putBoolean(WelcomeCompletedKey, true)
                    .apply()
                shouldShowWelcome = false
            },
            modifier = modifier,
        )
    } else {
        AuthRoute(
            repository = authRepository,
            googleWebClientId = googleWebClientId,
            onExit = { shouldShowWelcome = true },
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
    trackContextRepository: TrackContextRepository,
    libraryRepository: LibraryRepository,
    playlistRepository: PlaylistRepository,
    mediaDeliveryRepository: MediaDeliveryRepository,
    connectivityMonitor: ConnectivityMonitor,
    recentSearchRepository: RecentSearchRepository,
    playbackControllerProvider: () -> RakyzuPlaybackController,
    playbackPreferences: AndroidPlaybackPreferences,
    adminRepository: AdminRepository,
    artistWorkspaceRepository: ArtistWorkspaceRepository,
    offlineDownloadRepository: OfflineDownloadRepository,
    pendingTrackLink: StateFlow<String?>,
    onTrackLinkConsumed: (String) -> Unit,
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
            profile = profile,
            displayName = profile.displayName,
            profileDisplayNameDraft = profileState.displayName,
            isSavingProfile = profileState.isSaving,
            profileMessage = profileState.message,
            profileMessageIsError = profileState.messageIsError,
            onProfileDisplayNameChanged = profileViewModel::updateDisplayName,
            onSaveProfile = { profileViewModel.saveProfile(completeOnboarding = false) },
            onResetProfileDraft = profileViewModel::resetDraft,
            onAcceptArtistTerms = profileViewModel::acceptArtistTerms,
            onUpdateArtistBiography = profileViewModel::updateArtistBiography,
            onProfileAppearanceChanged = profileViewModel::updateAppearance,
            onUploadAvatar = profileViewModel::uploadAvatar,
            onDeleteAvatar = profileViewModel::deleteAvatar,
            authRepository = authRepository,
            catalogRepository = catalogRepository,
            trackContextRepository = trackContextRepository,
            libraryRepository = libraryRepository,
            playlistRepository = playlistRepository,
            mediaDeliveryRepository = mediaDeliveryRepository,
            connectivityMonitor = connectivityMonitor,
            recentSearchRepository = recentSearchRepository,
            playbackController = playbackControllerProvider(),
            playbackPreferences = playbackPreferences,
            adminRepository = adminRepository,
            artistWorkspaceRepository = artistWorkspaceRepository,
            offlineDownloadRepository = offlineDownloadRepository,
            pendingTrackLink = pendingTrackLink,
            onTrackLinkConsumed = onTrackLinkConsumed,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3AdaptiveNavigationSuiteApi::class)
@Composable
private fun AuthenticatedRakyzuMusicApp(
    versionName: String,
    profile: ListenerProfile,
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
    onAcceptArtistTerms: (String) -> Unit,
    onUpdateArtistBiography: (String) -> Unit,
    onProfileAppearanceChanged: (ProfileAppearance) -> Unit,
    onUploadAvatar: (ByteArray) -> Unit,
    onDeleteAvatar: () -> Unit,
    authRepository: AuthRepository,
    catalogRepository: CatalogRepository,
    trackContextRepository: TrackContextRepository,
    libraryRepository: LibraryRepository,
    playlistRepository: PlaylistRepository,
    mediaDeliveryRepository: MediaDeliveryRepository,
    connectivityMonitor: ConnectivityMonitor,
    recentSearchRepository: RecentSearchRepository,
    playbackController: RakyzuPlaybackController,
    playbackPreferences: AndroidPlaybackPreferences,
    adminRepository: AdminRepository,
    artistWorkspaceRepository: ArtistWorkspaceRepository,
    offlineDownloadRepository: OfflineDownloadRepository,
    pendingTrackLink: StateFlow<String?>,
    onTrackLinkConsumed: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val backStack = rememberNavBackStack(RakyzuRoute.Home)
    val currentRoute = backStack.lastOrNull()
    var showNotificationPrimer by remember(userId) { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        context.getSharedPreferences(ExperiencePreferences, Context.MODE_PRIVATE)
            .edit().putBoolean(NotificationPromptedPrefix + userId.lowercase(), true).apply()
    }
    LaunchedEffect(userId, currentRoute) {
        if (currentRoute != RakyzuRoute.Home || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return@LaunchedEffect
        }
        val preferences = context.getSharedPreferences(ExperiencePreferences, Context.MODE_PRIVATE)
        val promptKey = NotificationPromptedPrefix + userId.lowercase()
        if (
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED &&
            !preferences.getBoolean(promptKey, false)
        ) {
            showNotificationPrimer = true
        }
    }
    if (showNotificationPrimer) {
        NotificationPermissionPrimer(
            onEnable = {
                showNotificationPrimer = false
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onNotNow = {
                showNotificationPrimer = false
                context.getSharedPreferences(ExperiencePreferences, Context.MODE_PRIVATE)
                    .edit().putBoolean(NotificationPromptedPrefix + userId.lowercase(), true).apply()
            },
        )
    }
    val playbackSnapshot by playbackController.snapshot.collectAsStateWithLifecycle()
    val pendingTrackId by pendingTrackLink.collectAsStateWithLifecycle()
    var trackLinkMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(userId, pendingTrackId) {
        val trackId = pendingTrackId ?: return@LaunchedEffect
        try {
            var catalog = catalogRepository.observeCatalog().first()
            var track = catalog.tracks.firstOrNull { it.id == trackId }
            if (track == null) {
                catalogRepository.refresh()
                catalog = catalogRepository.observeCatalog().first()
                track = catalog.tracks.firstOrNull { it.id == trackId }
            }
            if (track != null) {
                trackLinkMessage = null
                playbackController.playQueue(listOf(track), 0)
                openNowPlaying(backStack)
            } else {
                trackLinkMessage = "This shared track is unavailable on Rakyzu Music."
                delay(5_000)
                trackLinkMessage = null
            }
        } finally {
            onTrackLinkConsumed(trackId)
        }
    }
    val currentTrackId = playbackSnapshot.mediaId
    val currentTrackContextFlow = remember(userId, currentTrackId, trackContextRepository) {
        currentTrackId?.let { trackContextRepository.observe(userId, it) }
            ?: kotlinx.coroutines.flow.flowOf(null)
    }
    val currentTrackContext by currentTrackContextFlow.collectAsStateWithLifecycle(initialValue = null)
    var isTrackContextRefreshing by remember { mutableStateOf(false) }
    var trackContextMessage by remember { mutableStateOf<String?>(null) }
    var offlineTrackContextCount by remember(userId) { mutableStateOf(0) }
    LaunchedEffect(userId, currentTrackId) {
        val trackId = currentTrackId ?: return@LaunchedEffect
        isTrackContextRefreshing = true
        trackContextMessage = null
        val result = trackContextRepository.refresh(userId, trackId)
        if (result is my.id.rakyzumusic.core.data.context.TrackContextRefreshResult.Failure &&
            currentTrackContext == null
        ) {
            trackContextMessage = "Lyrics and credits are temporarily unavailable."
        }
        offlineTrackContextCount = trackContextRepository.cachedTrackCount(userId)
        isTrackContextRefreshing = false
    }
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
    val offlineDownloadsViewModel: OfflineDownloadsViewModel = viewModel(
        key = "offline-downloads-$userId",
        factory = OfflineDownloadsViewModel.factory(userId, offlineDownloadRepository),
    )
    val offlineDownloadsState by offlineDownloadsViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(userId, catalogRepository, offlineDownloadsViewModel) {
        catalogRepository.observeCatalog().collect { catalog ->
            if (catalog.lastSyncedAtEpochMillis != null) {
                offlineDownloadsViewModel.reconcileCatalog(catalog.tracks)
                trackContextRepository.reconcileCatalog(userId, catalog.tracks.mapTo(mutableSetOf()) { it.id })
                offlineTrackContextCount = trackContextRepository.cachedTrackCount(userId)
            }
        }
    }
    val playlistViewModel: PlaylistViewModel = viewModel(
        key = "playlists-$userId",
        factory = PlaylistViewModel.factory(userId, playlistRepository),
    )
    val adminViewModel: AdminViewModel = viewModel(
        key = "admin-$userId",
        factory = AdminViewModel.factory(adminRepository),
    )
    val adminState by adminViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(adminState.dashboard?.recommendations) {
        if (adminState.dashboard != null) catalogRepository.refresh()
    }
    val canOpenAdmin = adminState.dashboard?.context?.let {
        it.isStaff && it.can(StaffPermission.AdminAccess)
    } == true
    val canManageEditorial = adminState.dashboard?.context?.let {
        it.isStaff && it.can(StaffPermission.EditorialManage)
    } == true
    val canOpenArtistWorkspace = profile.artist?.isActive == true
    val topLevelDestinations = listenerDestinations
    val libraryState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var showAccount by remember { mutableStateOf(false) }
    var editorialArtworkRevision by remember(userId) { mutableStateOf(0L) }
    var isSigningOut by remember { mutableStateOf(false) }
    var signOutMessage by remember { mutableStateOf<String?>(null) }
    var releaseNotificationPreference by remember {
        mutableStateOf(ReleaseNotificationPreference.Off)
    }
    var notificationPreferenceWorking by remember { mutableStateOf(false) }
    var notificationPreferenceMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(userId, trackContextRepository) {
        when (val result = trackContextRepository.notificationSettings()) {
            is NotificationSettingsResult.Success -> {
                releaseNotificationPreference = result.settings.preference
            }
            is NotificationSettingsResult.Failure -> {
                notificationPreferenceMessage = "Release notification settings are temporarily unavailable."
            }
        }
        offlineTrackContextCount = trackContextRepository.cachedTrackCount(userId)
    }

    ArtistWelcomeDialog(
        profile = profile,
        isWorking = isSavingProfile,
        onConfirm = onAcceptArtistTerms,
    )

    if (showAccount) {
        AccountSheet(
            profile = profile,
            email = email,
            displayName = displayName,
            displayNameDraft = profileDisplayNameDraft,
            isSavingProfile = isSavingProfile,
            profileMessage = profileMessage,
            profileMessageIsError = profileMessageIsError,
            isSigningOut = isSigningOut,
            message = signOutMessage,
            onDisplayNameChanged = onProfileDisplayNameChanged,
            onAppearanceChanged = onProfileAppearanceChanged,
            onUpdateArtistBiography = onUpdateArtistBiography,
            onUploadAvatar = onUploadAvatar,
            onDeleteAvatar = onDeleteAvatar,
            mediaDeliveryRepository = mediaDeliveryRepository,
            onSaveProfile = onSaveProfile,
            playbackPreferences = playbackPreferenceState,
            onWifiQualityChanged = playbackPreferences::setWifiQuality,
            onMobileQualityChanged = playbackPreferences::setMobileQuality,
            onDataSaverChanged = playbackPreferences::setDataSaverEnabled,
            releaseNotificationPreference = releaseNotificationPreference,
            notificationPreferenceWorking = notificationPreferenceWorking,
            notificationPreferenceMessage = notificationPreferenceMessage,
            onReleaseNotificationPreferenceChanged = { preference ->
                if (!notificationPreferenceWorking) {
                    coroutineScope.launch {
                        notificationPreferenceWorking = true
                        notificationPreferenceMessage = null
                        when (val result = trackContextRepository.updateNotificationSettings(preference)) {
                            is NotificationSettingsResult.Success -> {
                                releaseNotificationPreference = result.settings.preference
                                notificationPreferenceMessage = "Release notification preference saved."
                            }
                            is NotificationSettingsResult.Failure -> {
                                notificationPreferenceMessage = "Could not save release notification preference."
                            }
                        }
                        notificationPreferenceWorking = false
                    }
                }
            },
            offlineTrackContextCount = offlineTrackContextCount,
            canOpenAdmin = canOpenAdmin,
            canOpenArtistWorkspace = canOpenArtistWorkspace,
            onOpenAdmin = {
                showAccount = false
                selectTopLevelRoute(backStack, RakyzuRoute.Admin)
            },
            onOpenArtistWorkspace = {
                showAccount = false
                selectTopLevelRoute(backStack, RakyzuRoute.ArtistWorkspace)
            },
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
                        val downloadPreparation = offlineDownloadRepository.prepareForSignOut(userId)
                        if (downloadPreparation is my.id.rakyzumusic.core.data.download.OfflineDownloadActionResult.Rejected) {
                            signOutMessage = "Unable to apply the download sign-out policy. Try again."
                            isSigningOut = false
                            return@launch
                        }
                        when (val result = authRepository.signOut()) {
                            AuthActionResult.Success -> {
                                clearNativeCredentialState(context)
                                showAccount = false
                            }
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
            navigationBarContainerColor = Color(0xFF121111),
            navigationRailContainerColor = Color(0xFF121111),
            navigationDrawerContainerColor = Color(0xFF121111),
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
                            avatarAvailable = profile.avatarAvailable,
                            avatarRevision = profile.avatarVersion,
                            artworkRevision = editorialArtworkRevision,
                            onTrackPlay = playbackController::playQueue,
                            onProfileClick = {
                                signOutMessage = null
                                showAccount = true
                            },
                            onSettingsClick = {
                                signOutMessage = null
                                showAccount = true
                            },
                            canManageEditorial = canManageEditorial,
                            onDeleteGlobalCard = { shelf ->
                                adminViewModel.deleteRecommendation(shelf.id)
                            },
                            onDeleteGlobalCardArtwork = { shelf ->
                                editorialArtworkRevision++
                                adminViewModel.deleteRecommendationArtwork(shelf.id)
                            },
                            onSaveGlobalCard = { mutation, artworkBytes ->
                                if (artworkBytes != null) editorialArtworkRevision++
                                adminViewModel.saveEditorialGroup(mutation, artworkBytes)
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
                            mediaDeliveryRepository = mediaDeliveryRepository,
                            artworkRevision = editorialArtworkRevision,
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
                            canManageEditorial = canManageEditorial,
                            onSaveEditorialGroup = { mutation, artworkBytes ->
                                if (artworkBytes != null) editorialArtworkRevision++
                                adminViewModel.saveEditorialGroup(mutation, artworkBytes)
                            },
                            onDeleteEditorialGroup = adminViewModel::deleteRecommendation,
                            onDeleteEditorialArtwork = { shelfId ->
                                editorialArtworkRevision++
                                adminViewModel.deleteRecommendationArtwork(shelfId)
                            },
                            onAddGroupToQueue = { tracks -> tracks.forEach(playbackController::addToQueue) },
                            onDownloadGroup = { group ->
                                offlineDownloadsViewModel.downloadEditorial(
                                    group.id,
                                    group.title,
                                    group.tracks,
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
                            downloadItems = offlineDownloadsState.snapshot.collectionItems(
                                DownloadCollectionKind.Album,
                                route.albumId,
                            ),
                            onDownloadAlbum = { album, tracks ->
                                offlineDownloadsViewModel.downloadAlbum(
                                    album.id,
                                    album.title,
                                    tracks,
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
                            downloadState = offlineDownloadsState,
                            onAllowMobileDownloads =
                                offlineDownloadsViewModel::setAllowMobileDownloads,
                            onKeepDownloadsAfterSignOut =
                                offlineDownloadsViewModel::setKeepDownloadsAfterSignOut,
                            onPauseDownload = offlineDownloadsViewModel::pause,
                            onResumeDownload = offlineDownloadsViewModel::resume,
                            onCancelDownload = offlineDownloadsViewModel::cancel,
                            onRetryDownload = offlineDownloadsViewModel::retry,
                            onClearDownloads = offlineDownloadsViewModel::clearCompleted,
                        )
                    }
                    entry<RakyzuRoute.Create> {
                        PlaylistRoute(viewModel = playlistViewModel, onPlaylistClick = { playlist ->
                            val route = RakyzuRoute.PlaylistDetail(playlist.id)
                            if (backStack.lastOrNull() != route) backStack.add(route)
                        })
                    }
                    entry<RakyzuRoute.Admin> {
                        AdminRoute(
                            repository = adminRepository,
                            viewModel = adminViewModel,
                        )
                    }
                    entry<RakyzuRoute.ArtistWorkspace> {
                        val artistWorkspaceViewModel: ArtistWorkspaceViewModel = viewModel(
                            key = "artist-workspace-$userId",
                            factory = ArtistWorkspaceViewModel.factory(artistWorkspaceRepository),
                        )
                        ArtistWorkspaceRoute(viewModel = artistWorkspaceViewModel)
                    }
                    entry<RakyzuRoute.PlaylistDetail> { route ->
                        val detailViewModel: PlaylistDetailViewModel = viewModel(
                            key = "playlist-$userId-${route.playlistId}",
                            factory = PlaylistDetailViewModel.factory(userId, route.playlistId,
                                playlistRepository, catalogRepository),
                        )
                        PlaylistDetailRoute(detailViewModel,
                            onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                            onPlay = playbackController::playQueue,
                            downloadItems = offlineDownloadsState.snapshot.collectionItems(
                                DownloadCollectionKind.Playlist,
                                route.playlistId,
                            ),
                            onDownloadPlaylist = offlineDownloadsViewModel::downloadPlaylist)
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
                            onShareTrack = { item ->
                                val shareUri = rakyzuTrackShareUri(item.mediaId)
                                val shareText = buildString {
                                    append(item.title)
                                    append(" — ")
                                    append(item.artist)
                                    shareUri?.let { append("\n").append(it) }
                                }
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Listen on Rakyzu Music")
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(
                                    Intent.createChooser(intent, "Share from Rakyzu Music"),
                                )
                            },
                            trackContext = currentTrackContext,
                            isTrackContextRefreshing = isTrackContextRefreshing,
                            trackContextMessage = trackContextMessage,
                            onRetryTrackContext = {
                                currentTrackId?.let { trackId ->
                                    coroutineScope.launch {
                                        isTrackContextRefreshing = true
                                        trackContextMessage = null
                                        val result = trackContextRepository.refresh(userId, trackId)
                                        if (result is my.id.rakyzumusic.core.data.context.TrackContextRefreshResult.Failure) {
                                            trackContextMessage = "Lyrics and credits are temporarily unavailable."
                                        }
                                        offlineTrackContextCount =
                                            trackContextRepository.cachedTrackCount(userId)
                                        isTrackContextRefreshing = false
                                    }
                                }
                            },
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            trackLinkMessage?.let { message ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    )
                }
            }

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
private fun NotificationPermissionPrimer(
    onEnable: () -> Unit,
    onNotNow: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onNotNow,
        containerColor = Color(0xFF252525),
        icon = {
            Box(
                modifier = Modifier.size(82.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(RakyzuPurple, RakyzuAqua))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = RakyzuBlack, modifier = Modifier.size(42.dp))
            }
        },
        title = { Text("Turn on notifications", fontWeight = FontWeight.Black) },
        text = {
            Text(
                "Be the first to hear about events, Rakyzu Music updates, and new song or radio releases. " +
                    "You stay in control and can change this anytime in Settings.",
            )
        },
        confirmButton = {
            Button(onClick = onEnable, modifier = Modifier.fillMaxWidth()) {
                Text("Turn on notifications")
            }
        },
        dismissButton = {
            TextButton(onClick = onNotNow, modifier = Modifier.fillMaxWidth()) { Text("Not now") }
        },
    )
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
    profile: ListenerProfile,
    mediaDeliveryRepository: MediaDeliveryRepository,
    email: String?,
    displayName: String,
    displayNameDraft: String,
    isSavingProfile: Boolean,
    profileMessage: String?,
    profileMessageIsError: Boolean,
    isSigningOut: Boolean,
    message: String?,
    onDisplayNameChanged: (String) -> Unit,
    onAppearanceChanged: (ProfileAppearance) -> Unit,
    onUpdateArtistBiography: (String) -> Unit,
    onUploadAvatar: (ByteArray) -> Unit,
    onDeleteAvatar: () -> Unit,
    onSaveProfile: () -> Unit,
    playbackPreferences: PlaybackPreferences,
    onWifiQualityChanged: (PlaybackQuality) -> Unit,
    onMobileQualityChanged: (PlaybackQuality) -> Unit,
    onDataSaverChanged: (Boolean) -> Unit,
    releaseNotificationPreference: ReleaseNotificationPreference,
    notificationPreferenceWorking: Boolean,
    notificationPreferenceMessage: String?,
    onReleaseNotificationPreferenceChanged: (ReleaseNotificationPreference) -> Unit,
    offlineTrackContextCount: Int,
    canOpenAdmin: Boolean,
    canOpenArtistWorkspace: Boolean,
    onOpenAdmin: () -> Unit,
    onOpenArtistWorkspace: () -> Unit,
    onDismiss: () -> Unit,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var artistBiography by remember(profile.artist?.id, profile.artist?.biography) {
        mutableStateOf(profile.artist?.biography.orEmpty())
    }
    var notificationPermissionMessage by remember { mutableStateOf<String?>(null) }
    var pendingNotificationPreference by remember {
        mutableStateOf<ReleaseNotificationPreference?>(null)
    }
    val avatarRequest = remember(mediaDeliveryRepository, profile.userId, profile.avatarVersion) {
        (mediaDeliveryRepository.profileAvatarRequest(profile.userId) as? ArtworkRequestResult.Ready)?.request
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val pending = pendingNotificationPreference
        pendingNotificationPreference = null
        if (granted && pending != null) {
            onReleaseNotificationPreferenceChanged(pending)
        } else if (!granted) {
            notificationPermissionMessage =
                "Notification permission remains off. You can enable it later in Android settings."
        }
    }
    fun selectReleasePreference(preference: ReleaseNotificationPreference) {
        notificationPermissionMessage = null
        if (preference == ReleaseNotificationPreference.Off ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            onReleaseNotificationPreferenceChanged(preference)
        } else {
            pendingNotificationPreference = preference
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) { decodeProfilePhoto(context, uri) }
                if (bytes != null) onUploadAvatar(bytes)
            }
        }
    }
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
            avatarRequest?.let { request ->
                val headers = NetworkHeaders.Builder().apply {
                    request.requestHeaders().forEach { (name, value) -> set(name, value) }
                }.build()
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(request.url)
                        .httpHeaders(headers)
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .networkCachePolicy(CachePolicy.DISABLED)
                        .build(),
                    contentDescription = "Current profile photo",
                    modifier = Modifier.size(88.dp).clip(CircleShape),
                )
            }
            if (canOpenAdmin || canOpenArtistWorkspace) {
                Text(
                    text = "Workspaces",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (canOpenAdmin) {
                    Button(
                        onClick = onOpenAdmin,
                        enabled = !isSavingProfile && !isSigningOut,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) {
                        Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open Admin")
                    }
                }
                if (canOpenArtistWorkspace) {
                    Button(
                        onClick = onOpenArtistWorkspace,
                        enabled = !isSavingProfile && !isSigningOut,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) {
                        Icon(Icons.Rounded.Album, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open Artist workspace")
                    }
                }
            }
            IdentityName(profile = profile)
            if (profile.verified) {
                Text(
                    text = profile.role?.replace("_", " ")?.replaceFirstChar(Char::uppercase)
                        ?: "Verified Artist",
                    color = RakyzuAqua,
                    fontWeight = FontWeight.Bold,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Role color animation", fontWeight = FontWeight.Bold)
                        Text("Turn off to use the default name style.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = profile.appearance == ProfileAppearance.Role,
                        onCheckedChange = { enabled ->
                            onAppearanceChanged(if (enabled) ProfileAppearance.Role else ProfileAppearance.Default)
                        },
                        enabled = !isSavingProfile && !isSigningOut,
                    )
                }
            }
            val activeArtist = profile.artist?.takeIf { it.isActive }
            if (activeArtist != null) {
                Text("Artist workspace", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Text("${activeArtist.name} · Verified Artist", color = RakyzuAqua)
                OutlinedTextField(
                    value = artistBiography,
                    onValueChange = { artistBiography = it.take(1_500) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSavingProfile && !isSigningOut,
                    label = { Text("Artist biography") },
                    supportingText = { Text("Only the linked Artist account can edit this biography.") },
                    minLines = 3,
                )
                Button(
                    onClick = { onUpdateArtistBiography(artistBiography) },
                    enabled = !isSavingProfile && !isSigningOut &&
                        artistBiography != activeArtist.biography,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save Artist biography") }
            }
            Text(
                text = email ?: "Signed in to Rakyzu Music",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(
                onClick = { photoPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) },
                enabled = !isSavingProfile && !isSigningOut,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (profile.avatarAvailable) "Change profile photo" else "Add profile photo")
            }
            if (profile.avatarAvailable) {
                Button(
                    onClick = onDeleteAvatar,
                    enabled = !isSavingProfile && !isSigningOut,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Remove profile photo") }
            }
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
                text = "Release notifications",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "Choose which Artist releases can use the New music releases channel. " +
                    "Account and security alerts stay in a separate Android channel.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReleaseNotificationPreference.entries.forEach { preference ->
                    FilterChip(
                        selected = releaseNotificationPreference == preference,
                        onClick = { selectReleasePreference(preference) },
                        enabled = !notificationPreferenceWorking && !isSigningOut,
                        label = {
                            Text(
                                when (preference) {
                                    ReleaseNotificationPreference.Off -> "Off"
                                    ReleaseNotificationPreference.FollowedArtists -> "Followed Artists"
                                    ReleaseNotificationPreference.AllSavedArtists -> "Saved Artists"
                                },
                            )
                        },
                    )
                }
            }
            if (notificationPreferenceWorking) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(
                text = "Offline lyrics and credits: $offlineTrackContextCount of 250 tracks cached",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            (notificationPermissionMessage ?: notificationPreferenceMessage)?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
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

private fun decodeProfilePhoto(context: android.content.Context, uri: Uri): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        ?: return null
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: return null
    return ByteArrayOutputStream().use { output ->
        @Suppress("DEPRECATION")
        if (!bitmap.compress(Bitmap.CompressFormat.WEBP, 88, output)) return null
        output.toByteArray().takeIf { it.size in 12..(5 * 1024 * 1024) }
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
