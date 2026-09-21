package my.id.rakyzumusic.core.data.profile

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import my.id.rakyzumusic.core.data.media.RakyzuApiConfiguration

internal class SupabaseProfileRepository(
    private val auth: Auth,
    private val postgrest: Postgrest,
    apiConfiguration: RakyzuApiConfiguration,
) : ProfileRepository {
    private val apiOrigin = apiConfiguration.normalizedOriginOrNull()
    override suspend fun getProfile(): ProfileResult = profileRequest { fetchContext() }

    override suspend fun updateProfile(
        displayName: String,
        completeOnboarding: Boolean,
    ): ProfileResult {
        val validation = DisplayName(displayName).validate()
        if (validation !is DisplayNameValidation.Valid) {
            return ProfileResult.Failure(ProfileFailure.InvalidDisplayName)
        }
        return profileRequest {
            val userId = activeUserId() ?: return@profileRequest noSession()
            postgrest["profiles"].update({
                set("display_name", validation.displayName)
                if (completeOnboarding) set("onboarding_completed", true)
            }) { filter { eq("id", userId) } }
            fetchContext()
        }
    }

    override suspend fun acceptArtistTerms(version: String): ProfileResult {
        if (version.isBlank()) return ProfileResult.Failure(ProfileFailure.InvalidRequest)
        return profileRequest {
            postgrest.rpc("accept_artist_terms", buildJsonObject { put("requested_version", version) })
            fetchContext()
        }
    }

    override suspend fun updateArtistBiography(biography: String): ProfileResult {
        if (biography.length > 1_500) return ProfileResult.Failure(ProfileFailure.InvalidRequest)
        return profileRequest {
            val token = auth.currentAccessTokenOrNull()?.takeIf(String::isNotBlank)
                ?: return@profileRequest noSession()
            val origin = apiOrigin ?: return@profileRequest ProfileResult.Failure(ProfileFailure.ServiceUnavailable)
            val payload = buildJsonObject { put("biography", biography.trim()) }
                .toString().toByteArray(Charsets.UTF_8)
            withContext(Dispatchers.IO) {
                val connection = URI("$origin/v1/artists/me/biography").toURL()
                    .openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "PUT"
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 30_000
                    connection.doOutput = true
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setFixedLengthStreamingMode(payload.size)
                    connection.outputStream.use { it.write(payload) }
                    when (connection.responseCode) {
                        in 200..299 -> fetchContext()
                        400, 403, 404, 422 -> ProfileResult.Failure(ProfileFailure.InvalidRequest)
                        401 -> noSession()
                        else -> ProfileResult.Failure(ProfileFailure.ServiceUnavailable)
                    }
                } finally {
                    connection.disconnect()
                }
            }
        }
    }

    override suspend fun updateAppearance(mode: ProfileAppearance): ProfileResult = profileRequest {
        postgrest.rpc("update_profile_appearance", buildJsonObject { put("requested_mode", mode.wireName) })
        fetchContext()
    }

    override suspend fun uploadAvatar(webpBytes: ByteArray): ProfileResult = avatarMutation(
        method = "PUT",
        bytes = webpBytes.takeIf(::isWebp),
    )

    override suspend fun deleteAvatar(): ProfileResult = avatarMutation(method = "DELETE", bytes = null)

    private suspend fun avatarMutation(method: String, bytes: ByteArray?): ProfileResult =
        withContext(Dispatchers.IO) {
            val userId = activeUserId() ?: return@withContext noSession()
            val token = auth.currentAccessTokenOrNull()?.takeIf(String::isNotBlank)
                ?: return@withContext noSession()
            val origin = apiOrigin ?: return@withContext ProfileResult.Failure(ProfileFailure.ServiceUnavailable)
            if (method == "PUT" && (bytes == null || bytes.size > MAX_AVATAR_BYTES)) {
                return@withContext ProfileResult.Failure(ProfileFailure.InvalidRequest)
            }
            val connection = URI("$origin/v1/profiles/$userId/avatar").toURL()
                .openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("Authorization", "Bearer $token")
                if (bytes != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "image/webp")
                    connection.setFixedLengthStreamingMode(bytes.size)
                    connection.outputStream.use { it.write(bytes) }
                }
                when (connection.responseCode) {
                    in 200..299 -> fetchContext()
                    400, 404, 409, 413, 415, 422 -> ProfileResult.Failure(ProfileFailure.InvalidRequest)
                    401 -> noSession()
                    else -> ProfileResult.Failure(ProfileFailure.ServiceUnavailable)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: IOException) {
                ProfileResult.Failure(ProfileFailure.NetworkUnavailable)
            } finally {
                connection.disconnect()
            }
        }

    private suspend fun fetchContext(): ProfileResult {
        if (activeUserId() == null) return noSession()
        return ProfileResult.Success(
            postgrest.rpc("get_my_profile_context").decodeSingle<ProfileContextRow>().toDomain(),
        )
    }

    private fun activeUserId(): String? = auth.currentUserOrNull()?.id?.takeIf(String::isNotBlank)
    private fun noSession() = ProfileResult.Failure(ProfileFailure.NoActiveSession)

    private fun isWebp(bytes: ByteArray): Boolean = bytes.size >= 12 &&
        bytes.copyOfRange(0, 4).contentEquals("RIFF".encodeToByteArray()) &&
        bytes.copyOfRange(8, 12).contentEquals("WEBP".encodeToByteArray())

    private suspend fun profileRequest(block: suspend () -> ProfileResult): ProfileResult = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        ProfileResult.Failure(error.toProfileFailure())
    }
}

private const val MAX_AVATAR_BYTES = 5 * 1024 * 1024

@Serializable
private data class ProfileContextRow(
    val userId: String,
    val displayName: String,
    val onboardingCompleted: Boolean,
    val avatarAvailable: Boolean = false,
    val avatarVersion: String? = null,
    val appearanceMode: String = "role",
    val identityKind: String = "listener",
    val role: String? = null,
    val verified: Boolean = false,
    val artist: ArtistIdentityRow? = null,
) {
    fun toDomain() = ListenerProfile(
        userId, displayName, onboardingCompleted, avatarAvailable,
        ProfileAppearance.fromWire(appearanceMode), identityKind, role, verified,
        artist?.toDomain(), avatarVersion,
    )
}

@Serializable
private data class ArtistIdentityRow(
    val id: String,
    val name: String,
    val status: String,
    val termsVersion: String? = null,
    val termsTitle: String? = null,
    val termsSummary: String? = null,
    val termsText: String? = null,
    val biography: String = "",
) {
    fun toDomain() = ArtistIdentity(id, name, status, termsVersion, termsTitle,
        termsSummary, termsText, biography)
}

private fun Throwable.toProfileFailure(): ProfileFailure = when (this) {
    is HttpRequestTimeoutException, is HttpRequestException -> ProfileFailure.NetworkUnavailable
    is PostgrestRestException -> ProfileFailure.ServiceUnavailable
    else -> ProfileFailure.Unexpected
}
