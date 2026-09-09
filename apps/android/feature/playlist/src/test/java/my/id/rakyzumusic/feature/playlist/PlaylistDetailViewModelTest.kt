package my.id.rakyzumusic.feature.playlist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import my.id.rakyzumusic.core.data.catalog.CatalogRefreshResult
import my.id.rakyzumusic.core.data.catalog.CatalogRepository
import my.id.rakyzumusic.core.data.playlist.*
import my.id.rakyzumusic.core.model.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    @Test fun optimisticReorderRollsBackAndLocksEditsOnConflict() = runTest(dispatcher) {
        val repo = Repository()
        val vm = PlaylistDetailViewModel("owner", "playlist", repo, Catalog)
        testScheduler.advanceUntilIdle()
        vm.move("one", 1)
        assertEquals(listOf("two", "one"), vm.uiState.value.orderedItems.map { it.trackId })
        vm.move("one", -1)
        testScheduler.runCurrent()
        assertEquals(1, repo.calls)
        repo.result.complete(PlaylistActionResult.Failure(PlaylistFailure.Conflict))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("one", "two"), vm.uiState.value.orderedItems.map { it.trackId })
        assertFalse(vm.uiState.value.canMutate)
        assertTrue(vm.uiState.value.message!!.contains("another device"))
        vm.refresh(); testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.canMutate)
    }

    @Test fun boundaryMovesAndDuplicateAddsDoNotIssueMutations() = runTest(dispatcher) {
        val repo = Repository()
        val vm = PlaylistDetailViewModel("owner", "playlist", repo, Catalog)
        testScheduler.advanceUntilIdle()
        vm.move("one", -1); vm.move("two", 1); vm.move("missing", 1); vm.add(ONE)
        testScheduler.advanceUntilIdle()
        assertEquals(0, repo.calls)
    }

    @Test fun editingDraftSurvivesFailedSaveAndBusyEditsAreIgnored() = runTest(dispatcher) {
        val repo = Repository()
        val vm = PlaylistDetailViewModel("owner", "playlist", repo, Catalog)
        testScheduler.advanceUntilIdle()
        vm.edit(); vm.updateName("Changed"); vm.saveMetadata(); vm.updateName("Lost")
        repo.result.complete(PlaylistActionResult.Failure(PlaylistFailure.NetworkUnavailable))
        testScheduler.advanceUntilIdle()
        assertEquals("Changed", vm.uiState.value.name)
        assertTrue(vm.uiState.value.editing)
    }

    @Test fun cachedPlaybackSkipsUnavailableItemsWithoutChangingOrder() {
        val state = PlaylistDetailUiState(detail = DETAIL.copy(items = listOf(PlaylistItem("gone", null)) + DETAIL.items))
        assertEquals(listOf(ONE, TWO), state.playableTracks)
        assertEquals(3, state.orderedItems.size)
        assertFalse(state.canMutate)
    }

    @Test fun metadataSaveUsesDraftRevisionEvenAfterAnotherSnapshotArrives() = runTest(dispatcher) {
        val repo = Repository()
        val vm = PlaylistDetailViewModel("owner", "playlist", repo, Catalog)
        testScheduler.advanceUntilIdle()
        vm.edit(); vm.updateName("My edit")
        repo.detail.value = DETAIL.copy(playlist = DETAIL.playlist.copy(revision = 9))
        testScheduler.runCurrent()
        vm.saveMetadata()
        testScheduler.runCurrent()
        assertEquals(1L, repo.revision)
        repo.result.complete(PlaylistActionResult.Failure(PlaylistFailure.Conflict))
        testScheduler.advanceUntilIdle()
        assertEquals("My edit", vm.uiState.value.name)
    }

    private class Repository : PlaylistRepository {
        val detail = MutableStateFlow<PlaylistDetail?>(DETAIL)
        val result = CompletableDeferred<PlaylistActionResult>()
        var calls = 0
        var revision = 0L
        override fun observeDetail(userId: String, playlistId: String) = detail
        override suspend fun refreshDetail(userId: String, playlistId: String) = PlaylistActionResult.Success()
        override suspend fun mutate(userId: String, playlistId: String, revision: Long, mutation: PlaylistMutation): PlaylistActionResult {
            calls++; this.revision = revision; return result.await()
        }
        override fun observePlaylists(userId: String) = MutableStateFlow(PlaylistSnapshot(emptyList(), null))
        override suspend fun refresh(userId: String) = PlaylistActionResult.Success()
        override suspend fun create(userId: String, name: String, description: String) = PlaylistActionResult.Success()
        override suspend fun artwork(userId: String, playlistId: String) = PlaylistArtworkResult.Absent
        override suspend fun updateArtwork(userId: String, playlistId: String, png: ByteArray?) = PlaylistArtworkResult.Updated
    }

    private object Catalog : CatalogRepository {
        private val snapshot = CatalogSnapshot(emptyList(), emptyList(), listOf(ONE, TWO), null)
        override fun observeCatalog() = MutableStateFlow(snapshot)
        override fun observeHomeFeed(userId: String) = MutableStateFlow(HomeFeedSnapshot(snapshot, emptyList()))
        override suspend fun refresh() = CatalogRefreshResult.Success(1000)
        override suspend fun recordRecentlyPlayed(userId: String, trackId: String) = true
    }

    companion object {
        private val ONE = Track("one", "One", "Artist", 2000)
        private val TWO = Track("two", "Two", "Artist", 2000)
        private val DETAIL = PlaylistDetail(PlaylistSummary("playlist", "Mix", "", 2, 1, 1000, 1000),
            listOf(PlaylistItem("one", ONE), PlaylistItem("two", TWO)))
    }
}
