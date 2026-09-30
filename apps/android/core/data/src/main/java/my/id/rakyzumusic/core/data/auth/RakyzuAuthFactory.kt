package my.id.rakyzumusic.core.data.auth

import android.content.Context
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import java.net.URI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json
import my.id.rakyzumusic.core.data.catalog.CatalogRefreshFailure
import my.id.rakyzumusic.core.data.artist.ArtistWorkspaceRepository
import my.id.rakyzumusic.core.data.artist.AuthenticatedArtistWorkspaceRepository
import my.id.rakyzumusic.core.data.artist.UnavailableArtistWorkspaceRepository
import my.id.rakyzumusic.core.data.admin.AdminRepository
import my.id.rakyzumusic.core.data.admin.AuthenticatedAdminRepository
import my.id.rakyzumusic.core.data.admin.UnavailableAdminRepository
import my.id.rakyzumusic.core.data.catalog.CatalogRefreshResult
import my.id.rakyzumusic.core.data.catalog.CatalogRepository
import my.id.rakyzumusic.core.data.context.AuthenticatedTrackContextRepository
import my.id.rakyzumusic.core.data.context.TrackContextRepository
import my.id.rakyzumusic.core.data.context.UnavailableTrackContextRepository
import my.id.rakyzumusic.core.data.catalog.OfflineFirstCatalogRepository
import my.id.rakyzumusic.core.data.catalog.SupabaseCatalogRemoteDataSource
import my.id.rakyzumusic.core.data.library.LibraryActionResult
import my.id.rakyzumusic.core.data.library.LibraryFailure
import my.id.rakyzumusic.core.data.library.LibraryRepository
import my.id.rakyzumusic.core.data.library.OfflineFirstLibraryRepository
import my.id.rakyzumusic.core.data.library.SupabaseLibraryRemoteDataSource
import my.id.rakyzumusic.core.data.download.AuthenticatedOfflineDownloadRepository
import my.id.rakyzumusic.core.data.download.ExecutableOfflineDownloadRepository
import my.id.rakyzumusic.core.data.download.UnavailableOfflineDownloadRepository
import my.id.rakyzumusic.core.data.media.AccessTokenProvider
import my.id.rakyzumusic.core.data.media.AuthenticatedMediaDeliveryRepository
import my.id.rakyzumusic.core.data.media.MediaDeliveryRepository
import my.id.rakyzumusic.core.data.media.RakyzuApiConfiguration
import my.id.rakyzumusic.core.data.media.UnavailableMediaDeliveryRepository
import my.id.rakyzumusic.core.data.network.ConnectivityMonitor
import my.id.rakyzumusic.core.data.network.createConnectivityMonitor
import my.id.rakyzumusic.core.data.playlist.OfflineFirstPlaylistRepository
import my.id.rakyzumusic.core.data.playlist.PlaylistActionResult
import my.id.rakyzumusic.core.data.playlist.PlaylistFailure
import my.id.rakyzumusic.core.data.playlist.PlaylistRepository
import my.id.rakyzumusic.core.data.playlist.SupabasePlaylistRemoteDataSource
import my.id.rakyzumusic.core.data.profile.ProfileFailure
import my.id.rakyzumusic.core.data.profile.ProfileAppearance
import my.id.rakyzumusic.core.data.profile.ProfileRepository
import my.id.rakyzumusic.core.data.profile.ProfileResult
import my.id.rakyzumusic.core.data.profile.SupabaseProfileRepository
import my.id.rakyzumusic.core.data.queue.OfflineFirstPlaybackQueueRepository
import my.id.rakyzumusic.core.data.queue.PlaybackQueueRepository
import my.id.rakyzumusic.core.data.search.RecentSearchRepository
import my.id.rakyzumusic.core.data.search.createRecentSearchRepository
import my.id.rakyzumusic.core.database.catalog.createRakyzuLocalDataSources
import my.id.rakyzumusic.core.model.CatalogSnapshot
import my.id.rakyzumusic.core.model.HomeFeedSnapshot
import my.id.rakyzumusic.core.model.LibraryItemKind
import my.id.rakyzumusic.core.model.LibrarySnapshot
import my.id.rakyzumusic.core.model.PlaylistSnapshot

data class SupabasePublicConfiguration(
    val url: String,
    val publishableKey: String,
) {
    fun isValid(): Boolean {
        val uri = runCatching { URI(url) }.getOrNull()
        return uri?.scheme == "https" &&
            !uri.host.isNullOrBlank() &&
            publishableKey.isNotBlank()
    }
}

object RakyzuAuthFactory {
    fun create(
        context: Context,
        configuration: SupabasePublicConfiguration,
        applicationScope: CoroutineScope,
    ): AuthRepository {
        return createRepositories(context, configuration, applicationScope).authRepository
    }

    fun createRepositories(
        context: Context,
        configuration: SupabasePublicConfiguration,
        applicationScope: CoroutineScope,
        apiConfiguration: RakyzuApiConfiguration = RakyzuApiConfiguration(""),
    ): RakyzuRepositories {
        val connectivityMonitor = createConnectivityMonitor(context)
        val recentSearchRepository = createRecentSearchRepository(context)
        if (!configuration.isValid()) return RakyzuRepositories(
            authRepository = UnavailableAuthRepository,
            profileRepository = UnavailableProfileRepository,
            catalogRepository = UnavailableCatalogRepository,
            mediaDeliveryRepository = UnavailableMediaDeliveryRepository,
            libraryRepository = UnavailableLibraryRepository,
            playlistRepository = UnavailablePlaylistRepository,
            playbackQueueRepository = UnavailablePlaybackQueueRepository,
            connectivityMonitor = connectivityMonitor,
            recentSearchRepository = recentSearchRepository,
            adminRepository = UnavailableAdminRepository,
            artistWorkspaceRepository = UnavailableArtistWorkspaceRepository,
            offlineDownloadRepository = UnavailableOfflineDownloadRepository,
            trackContextRepository = UnavailableTrackContextRepository,
        )

        val localDataSources = createRakyzuLocalDataSources(context)
        val encryptedStore = EncryptedAuthStore(context)
        val sessionJson = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
        val client = createSupabaseClient(
            supabaseUrl = configuration.url,
            supabaseKey = configuration.publishableKey,
        ) {
            install(Auth) {
                flowType = FlowType.PKCE
                scheme = "my.id.rakyzumusic"
                host = "auth"
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
                autoSaveToStorage = true
                sessionManager = EncryptedSessionManager(encryptedStore, sessionJson)
                codeVerifierCache = EncryptedCodeVerifierCache(encryptedStore)
            }
            install(Postgrest) {
                requireValidSession = true
            }
        }
        val mediaDeliveryRepository = if (apiConfiguration.normalizedOriginOrNull() == null) {
            UnavailableMediaDeliveryRepository
        } else {
            AuthenticatedMediaDeliveryRepository(
                configuration = apiConfiguration,
                accessTokenProvider = AccessTokenProvider(client.auth::currentAccessTokenOrNull),
            )
        }
        return RakyzuRepositories(
            authRepository = SupabaseAuthRepository(
                auth = client.auth,
                recoveryState = PasswordRecoveryState(encryptedStore),
                applicationScope = applicationScope,
            ),
            profileRepository = SupabaseProfileRepository(client.auth, client.postgrest, apiConfiguration),
            catalogRepository = OfflineFirstCatalogRepository(
                localDataSource = localDataSources.catalog,
                remoteDataSource = SupabaseCatalogRemoteDataSource(client.postgrest),
            ),
            libraryRepository = OfflineFirstLibraryRepository(
                localDataSource = localDataSources.library,
                remoteDataSource = SupabaseLibraryRemoteDataSource(client.postgrest),
            ),
            playlistRepository = OfflineFirstPlaylistRepository(
                localDataSource = localDataSources.playlist,
                remoteDataSource = SupabasePlaylistRemoteDataSource(client.postgrest),
                activeUserId = { client.auth.currentUserOrNull()?.id },
                artworkRemote = my.id.rakyzumusic.core.data.playlist.PlaylistArtworkRemoteDataSource(
                    apiConfiguration, AccessTokenProvider(client.auth::currentAccessTokenOrNull),
                ),
            ),
            playbackQueueRepository = OfflineFirstPlaybackQueueRepository(
                localDataSource = localDataSources.playbackQueue,
            ),
            mediaDeliveryRepository = mediaDeliveryRepository,
            connectivityMonitor = connectivityMonitor,
            recentSearchRepository = recentSearchRepository,
            adminRepository = AuthenticatedAdminRepository(
                apiConfiguration,
                AccessTokenProvider(client.auth::currentAccessTokenOrNull),
            ),
            artistWorkspaceRepository = AuthenticatedArtistWorkspaceRepository(
                apiConfiguration,
                AccessTokenProvider(client.auth::currentAccessTokenOrNull),
            ),
            offlineDownloadRepository = AuthenticatedOfflineDownloadRepository(
                context = context,
                local = localDataSources.downloads,
                mediaDelivery = mediaDeliveryRepository,
                activeUserId = { client.auth.currentUserOrNull()?.id },
            ),
            trackContextRepository = AuthenticatedTrackContextRepository(
                configuration = apiConfiguration,
                tokens = AccessTokenProvider(client.auth::currentAccessTokenOrNull),
                local = localDataSources.trackContext,
            ),
        )
    }
}

data class RakyzuRepositories(
    val authRepository: AuthRepository,
    val profileRepository: ProfileRepository,
    val catalogRepository: CatalogRepository,
    val libraryRepository: LibraryRepository,
    val playlistRepository: PlaylistRepository,
    val playbackQueueRepository: PlaybackQueueRepository,
    val mediaDeliveryRepository: MediaDeliveryRepository,
    val connectivityMonitor: ConnectivityMonitor,
    val recentSearchRepository: RecentSearchRepository,
    val adminRepository: AdminRepository,
    val artistWorkspaceRepository: ArtistWorkspaceRepository,
    val offlineDownloadRepository: ExecutableOfflineDownloadRepository,
    val trackContextRepository: TrackContextRepository,
)

private data object UnavailablePlaybackQueueRepository : PlaybackQueueRepository {
    override suspend fun read(userId: String) =
        my.id.rakyzumusic.core.model.PersistedPlaybackQueue()

    override suspend fun replace(
        userId: String,
        items: List<my.id.rakyzumusic.core.model.PlaybackQueueItem>,
        currentIndex: Int,
    ) = false
}

private data object UnavailablePlaylistRepository : PlaylistRepository {
    override suspend fun artwork(userId: String, playlistId: String) =
        my.id.rakyzumusic.core.data.playlist.PlaylistArtworkResult.Failed
    override suspend fun updateArtwork(userId: String, playlistId: String, png: ByteArray?) =
        my.id.rakyzumusic.core.data.playlist.PlaylistArtworkResult.Failed
    override fun observeDetail(userId: String, playlistId: String) =
        flowOf<my.id.rakyzumusic.core.model.PlaylistDetail?>(null)
    override suspend fun refreshDetail(userId: String, playlistId: String) =
        PlaylistActionResult.Failure(PlaylistFailure.ServiceUnavailable)
    override suspend fun mutate(userId: String, playlistId: String, revision: Long,
        mutation: my.id.rakyzumusic.core.data.playlist.PlaylistMutation) =
        PlaylistActionResult.Failure(PlaylistFailure.ServiceUnavailable)
    override fun observePlaylists(userId: String) = flowOf(
        PlaylistSnapshot(playlists = emptyList(), lastSyncedAtEpochMillis = null),
    )

    override suspend fun refresh(userId: String) =
        PlaylistActionResult.Failure(PlaylistFailure.ServiceUnavailable)

    override suspend fun create(userId: String, name: String, description: String) =
        PlaylistActionResult.Failure(PlaylistFailure.ServiceUnavailable)
}

private data object UnavailableLibraryRepository : LibraryRepository {
    private val empty = LibrarySnapshot(
        likedTracks = emptyList(),
        savedAlbums = emptyList(),
        followedArtists = emptyList(),
        lastSyncedAtEpochMillis = null,
    )

    override fun observeLibrary(userId: String) = flowOf(empty)

    override suspend fun refresh(userId: String) =
        LibraryActionResult.Failure(LibraryFailure.ServiceUnavailable)

    override suspend fun setSaved(
        userId: String,
        kind: LibraryItemKind,
        itemId: String,
        saved: Boolean,
    ) = LibraryActionResult.Failure(LibraryFailure.ServiceUnavailable)
}

private data object UnavailableAuthRepository : AuthRepository {
    override val sessionState: StateFlow<AuthSessionState> = MutableStateFlow(
        AuthSessionState.RecoveryRequired(AuthFailure.InvalidConfiguration),
    )

    override suspend fun signIn(email: String, password: String): AuthActionResult = unavailable()

    override suspend fun signUp(email: String, password: String): AuthActionResult = unavailable()

    override suspend fun requestPasswordReset(email: String): AuthActionResult = unavailable()

    override suspend fun updatePassword(password: String): AuthActionResult = unavailable()

    override suspend fun signOut(): AuthActionResult = unavailable()

    override fun handleAuthCallback(callback: AuthCallback) = Unit

    private fun unavailable() = AuthActionResult.Failure(AuthFailure.InvalidConfiguration)
}

private data object UnavailableProfileRepository : ProfileRepository {
    override suspend fun getProfile(): ProfileResult = unavailable()

    override suspend fun updateProfile(
        displayName: String,
        completeOnboarding: Boolean,
    ): ProfileResult = unavailable()

    override suspend fun acceptArtistTerms(version: String): ProfileResult = unavailable()
    override suspend fun updateArtistBiography(biography: String): ProfileResult = unavailable()

    override suspend fun updateAppearance(mode: ProfileAppearance): ProfileResult = unavailable()
    override suspend fun uploadAvatar(webpBytes: ByteArray): ProfileResult = unavailable()
    override suspend fun deleteAvatar(): ProfileResult = unavailable()

    private fun unavailable() = ProfileResult.Failure(ProfileFailure.ServiceUnavailable)
}

private data object UnavailableCatalogRepository : CatalogRepository {
    private val emptyCatalog = CatalogSnapshot(
        artists = emptyList(),
        albums = emptyList(),
        tracks = emptyList(),
        lastSyncedAtEpochMillis = null,
    )

    override fun observeCatalog() = flowOf(emptyCatalog)

    override fun observeHomeFeed(userId: String) = flowOf(
        HomeFeedSnapshot(catalog = emptyCatalog, recentlyPlayed = emptyList()),
    )

    override suspend fun refresh() = CatalogRefreshResult.Failure(
        CatalogRefreshFailure.ServiceUnavailable,
    )

    override suspend fun recordRecentlyPlayed(userId: String, trackId: String) = false
}
