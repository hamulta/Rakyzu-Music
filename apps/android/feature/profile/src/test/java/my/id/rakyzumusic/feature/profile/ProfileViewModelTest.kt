package my.id.rakyzumusic.feature.profile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import my.id.rakyzumusic.core.data.profile.ListenerProfile
import my.id.rakyzumusic.core.data.profile.ProfileFailure
import my.id.rakyzumusic.core.data.profile.ProfileAppearance
import my.id.rakyzumusic.core.data.profile.ProfileRepository
import my.id.rakyzumusic.core.data.profile.ProfileResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun profileLoadExposesRequiredOnboarding() = runTest(dispatcher) {
        val repository = FakeProfileRepository()
        val viewModel = ProfileViewModel(repository)
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.profile?.onboardingCompleted == true)
        assertEquals("Rakyzu Listener", viewModel.uiState.value.displayName)
    }

    @Test
    fun completingOnboardingPublishesUpdatedProfile() = runTest(dispatcher) {
        val repository = FakeProfileRepository()
        val viewModel = ProfileViewModel(repository)
        testScheduler.advanceUntilIdle()

        viewModel.updateDisplayName("  Rakyzu CEO  ")
        viewModel.saveProfile(completeOnboarding = true)
        testScheduler.advanceUntilIdle()

        assertEquals("  Rakyzu CEO  ", repository.lastDisplayName)
        assertTrue(repository.lastCompleteOnboarding)
        assertTrue(viewModel.uiState.value.profile?.onboardingCompleted == true)
        assertEquals("Rakyzu CEO", viewModel.uiState.value.displayName)
    }

    @Test
    fun profileFailureUsesSafeRetryableMessage() = runTest(dispatcher) {
        val repository = FakeProfileRepository(
            loadResult = ProfileResult.Failure(ProfileFailure.NetworkUnavailable),
        )
        val viewModel = ProfileViewModel(repository)
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.message?.startsWith("No connection") == true)
        assertTrue(viewModel.uiState.value.messageIsError)
    }
}

private class FakeProfileRepository(
    private val loadResult: ProfileResult = ProfileResult.Success(INCOMPLETE_PROFILE),
) : ProfileRepository {
    var lastDisplayName: String? = null
    var lastCompleteOnboarding = false

    override suspend fun getProfile(): ProfileResult = loadResult

    override suspend fun updateProfile(
        displayName: String,
        completeOnboarding: Boolean,
    ): ProfileResult {
        lastDisplayName = displayName
        lastCompleteOnboarding = completeOnboarding
        return ProfileResult.Success(
            INCOMPLETE_PROFILE.copy(
                displayName = displayName.trim(),
                onboardingCompleted = completeOnboarding,
            ),
        )
    }

    override suspend fun acceptArtistTerms(version: String): ProfileResult = loadResult
    override suspend fun updateArtistBiography(biography: String): ProfileResult = loadResult

    override suspend fun updateAppearance(mode: ProfileAppearance): ProfileResult = loadResult
    override suspend fun uploadAvatar(webpBytes: ByteArray): ProfileResult = loadResult
    override suspend fun deleteAvatar(): ProfileResult = loadResult

    private companion object {
        val INCOMPLETE_PROFILE = ListenerProfile(
            userId = "listener-id",
            displayName = "Rakyzu Listener",
            onboardingCompleted = false,
        )
    }
}
