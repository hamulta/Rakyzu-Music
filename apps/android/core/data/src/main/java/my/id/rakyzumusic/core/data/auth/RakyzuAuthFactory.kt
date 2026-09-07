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
import my.id.rakyzumusic.core.data.catalog.CatalogRefreshResult
import my.id.rakyzumusic.core.data.catalog.CatalogRepository
import my.id.rakyzumusic.core.data.catalog.OfflineFirstCatalogRepository
import my.id.rakyzumusic.core.data.catalog.SupabaseCatalogRemoteDataSource
import my.id.rakyzumusic.core.data.library.LibraryActionResult
import my.id.rakyzumusic.core.data.library.LibraryFailure
import my.id.rakyzumusic.core.data.library.LibraryRepository
import my.id.rakyzumusic.core.data.library.OfflineFirstLibraryRepository
import my.id.rakyzumusic.core.data.library.SupabaseLibraryRemoteDataSource
import my.id.rakyzumusic.core.data.media.AccessTokenProvider
import my.id.rakyzumusic.core.data.media.AuthenticatedMediaDeliveryRepository
import my.id.rakyzumusic.core.data.media.MediaDeliveryRepository
import my.id.rakyzumusic.core.data.media.RakyzuApiConfiguration
import my.id.rakyzumusic.core.data.media.UnavailableMediaDeliveryRepository
import my.id.rakyzumusic.core.data.network.ConnectivityMonitor
import my.id.rakyzumusic.core.data.network.createConnectivityMonitor
import my.id.rakyzumusic.core.data.profile.ProfileFailure
import my.id.rakyzumusic.core.data.profile.ProfileRepository
import my.id.rakyzumusic.core.data.profile.ProfileResult
import my.id.rakyzumusic.core.data.profile.SupabaseProfileRepository
import my.id.rakyzumusic.core.data.search.RecentSearchRepository
import my.id.rakyzumusic.core.data.search.createRecentSearchRepository
import my.id.rakyzumusic.core.database.catalog.createRakyzuLocalDataSources
import my.id.rakyzumusic.core.model.CatalogSnapshot
import my.id.rakyzumusic.core.model.HomeFeedSnapshot
import my.id.rakyzumusic.core.model.LibraryItemKind
import my.id.rakyzumusic.core.model.LibrarySnapshot

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
            connectivityMonitor = connectivityMonitor,
            recentSearchRepository = recentSearchRepository,
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
        return RakyzuRepositories(
            authRepository = SupabaseAuthRepository(
                auth = client.auth,
                recoveryState = PasswordRecoveryState(encryptedStore),
                applicationScope = applicationScope,
            ),
            profileRepository = SupabaseProfileRepository(client.auth, client.postgrest),
            catalogRepository = OfflineFirstCatalogRepository(
                localDataSource = localDataSources.catalog,
                remoteDataSource = SupabaseCatalogRemoteDataSource(client.postgrest),
            ),
            libraryRepository = OfflineFirstLibraryRepository(
                localDataSource = localDataSources.library,
                remoteDataSource = SupabaseLibraryRemoteDataSource(client.postgrest),
            ),
            mediaDeliveryRepository = if (apiConfiguration.normalizedOriginOrNull() == null) {
                UnavailableMediaDeliveryRepository
            } else {
                AuthenticatedMediaDeliveryRepository(
                    configuration = apiConfiguration,
                    accessTokenProvider = AccessTokenProvider(client.auth::currentAccessTokenOrNull),
                )
            },
            connectivityMonitor = connectivityMonitor,
            recentSearchRepository = recentSearchRepository,
        )
    }
}

data class RakyzuRepositories(
    val authRepository: AuthRepository,
    val profileRepository: ProfileRepository,
    val catalogRepository: CatalogRepository,
    val libraryRepository: LibraryRepository,
    val mediaDeliveryRepository: MediaDeliveryRepository,
    val connectivityMonitor: ConnectivityMonitor,
    val recentSearchRepository: RecentSearchRepository,
)

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

    override fun markPasswordRecoveryCallback() = Unit

    private fun unavailable() = AuthActionResult.Failure(AuthFailure.InvalidConfiguration)
}

private data object UnavailableProfileRepository : ProfileRepository {
    override suspend fun getProfile(): ProfileResult = unavailable()

    override suspend fun updateProfile(
        displayName: String,
        completeOnboarding: Boolean,
    ): ProfileResult = unavailable()

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
