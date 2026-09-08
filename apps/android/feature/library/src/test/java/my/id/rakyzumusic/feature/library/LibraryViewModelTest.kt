package my.id.rakyzumusic.feature.library

import androidx.lifecycle.SavedStateHandle
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
import my.id.rakyzumusic.core.data.network.ConnectivityMonitor
import my.id.rakyzumusic.core.model.LibraryItemKind
import my.id.rakyzumusic.core.model.Album
import my.id.rakyzumusic.core.model.Artist
import my.id.rakyzumusic.core.model.LibraryAlbum
import my.id.rakyzumusic.core.model.LibrarySnapshot
import my.id.rakyzumusic.core.model.Track
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

    @Test
    fun offlineMutationIsReportedAsQueuedInsteadOfAnError() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            mutationResult = LibraryActionResult.Queued(1)
        }
        val viewModel = LibraryViewModel("listener-1", repository)
        testScheduler.advanceUntilIdle()

        viewModel.setSaved(LibraryItemKind.Track, "track-1", true)
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.messageIsError)
        assertEquals(
            "Saved offline. 1 Library change is waiting to sync.",
            viewModel.uiState.value.message,
        )
    }

    @Test
    fun searchIsAccentCaseAndWhitespaceInsensitiveAcrossMetadata() = runTest(dispatcher) {
        val viewModel = LibraryViewModel("listener-1", FakeRepository(SEARCHABLE))
        testScheduler.advanceUntilIdle()

        viewModel.updateQuery("  cafe   sessions ")

        assertEquals(listOf("track-cafe"), viewModel.uiState.value.visibleLikedTracks.map(Track::id))
        assertTrue(viewModel.uiState.value.visibleSavedAlbums.isEmpty())
        assertTrue(viewModel.uiState.value.visibleFollowedArtists.isEmpty())
    }

    @Test
    fun queryFilterAndSortRestoreForTheListenerDestination() = runTest(dispatcher) {
        val savedState = SavedStateHandle(
            mapOf(
                SAVED_LIBRARY_QUERY_KEY to "signal",
                SAVED_LIBRARY_FILTER_KEY to LibraryFilter.Songs.name,
                SAVED_LIBRARY_SORT_KEY to LibrarySort.OldestAdded.name,
            ),
        )

        val viewModel = LibraryViewModel("listener-1", FakeRepository(ORDERED), savedState)
        testScheduler.advanceUntilIdle()

        assertEquals("signal", viewModel.uiState.value.query)
        assertEquals(LibraryFilter.Songs, viewModel.uiState.value.filter)
        assertEquals(LibrarySort.OldestAdded, viewModel.uiState.value.sort)
        assertEquals(listOf("track-old", "track-new"), viewModel.uiState.value.visibleLikedTracks.map(Track::id))
    }

    @Test
    fun likedSongsUsePersistedTimestampsAndDeterministicTieBreaks() = runTest(dispatcher) {
        val viewModel = LibraryViewModel("listener-1", FakeRepository(ORDERED))
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("track-new", "track-old"), viewModel.uiState.value.visibleLikedTracks.map(Track::id))

        viewModel.selectSort(LibrarySort.OldestAdded)
        assertEquals(listOf("track-old", "track-new"), viewModel.uiState.value.visibleLikedTracks.map(Track::id))

        viewModel.selectSort(LibrarySort.Alphabetical)
        assertEquals(listOf("track-old", "track-new"), viewModel.uiState.value.visibleLikedTracks.map(Track::id))
    }

    @Test
    fun transientRefreshUsesExactlyTwoBoundedRetries() = runTest(dispatcher) {
        val failure = LibraryActionResult.Failure(LibraryFailure.ServiceUnavailable)
        val repository = FakeRepository(
            initialRefreshResult = failure,
            additionalRefreshResults = listOf(failure, failure),
        )

        val viewModel = LibraryViewModel(
            userId = "listener-1",
            repository = repository,
            connectivityMonitor = FakeConnectivityMonitor(true),
        )
        testScheduler.advanceUntilIdle()

        assertEquals(3, repository.refreshCalls)
        assertFalse(viewModel.uiState.value.isRefreshing)
        assertTrue(viewModel.uiState.value.message?.contains("after retrying") == true)
    }

    @Test
    fun invalidPayloadIsNotRetried() = runTest(dispatcher) {
        val repository = FakeRepository(
            initialRefreshResult = LibraryActionResult.Failure(LibraryFailure.InvalidPayload),
        )

        val viewModel = LibraryViewModel(
            userId = "listener-1",
            repository = repository,
            connectivityMonitor = FakeConnectivityMonitor(true),
        )
        testScheduler.advanceUntilIdle()

        assertEquals(1, repository.refreshCalls)
        assertEquals(
            "The latest Library update could not be verified.",
            viewModel.uiState.value.message,
        )
    }

    @Test
    fun offlineFailureRecoversOnceWhenValidatedConnectivityReturns() = runTest(dispatcher) {
        val offlineFailure = LibraryActionResult.Failure(LibraryFailure.NetworkUnavailable)
        val repository = FakeRepository(
            initialRefreshResult = offlineFailure,
            additionalRefreshResults = listOf(LibraryActionResult.Success),
        )
        val connectivity = FakeConnectivityMonitor(false)
        val viewModel = LibraryViewModel(
            userId = "listener-1",
            repository = repository,
            connectivityMonitor = connectivity,
        )
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isWaitingForConnection)
        assertFalse(viewModel.uiState.value.messageIsError)
        assertEquals(1, repository.refreshCalls)

        connectivity.online.value = true
        testScheduler.advanceUntilIdle()

        assertEquals(2, repository.refreshCalls)
        assertFalse(viewModel.uiState.value.isWaitingForConnection)
        assertEquals(null, viewModel.uiState.value.message)

        connectivity.online.value = true
        testScheduler.advanceUntilIdle()
        assertEquals(2, repository.refreshCalls)
    }

    @Test
    fun connectivityLossDuringBackoffStopsRetryAndRecoversOnce() = runTest(dispatcher) {
        val repository = FakeRepository(
            initialRefreshResult = LibraryActionResult.Failure(
                LibraryFailure.ServiceUnavailable,
            ),
            additionalRefreshResults = listOf(LibraryActionResult.Success),
        )
        val connectivity = FakeConnectivityMonitor(true)
        val viewModel = LibraryViewModel(
            userId = "listener-1",
            repository = repository,
            connectivityMonitor = connectivity,
            retryDelaysMillis = listOf(1_000L),
        )
        testScheduler.runCurrent()

        connectivity.online.value = false
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(1_000L)
        testScheduler.runCurrent()

        assertEquals(1, repository.refreshCalls)
        assertTrue(viewModel.uiState.value.isWaitingForConnection)

        connectivity.online.value = true
        testScheduler.advanceUntilIdle()

        assertEquals(2, repository.refreshCalls)
        assertFalse(viewModel.uiState.value.isWaitingForConnection)
    }

    @Test
    fun queuedOfflineMutationTriggersRecoveryRefresh() = runTest(dispatcher) {
        val connectivity = FakeConnectivityMonitor(false)
        val repository = FakeRepository().apply {
            mutationResult = LibraryActionResult.Queued(1)
        }
        val viewModel = LibraryViewModel(
            userId = "listener-1",
            repository = repository,
            connectivityMonitor = connectivity,
        )
        testScheduler.advanceUntilIdle()

        viewModel.setSaved(LibraryItemKind.Track, "track-1", true)
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isWaitingForConnection)
        assertEquals(1, repository.refreshCalls)

        connectivity.online.value = true
        testScheduler.advanceUntilIdle()

        assertEquals(2, repository.refreshCalls)
        assertFalse(viewModel.uiState.value.isWaitingForConnection)
    }

    @Test
    fun concurrentRefreshIsCoalescedAndDiagnosed() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeRepository(refreshGate = gate)
        val diagnostics = RecordingLibraryDiagnostics()
        val viewModel = LibraryViewModel(
            userId = "listener-1",
            repository = repository,
            diagnostics = diagnostics,
        )
        testScheduler.runCurrent()

        viewModel.refresh()

        assertEquals(1, repository.refreshCalls)
        assertTrue(
            diagnostics.events.contains(
                LibraryDiagnosticEvent.RefreshCoalesced(LibraryRefreshTrigger.Manual),
            ),
        )
        gate.complete(Unit)
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun freshnessUsesPersistedSyncTimeAndStalesAfterOneDay() = runTest(dispatcher) {
        val now = 1_800_000_000_000L
        val staleTime = now - (24L * 60L * 60L * 1_000L)
        val snapshot = CACHED.copy(lastSyncedAtEpochMillis = staleTime)
        val viewModel = LibraryViewModel(
            userId = "listener-1",
            repository = FakeRepository(snapshot = snapshot),
            currentTimeMillis = { now },
        )
        testScheduler.advanceUntilIdle()

        assertEquals("Updated 1 day ago", viewModel.uiState.value.freshness.label)
        assertTrue(viewModel.uiState.value.freshness.isStale)
    }

    @Test(timeout = 2_000L)
    fun largeLibraryProjectionKeepsDeterministicOrder() {
        val tracks = (0 until 5_000).map { index ->
            Track(
                id = "track-$index",
                title = "Signal $index",
                artist = "Rakyzu Sessions",
                durationMs = 180_000L,
            )
        }
        val snapshot = CACHED.copy(likedTracks = tracks)

        val content = deriveLibraryVisibleContent(
            library = snapshot,
            query = "rakyzu signal",
            filter = LibraryFilter.Songs,
            sort = LibrarySort.Alphabetical,
        )

        assertEquals(5_000, content.likedTracks.size)
        assertEquals("track-0", content.likedTracks.first().id)
        assertEquals("track-999", content.likedTracks.last().id)
    }

    private class FakeRepository(
        private val refreshGate: CompletableDeferred<Unit>? = null,
        snapshot: LibrarySnapshot = CACHED,
        initialRefreshResult: LibraryActionResult = LibraryActionResult.Success,
        additionalRefreshResults: List<LibraryActionResult> = emptyList(),
    ) : LibraryRepository {
        constructor(snapshot: LibrarySnapshot) : this(refreshGate = null, snapshot = snapshot)

        private val library = MutableStateFlow(snapshot)
        private val refreshResults = ArrayDeque(
            listOf(initialRefreshResult) + additionalRefreshResults,
        )
        var mutationResult: LibraryActionResult = LibraryActionResult.Success
        var lastMutation: Mutation? = null
        var mutationCalls = 0
        var refreshCalls = 0

        override fun observeLibrary(userId: String): Flow<LibrarySnapshot> = library

        override suspend fun refresh(userId: String): LibraryActionResult {
            refreshCalls += 1
            refreshGate?.await()
            return if (refreshResults.size > 1) {
                refreshResults.removeFirst()
            } else {
                refreshResults.first()
            }
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

    private class FakeConnectivityMonitor(initiallyOnline: Boolean) : ConnectivityMonitor {
        val online = MutableStateFlow(initiallyOnline)

        override val isOnline: Flow<Boolean> = online

        override fun isCurrentlyOnline(): Boolean = online.value
    }

    private class RecordingLibraryDiagnostics : LibraryDiagnosticSink {
        val events = mutableListOf<LibraryDiagnosticEvent>()

        override fun record(event: LibraryDiagnosticEvent) {
            events += event
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
        val OLD_TRACK = Track(
            id = "track-old",
            title = "Alpha Signal",
            artist = "Rakyzu Sessions",
            durationMs = 180_000L,
            artistId = "artist-1",
            albumId = "album-1",
            albumTitle = "Signal Zero",
        )
        val NEW_TRACK = Track(
            id = "track-new",
            title = "Beta Signal",
            artist = "Rakyzu Sessions",
            durationMs = 190_000L,
            artistId = "artist-1",
            albumId = "album-1",
            albumTitle = "Signal Zero",
        )
        val ORDERED = LibrarySnapshot(
            likedTracks = listOf(OLD_TRACK, NEW_TRACK),
            savedAlbums = emptyList(),
            followedArtists = emptyList(),
            lastSyncedAtEpochMillis = 42L,
            likedTrackSavedAtEpochMillis = mapOf("track-old" to 10L, "track-new" to 20L),
        )
        val SEARCHABLE = LibrarySnapshot(
            likedTracks = listOf(
                Track(
                    id = "track-cafe",
                    title = "Café Signal",
                    artist = "Rakyzu Sessions",
                    durationMs = 180_000L,
                ),
                Track("track-other", "Other", "Elsewhere", 100_000L),
            ),
            savedAlbums = listOf(
                LibraryAlbum(Album("album-1", "artist-1", "Quiet Room", null), "Elsewhere"),
            ),
            followedArtists = listOf(Artist("artist-1", "Elsewhere")),
            lastSyncedAtEpochMillis = 42L,
        )
    }
}
