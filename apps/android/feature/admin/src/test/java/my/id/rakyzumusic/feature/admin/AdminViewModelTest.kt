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
import my.id.rakyzumusic.core.data.admin.AdminAuditExportResult
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

        viewModel.createArtist("Rakyzu Original", null)
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

        override suspend fun createArtist(name: String, email: String?): AdminActionResult {
            artistName = name
            return AdminActionResult.Success("Artist draft created")
        }

        override suspend fun updateArtist(id: String, name: String, email: String?) = ok()
        override suspend fun archiveArtist(id: String) = ok()

        override suspend fun createAlbum(artistId: String, title: String, releaseDate: String?) = ok()
        override suspend fun updateAlbum(id: String, title: String, releaseDate: String?) = ok()
        override suspend fun archiveAlbum(id: String) = ok()
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
        override suspend fun enforceContent(
            subjectType: String, subjectId: String, action: String, reason: String, caseId: String?,
        ) = ok()
        override suspend fun assignCatalogTeam(
            scopeType: String, scopeId: String, email: String, accessLevel: String, active: Boolean,
        ) = ok()
        override suspend fun createCatalogLabel(name: String) = ok()
        override suspend fun linkCatalogLabelArtist(labelId: String, artistId: String) = ok()
        override suspend fun uploadArtwork(albumId: String, bytes: ByteArray) = ok()
        override suspend fun submitReview(reviewType: String, targetId: String, notes: String) = ok()
        override suspend fun decideReview(reviewId: String, decision: String, notes: String) = ok()
        override suspend fun scheduleAlbum(albumId: String, publishAt: String) = ok()
        override suspend fun exportAudit(operation: String?, targetType: String?) =
            AdminAuditExportResult.Success("id,operation,target_type,target_id,created_at")
        override suspend fun setAuditRetention(days: Int) = ok()
        override suspend fun upsertRecommendation(
            id: String?, title: String, subtitle: String?, position: Int,
            trackId: String?, published: Boolean,
        ) = ok()
        override suspend fun deleteRecommendation(id: String) = ok()
        override suspend fun uploadRecommendationArtwork(id: String, bytes: ByteArray) = ok()
        private fun ok() = AdminActionResult.Success("Updated")
    }
}
