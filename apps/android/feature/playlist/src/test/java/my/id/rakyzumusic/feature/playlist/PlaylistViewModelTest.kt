package my.id.rakyzumusic.feature.playlist

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import my.id.rakyzumusic.core.data.playlist.PlaylistActionResult
import my.id.rakyzumusic.core.data.playlist.PlaylistRepository
import my.id.rakyzumusic.core.model.PlaylistSnapshot
import my.id.rakyzumusic.core.model.PlaylistSummary
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun cachedPlaylistsRemainVisibleAfterRefresh() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = PlaylistViewModel("listener-1", repository)

        testScheduler.advanceUntilIdle()

        assertEquals(listOf(PLAYLIST), viewModel.uiState.value.playlists)
        assertEquals(1, repository.refreshCalls)
        assertTrue(viewModel.uiState.value.hasObservedPlaylists)
    }

    @Test
    fun successfulCreateClearsRestorableDraft() = runTest(dispatcher) {
        val repository = FakeRepository()
        val savedState = SavedStateHandle()
        val viewModel = PlaylistViewModel("listener-1", repository, savedState)
        testScheduler.advanceUntilIdle()
        viewModel.updateName("Road Trip")
        viewModel.updateDescription("Coast")

        viewModel.create()
        testScheduler.advanceUntilIdle()

        assertEquals("Road Trip", repository.createdName)
        assertEquals("", viewModel.uiState.value.name)
        assertEquals("Playlist created.", viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.messageIsError)
    }

    private class FakeRepository : PlaylistRepository {
        private val snapshot = MutableStateFlow(PlaylistSnapshot(listOf(PLAYLIST), 1_000L))
        var refreshCalls = 0
        var createdName: String? = null

        override fun observePlaylists(userId: String): Flow<PlaylistSnapshot> = snapshot

        override suspend fun refresh(userId: String): PlaylistActionResult {
            refreshCalls += 1
            return PlaylistActionResult.Success()
        }

        override suspend fun create(
            userId: String,
            name: String,
            description: String,
        ): PlaylistActionResult {
            createdName = name
            return PlaylistActionResult.Success(PLAYLIST)
        }
    }

    private companion object {
        val PLAYLIST = PlaylistSummary(
            id = "90000000-0000-4000-8000-000000000001",
            name = "Road Trip",
            description = "Coast",
            trackCount = 0,
            revision = 1,
            createdAtEpochMillis = 1_000L,
            updatedAtEpochMillis = 1_000L,
        )
    }
}
