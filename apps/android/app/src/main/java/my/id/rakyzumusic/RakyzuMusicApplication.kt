package my.id.rakyzumusic

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import my.id.rakyzumusic.core.data.auth.AuthRepository
import my.id.rakyzumusic.core.data.auth.AuthSessionState
import my.id.rakyzumusic.core.data.auth.RakyzuAuthFactory
import my.id.rakyzumusic.core.data.auth.RakyzuRepositories
import my.id.rakyzumusic.core.data.auth.SupabasePublicConfiguration
import my.id.rakyzumusic.core.data.catalog.CatalogRepository
import my.id.rakyzumusic.core.data.library.LibraryRepository
import my.id.rakyzumusic.core.data.media.MediaDeliveryRepository
import my.id.rakyzumusic.core.data.media.RakyzuApiConfiguration
import my.id.rakyzumusic.core.data.media.MediaStreamRequestFailure
import my.id.rakyzumusic.core.data.media.MediaStreamRequestResult
import my.id.rakyzumusic.core.data.network.ConnectivityMonitor
import my.id.rakyzumusic.core.data.playlist.PlaylistRepository
import my.id.rakyzumusic.core.data.profile.ProfileRepository
import my.id.rakyzumusic.core.data.search.RecentSearchRepository
import my.id.rakyzumusic.core.playback.PlaybackDependencies
import my.id.rakyzumusic.core.playback.PlaybackNetworkRequest
import my.id.rakyzumusic.core.playback.PlaybackRequestFailure
import my.id.rakyzumusic.core.playback.PlaybackStreamRequestProvider
import my.id.rakyzumusic.core.playback.PlaybackStreamRequestResult
import my.id.rakyzumusic.core.playback.RakyzuPlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath

class RakyzuMusicApplication : Application(), PlaybackDependencies, SingletonImageLoader.Factory {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val repositories: RakyzuRepositories by lazy {
        RakyzuAuthFactory.createRepositories(
            context = this,
            configuration = SupabasePublicConfiguration(
                url = BuildConfig.SUPABASE_URL,
                publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
            ),
            applicationScope = applicationScope,
            apiConfiguration = RakyzuApiConfiguration(BuildConfig.RAKYZU_API_BASE_URL),
        )
    }

    val authRepository: AuthRepository
        get() = repositories.authRepository

    val profileRepository: ProfileRepository
        get() = repositories.profileRepository

    val catalogRepository: CatalogRepository
        get() = repositories.catalogRepository

    val libraryRepository: LibraryRepository
        get() = repositories.libraryRepository

    val playlistRepository: PlaylistRepository
        get() = repositories.playlistRepository

    val mediaDeliveryRepository: MediaDeliveryRepository
        get() = repositories.mediaDeliveryRepository

    val connectivityMonitor: ConnectivityMonitor
        get() = repositories.connectivityMonitor

    val recentSearchRepository: RecentSearchRepository
        get() = repositories.recentSearchRepository

    override val playbackStreamRequestProvider: PlaybackStreamRequestProvider by lazy {
        PlaybackStreamRequestProvider { trackId ->
            mediaDeliveryRepository.streamRequest(trackId).toPlaybackRequestResult()
        }
    }

    val playbackController: RakyzuPlaybackController by lazy {
        RakyzuPlaybackController(this) { trackId ->
            val signedIn = authRepository.sessionState.value as? AuthSessionState.SignedIn
            if (signedIn != null) {
                applicationScope.launch {
                    catalogRepository.recordRecentlyPlayed(signedIn.userId, trackId)
                }
            }
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(context, ARTWORK_MEMORY_CACHE_PERCENT)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve(ARTWORK_CACHE_DIRECTORY).toOkioPath())
                .maxSizeBytes(ARTWORK_DISK_CACHE_BYTES)
                .build()
        }
        .components {
            add(
                OkHttpNetworkFetcherFactory(
                    cacheStrategy = { CacheControlCacheStrategy() },
                ),
            )
        }
        .crossfade(true)
        .build()

    private companion object {
        const val ARTWORK_MEMORY_CACHE_PERCENT = 0.25
        const val ARTWORK_DISK_CACHE_BYTES = 128L * 1024L * 1024L
        const val ARTWORK_CACHE_DIRECTORY = "rakyzu_music_artwork"
    }
}

private fun MediaStreamRequestResult.toPlaybackRequestResult(): PlaybackStreamRequestResult =
    when (this) {
        is MediaStreamRequestResult.Ready -> PlaybackStreamRequestResult.Ready(
            PlaybackNetworkRequest(
                url = request.url,
                headers = request.requestHeaders(),
            ),
        )
        is MediaStreamRequestResult.Failure -> PlaybackStreamRequestResult.Failure(
            when (reason) {
                MediaStreamRequestFailure.InvalidConfiguration -> {
                    PlaybackRequestFailure.InvalidConfiguration
                }
                MediaStreamRequestFailure.InvalidTrackId -> PlaybackRequestFailure.InvalidMediaId
                MediaStreamRequestFailure.NotAuthenticated -> PlaybackRequestFailure.NotAuthenticated
            },
        )
    }
