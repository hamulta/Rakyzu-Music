package my.id.rakyzumusic.feature.library

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import my.id.rakyzumusic.core.data.library.LibraryActionResult
import my.id.rakyzumusic.core.data.library.LibraryFailure
import my.id.rakyzumusic.core.data.library.LibraryRepository
import my.id.rakyzumusic.core.model.LibraryItemKind
import my.id.rakyzumusic.core.model.LibrarySnapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun observesCachedLibraryBeforeRefreshCompletes() = runTest(dispatcher) {
        val refreshGate = CompletableDeferred<Unit>()
        val repository = FakeRepository(refreshGate)
        val viewModel = LibraryViewModel("listener-1", repository)
        testScheduler.runCurrent()

        assertTrue(viewModel.uiState.value.hasObservedLibrary)
        assertEquals(CACHED, viewModel.uiState.value.library)
        assertTrue(viewModel.uiState.value.isRefreshing)

        refreshGate.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun mutationUsesAccountKindAndDesiredState() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = LibraryViewModel("listener-1", repository)
        testScheduler.advanceUntilIdle()

        viewModel.setSaved(LibraryItemKind.Album, "album-1", true)
        testScheduler.advanceUntilIdle()

        assertEquals(
            Mutation("listener-1", LibraryItemKind.Album, "album-1", true),
            repository.lastMutation,
        )
        assertEquals("Album saved.", viewModel.uiState.value.message)
    }

    @Test
    fun duplicatePendingMutationIsSuppressed() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = LibraryViewModel("listener-1", repository)
        testScheduler.advanceUntilIdle()

        viewModel.setSaved(LibraryItemKind.Track, "track-1", true)
        viewModel.setSaved(LibraryItemKind.Track, "track-1", false)
        testScheduler.advanceUntilIdle()

        assertEquals(1, repository.mutationCalls)
    }

    @Test
    fun failedMutationExposesAccessibleMessageWithoutChangingSnapshot() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            mutationResult = LibraryActionResult.Failure(LibraryFailure.NetworkUnavailable)
        }
        val viewModel = LibraryViewModel("listener-1", repository)
        testScheduler.advanceUntilIdle()

        viewModel.setSaved(LibraryItemKind.Artist, "artist-1", false)
        testScheduler.advanceUntilIdle()

        assertEquals(CACHED, viewModel.uiState.value.library)
        assertTrue(viewModel.uiState.value.messageIsError)
        assertEquals(
            "Library sync needs an internet connection.",
            viewModel.uiState.value.message,
        )
    }

    private class FakeRepository(
        private val refreshGate: CompletableDeferred<Unit>? = null,
    ) : LibraryRepository {
        private val library = MutableStateFlow(CACHED)
        var refreshResult: LibraryActionResult = LibraryActionResult.Success
        var mutationResult: LibraryActionResult = LibraryActionResult.Success
        var lastMutation: Mutation? = null
        var mutationCalls = 0

        override fun observeLibrary(userId: String): Flow<LibrarySnapshot> = library

        override suspend fun refresh(userId: String): LibraryActionResult {
            refreshGate?.await()
            return refreshResult
        }

        override suspend fun setSaved(
            userId: String,
            kind: LibraryItemKind,
            itemId: String,
            saved: Boolean,
        ): LibraryActionResult {
            mutationCalls += 1
            lastMutation = Mutation(userId, kind, itemId, saved)
            return mutationResult
        }
    }

    private data class Mutation(
        val userId: String,
        val kind: LibraryItemKind,
        val itemId: String,
        val saved: Boolean,
    )

    private companion object {
        val CACHED = LibrarySnapshot(emptyList(), emptyList(), emptyList(), 42L)
    }
}
