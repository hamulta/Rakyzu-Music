package my.id.rakyzumusic.core.data.profile

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal class SupabaseProfileRepository(
    private val auth: Auth,
    private val postgrest: Postgrest,
) : ProfileRepository {
    override suspend fun getProfile(): ProfileResult = profileRequest {
        val userId = activeUserId() ?: return@profileRequest ProfileResult.Failure(
            ProfileFailure.NoActiveSession,
        )
        val row = postgrest[PROFILES_TABLE].select(PROFILE_COLUMNS) {
            filter { eq("id", userId) }
        }.decodeSingle<ProfileRow>()
        ProfileResult.Success(row.toDomain())
    }

    override suspend fun updateProfile(
        displayName: String,
        completeOnboarding: Boolean,
    ): ProfileResult {
        val validation = DisplayName(displayName).validate()
        if (validation !is DisplayNameValidation.Valid) {
            return ProfileResult.Failure(ProfileFailure.InvalidDisplayName)
        }

        return profileRequest {
            val userId = activeUserId() ?: return@profileRequest ProfileResult.Failure(
                ProfileFailure.NoActiveSession,
            )
            val row = postgrest[PROFILES_TABLE].update(
                {
                    set("display_name", validation.displayName)
                    if (completeOnboarding) set("onboarding_completed", true)
                },
            ) {
                select(PROFILE_COLUMNS)
                filter { eq("id", userId) }
            }.decodeSingle<ProfileRow>()
            ProfileResult.Success(row.toDomain())
        }
    }

    private fun activeUserId(): String? = auth.currentUserOrNull()?.id?.takeIf(String::isNotBlank)

    private suspend fun profileRequest(block: suspend () -> ProfileResult): ProfileResult = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        ProfileResult.Failure(error.toProfileFailure())
    }

    private companion object {
        const val PROFILES_TABLE = "profiles"
        val PROFILE_COLUMNS = Columns.list("id", "display_name", "onboarding_completed")
    }
}

@Serializable
private data class ProfileRow(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("onboarding_completed") val onboardingCompleted: Boolean,
) {
    fun toDomain() = ListenerProfile(
        userId = id,
        displayName = displayName,
        onboardingCompleted = onboardingCompleted,
    )
}

private fun Throwable.toProfileFailure(): ProfileFailure = when (this) {
    is HttpRequestTimeoutException,
    is HttpRequestException,
    -> ProfileFailure.NetworkUnavailable
    is PostgrestRestException -> ProfileFailure.ServiceUnavailable
    else -> ProfileFailure.Unexpected
}
