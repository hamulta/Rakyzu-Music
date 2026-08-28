package my.id.rakyzumusic

import android.app.Application
import my.id.rakyzumusic.core.data.auth.AuthRepository
import my.id.rakyzumusic.core.data.auth.RakyzuAuthFactory
import my.id.rakyzumusic.core.data.auth.SupabasePublicConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class RakyzuMusicApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val authRepository: AuthRepository by lazy {
        RakyzuAuthFactory.create(
            context = this,
            configuration = SupabasePublicConfiguration(
                url = BuildConfig.SUPABASE_URL,
                publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
            ),
            applicationScope = applicationScope,
        )
    }
}
