package my.id.rakyzumusic.feature.admin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import my.id.rakyzumusic.core.data.admin.AdminActionResult
import my.id.rakyzumusic.core.data.admin.AdminDashboard
import my.id.rakyzumusic.core.data.admin.AdminDashboardResult
import my.id.rakyzumusic.core.data.admin.AdminRepository
import my.id.rakyzumusic.core.data.admin.StaffAccessContext
import my.id.rakyzumusic.core.data.admin.StaffPermission
import my.id.rakyzumusic.core.data.admin.StaffRole
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `server staff context loads before privileged controls`() = runTest(dispatcher) {
        val repository = FakeAdminRepository()
        val viewModel = AdminViewModel(repository)

        testScheduler.advanceUntilIdle()

        assertEquals(StaffRole.Manager, viewModel.uiState.value.dashboard?.context?.role)
        assertEquals(1, repository.dashboardLoads)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `successful action refreshes server-authoritative dashboard`() = runTest(dispatcher) {
        val repository = FakeAdminRepository()
        val viewModel = AdminViewModel(repository)
        testScheduler.advanceUntilIdle()

        viewModel.createArtist("Rakyzu Original")
        testScheduler.advanceUntilIdle()

        assertEquals("Rakyzu Original", repository.artistName)
        assertEquals(2, repository.dashboardLoads)
        assertEquals("Artist draft created", viewModel.uiState.value.message)
    }

    private class FakeAdminRepository : AdminRepository {
        var dashboardLoads = 0
        var artistName: String? = null

        override suspend fun loadDashboard(): AdminDashboardResult {
            dashboardLoads += 1
            return AdminDashboardResult.Success(
                AdminDashboard(
                    context = StaffAccessContext(
                        isStaff = true,
                        role = StaffRole.Manager,
                        displayRole = "Manager",
                        fullAccess = false,
                        permissions = setOf(
                            StaffPermission.AdminAccess,
                            StaffPermission.CatalogDraft,
                        ),
                    ),
                ),
            )
        }

        override suspend fun createArtist(name: String): AdminActionResult {
            artistName = name
            return AdminActionResult.Success("Artist draft created")
        }

        override suspend fun createAlbum(artistId: String, title: String, releaseDate: String?) = ok()
        override suspend fun createTrack(
            albumId: String, title: String, durationMs: Int, discNumber: Int,
            trackNumber: Int, explicit: Boolean,
        ) = ok()
        override suspend fun uploadAudio(trackId: String, quality: String, bytes: ByteArray) = ok()
        override suspend fun publishAlbum(albumId: String) = ok()
        override suspend fun createModerationCase(
            subjectType: String, subjectId: String, reason: String, priority: Int,
        ) = ok()
        override suspend fun moderate(caseId: String, action: String, notes: String) = ok()
        override suspend fun assignStaff(email: String, role: StaffRole, active: Boolean) = ok()
        private fun ok() = AdminActionResult.Success("Updated")
    }
}
