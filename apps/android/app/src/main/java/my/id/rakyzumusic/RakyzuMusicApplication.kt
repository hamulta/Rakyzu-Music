package my.id.rakyzumusic

import android.app.Application
import my.id.rakyzumusic.core.data.auth.AuthRepository
import my.id.rakyzumusic.core.data.auth.RakyzuAuthFactory
import my.id.rakyzumusic.core.data.auth.RakyzuRepositories
import my.id.rakyzumusic.core.data.auth.SupabasePublicConfiguration
import my.id.rakyzumusic.core.data.catalog.CatalogRepository
import my.id.rakyzumusic.core.data.media.MediaDeliveryRepository
import my.id.rakyzumusic.core.data.media.RakyzuApiConfiguration
import my.id.rakyzumusic.core.data.profile.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class RakyzuMusicApplication : Application() {
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
}
