package my.id.rakyzumusic

import android.app.Application
import my.id.rakyzumusic.core.data.auth.AuthRepository
import my.id.rakyzumusic.core.data.auth.AuthSessionState
import my.id.rakyzumusic.core.data.auth.RakyzuAuthFactory
import my.id.rakyzumusic.core.data.auth.RakyzuRepositories
import my.id.rakyzumusic.core.data.auth.SupabasePublicConfiguration
import my.id.rakyzumusic.core.data.catalog.CatalogRepository
import my.id.rakyzumusic.core.data.media.MediaDeliveryRepository
import my.id.rakyzumusic.core.data.media.RakyzuApiConfiguration
import my.id.rakyzumusic.core.data.media.MediaStreamRequestFailure
import my.id.rakyzumusic.core.data.media.MediaStreamRequestResult
import my.id.rakyzumusic.core.data.profile.ProfileRepository
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

class RakyzuMusicApplication : Application(), PlaybackDependencies {
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

    val mediaDeliveryRepository: MediaDeliveryRepository
        get() = repositories.mediaDeliveryRepository

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
