package my.id.rakyzumusic.feature.auth

import my.id.rakyzumusic.core.data.auth.AuthActionResult
import my.id.rakyzumusic.core.data.auth.AuthRepository
import my.id.rakyzumusic.core.data.auth.AuthSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
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
    fun malformedEmailIsRejectedWithoutNetworkRequest() = runTest(dispatcher) {
        val repository = FakeAuthRepository()
        val viewModel = AuthViewModel(repository)

        viewModel.updateEmail("invalid")
        viewModel.updatePassword("Secure-Password1")
        viewModel.submit()

        assertEquals(0, repository.signInCalls)
        assertEquals("Enter a valid email address.", viewModel.uiState.value.message)
        assertTrue(viewModel.uiState.value.messageIsError)
    }

    @Test
    fun signInNormalizesEmailAndClearsPasswordAfterSuccess() = runTest(dispatcher) {
        val repository = FakeAuthRepository(signInResult = AuthActionResult.Success)
        val viewModel = AuthViewModel(repository)

        viewModel.updateEmail("  Listener@Rakyzu.My.Id ")
        viewModel.updatePassword("Secure-Password1")
        viewModel.submit()
        testScheduler.advanceUntilIdle()

        assertEquals("listener@rakyzu.my.id", repository.lastEmail)
        assertEquals("", viewModel.uiState.value.password)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun signUpConfirmationReturnsToSignInWithSafeMessage() = runTest(dispatcher) {
        val repository = FakeAuthRepository(
            signUpResult = AuthActionResult.ConfirmationRequired("listener@rakyzu.my.id"),
        )
        val viewModel = AuthViewModel(repository)

        viewModel.switchMode()
        viewModel.updateEmail("listener@rakyzu.my.id")
        viewModel.updatePassword("Secure-Password1")
        viewModel.updatePasswordConfirmation("Secure-Password1")
        viewModel.submit()
        testScheduler.advanceUntilIdle()

        assertEquals(AuthMode.SignIn, viewModel.uiState.value.mode)
        assertEquals(1, repository.signUpCalls)
        assertTrue(viewModel.uiState.value.message?.contains("Check your email") == true)
        assertFalse(viewModel.uiState.value.messageIsError)
    }

    @Test
    fun recoveryRequestNormalizesEmailAndUsesNonEnumeratingMessage() = runTest(dispatcher) {
        val repository = FakeAuthRepository(
            recoveryResult = AuthActionResult.RecoveryEmailSent("listener@rakyzu.my.id"),
        )
        val viewModel = AuthViewModel(repository)

        viewModel.showForgotPassword()
        viewModel.updateEmail("  Listener@Rakyzu.My.Id ")
        viewModel.submit()
        testScheduler.advanceUntilIdle()

        assertEquals("listener@rakyzu.my.id", repository.lastEmail)
        assertEquals(AuthMode.SignIn, viewModel.uiState.value.mode)
        assertTrue(viewModel.uiState.value.message?.startsWith("If an account exists") == true)
        assertFalse(viewModel.uiState.value.messageIsError)
    }

    @Test
    fun resetPasswordRequiresMatchingStrongPasswords() = runTest(dispatcher) {
        val repository = FakeAuthRepository()
        val viewModel = AuthViewModel(repository)

        viewModel.showPasswordReset()
        viewModel.updatePassword("Secure-Password1")
        viewModel.updatePasswordConfirmation("Different-Password1")
        viewModel.submit()
        testScheduler.advanceUntilIdle()

        assertEquals(0, repository.updatePasswordCalls)
        assertEquals("Passwords do not match.", viewModel.uiState.value.message)
    }

    @Test
    fun resetPasswordSubmitsTheNewCredential() = runTest(dispatcher) {
        val repository = FakeAuthRepository()
        val viewModel = AuthViewModel(repository)

        viewModel.showPasswordReset()
        viewModel.updatePassword("Secure-Password1")
        viewModel.updatePasswordConfirmation("Secure-Password1")
        viewModel.submit()
        testScheduler.advanceUntilIdle()

        assertEquals(1, repository.updatePasswordCalls)
        assertEquals("", viewModel.uiState.value.password)
        assertEquals("", viewModel.uiState.value.passwordConfirmation)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }
}

private class FakeAuthRepository(
    private val signInResult: AuthActionResult = AuthActionResult.Success,
    private val signUpResult: AuthActionResult = AuthActionResult.Success,
    private val recoveryResult: AuthActionResult = AuthActionResult.Success,
    private val updatePasswordResult: AuthActionResult = AuthActionResult.Success,
) : AuthRepository {
    override val sessionState: StateFlow<AuthSessionState> = MutableStateFlow(AuthSessionState.SignedOut)
    var signInCalls = 0
    var signUpCalls = 0
    var lastEmail: String? = null
    var updatePasswordCalls = 0

    override suspend fun signIn(email: String, password: String): AuthActionResult {
        signInCalls += 1
        lastEmail = email
        return signInResult
    }

    override suspend fun signUp(email: String, password: String): AuthActionResult {
        signUpCalls += 1
        lastEmail = email
        return signUpResult
    }

    override suspend fun signOut(): AuthActionResult = AuthActionResult.Success

    override suspend fun requestPasswordReset(email: String): AuthActionResult {
        lastEmail = email
        return recoveryResult
    }

    override suspend fun updatePassword(password: String): AuthActionResult {
        updatePasswordCalls += 1
        return updatePasswordResult
    }

    override fun handleAuthCallback(
        callback: my.id.rakyzumusic.core.data.auth.AuthCallback,
    ) = Unit
}
