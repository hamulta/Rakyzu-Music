package my.id.rakyzumusic

import android.app.Application
import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import my.id.rakyzumusic.core.data.auth.AuthRepository
import my.id.rakyzumusic.core.data.admin.AdminRepository
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
import my.id.rakyzumusic.core.data.queue.PlaybackQueueRepository
import my.id.rakyzumusic.core.data.search.RecentSearchRepository
import my.id.rakyzumusic.core.playback.PlaybackDependencies
import my.id.rakyzumusic.core.playback.BoundedPlaybackDiagnosticSink
import my.id.rakyzumusic.core.playback.PlaybackNetworkRequest
import my.id.rakyzumusic.core.playback.PlaybackQualityProvider
import my.id.rakyzumusic.core.playback.PlaybackRequestFailure
import my.id.rakyzumusic.core.playback.PlaybackStreamRequestProvider
import my.id.rakyzumusic.core.playback.PlaybackStreamRequestResult
import my.id.rakyzumusic.core.playback.RakyzuPlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath

class RakyzuMusicApplication : Application(), PlaybackDependencies, SingletonImageLoader.Factory {
    private val backgroundFailureHandler = CoroutineExceptionHandler { _, failure ->
        // A failed optional background task must not kill the listener's foreground UI.
        Log.e("RakyzuBackground", "Background task failed: ${failure.javaClass.simpleName}")
    }
    private val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + backgroundFailureHandler,
    )
    private val queueStateUpdates = Channel<AccountQueueState>(Channel.CONFLATED)
    private var restoredQueueForUserId: String? = null

    override fun onCreate() {
        super.onCreate()
        // Initialize the repository graph after all property delegates exist, before any
        // observer can race MainActivity for the same synchronized lazy instance.
        repositories
        applicationScope.launch {
            for (update in queueStateUpdates) {
                try {
                    playbackQueueRepository.replace(
                        userId = update.userId,
                        items = update.snapshot.queue,
                        currentIndex = update.snapshot.currentIndex,
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Exception) {
                    Log.e("RakyzuBackground", "Queue persistence failed: ${failure.javaClass.simpleName}")
                }
            }
        }
        applicationScope.launch {
            authRepository.sessionState.collect { session ->
                val userId = (session as? AuthSessionState.SignedIn)?.userId
                if (userId == null) {
                    restoredQueueForUserId = null
                } else if (restoredQueueForUserId != userId) {
                    // A restored queue is intentionally paused. The listener makes the next play
                    // decision after returning to the app, rather than resuming unexpectedly.
                    try {
                        val queue = playbackQueueRepository.read(userId)
                        playbackController.restoreQueue(queue)
                        restoredQueueForUserId = userId
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (failure: Exception) {
                        Log.e("RakyzuBackground", "Queue restore failed: ${failure.javaClass.simpleName}")
                    }
                }
            }
        }
        applicationScope.launch {
            connectivityMonitor.isOnline.drop(1).filter { it }.collect {
                if (playbackControllerDelegate.isInitialized() &&
                    playbackController.snapshot.value.recovery.canRetry
                ) {
                    try {
                        playbackController.retryPlayback()
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (failure: Exception) {
                        Log.e("RakyzuBackground", "Playback retry failed: ${failure.javaClass.simpleName}")
                    }
                }
            }
        }
    }

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

    val playbackQueueRepository: PlaybackQueueRepository
        get() = repositories.playbackQueueRepository

    val mediaDeliveryRepository: MediaDeliveryRepository
        get() = repositories.mediaDeliveryRepository

    val connectivityMonitor: ConnectivityMonitor
        get() = repositories.connectivityMonitor

    val recentSearchRepository: RecentSearchRepository
        get() = repositories.recentSearchRepository

    val adminRepository: AdminRepository
        get() = repositories.adminRepository

    val playbackPreferences: AndroidPlaybackPreferences by lazy {
        AndroidPlaybackPreferences(this)
    }

    override val playbackStreamRequestProvider: PlaybackStreamRequestProvider by lazy {
        PlaybackStreamRequestProvider { trackId ->
            mediaDeliveryRepository.streamRequest(trackId).toPlaybackRequestResult()
        }
    }

    override val playbackQualityProvider: PlaybackQualityProvider by lazy {
        PlaybackQualityProvider(playbackPreferences::effectiveQuality)
    }

    private val playbackControllerDelegate = lazy {
        RakyzuPlaybackController(
            context = this,
            onMediaItemTransition = { trackId ->
                val signedIn = authRepository.sessionState.value as? AuthSessionState.SignedIn
                if (signedIn != null) {
                    applicationScope.launch {
                        catalogRepository.recordRecentlyPlayed(signedIn.userId, trackId)
                    }
                }
            },
            onQueueStateChanged = { snapshot ->
                val signedIn = authRepository.sessionState.value as? AuthSessionState.SignedIn
                if (signedIn != null) {
                    queueStateUpdates.trySend(AccountQueueState(signedIn.userId, snapshot))
                }
            },
            diagnosticSink = BoundedPlaybackDiagnosticSink { line ->
                Log.d("RakyzuPlayback", line)
            },
        )
    }

    val playbackController: RakyzuPlaybackController
        get() = playbackControllerDelegate.value

    fun stopPlaybackIfRunning() {
        if (playbackControllerDelegate.isInitialized()) {
            playbackControllerDelegate.value.stopAndClear()
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

private data class AccountQueueState(
    val userId: String,
    val snapshot: my.id.rakyzumusic.core.playback.PlaybackSnapshot,
)

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
