package my.id.rakyzumusic.feature.profile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import my.id.rakyzumusic.core.data.artist.ArtistWorkspace
import my.id.rakyzumusic.core.data.artist.ArtistWorkspaceActionResult
import my.id.rakyzumusic.core.data.artist.ArtistWorkspaceRepository
import my.id.rakyzumusic.core.data.artist.ArtistWorkspaceResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArtistWorkspaceViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadsOnlyTheAuthenticatedArtistWorkspace() = runTest(dispatcher) {
        val repository = FakeArtistWorkspaceRepository()
        val viewModel = ArtistWorkspaceViewModel(repository)
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals("Rakyzu Artist", viewModel.state.value.workspace?.artistName)
    }

    @Test
    fun successfulDraftActionRefreshesAuthoritativeWorkspace() = runTest(dispatcher) {
        val repository = FakeArtistWorkspaceRepository()
        val viewModel = ArtistWorkspaceViewModel(repository)
        testScheduler.advanceUntilIdle()

        viewModel.createAlbum("New Release", null)
        testScheduler.advanceUntilIdle()

        assertEquals("artist-id", repository.createdForArtistId)
        assertEquals("New Release", repository.createdTitle)
        assertTrue(repository.loadCount >= 2)
        assertFalse(viewModel.state.value.messageIsError)
    }
}

private class FakeArtistWorkspaceRepository : ArtistWorkspaceRepository {
    var loadCount = 0
    var createdForArtistId: String? = null
    var createdTitle: String? = null

    override suspend fun load(): ArtistWorkspaceResult {
        loadCount += 1
        return ArtistWorkspaceResult.Success(
            ArtistWorkspace("artist-id", "Rakyzu Artist", false,
                emptyList(), emptyList(), emptyList(), emptyList()),
        )
    }

    override suspend fun createAlbum(
        artistId: String,
        title: String,
        releaseDate: String?,
    ): ArtistWorkspaceActionResult {
        createdForArtistId = artistId
        createdTitle = title
        return ArtistWorkspaceActionResult.Success("Album draft created")
    }

    override suspend fun assignTeam(email: String, accessLevel: String, active: Boolean) = success()
    override suspend fun createTrack(albumId: String, title: String, durationMs: Int,
        discNumber: Int, trackNumber: Int, explicit: Boolean) = success()
    override suspend fun uploadArtistArtwork(bytes: ByteArray) = success()
    override suspend fun uploadAlbumArtwork(albumId: String, bytes: ByteArray) = success()
    override suspend fun uploadTrackAudio(trackId: String, quality: String, bytes: ByteArray) = success()
    override suspend fun submitReview(type: String, albumId: String, notes: String) = success()

    private fun success() = ArtistWorkspaceActionResult.Success("Updated")
}
