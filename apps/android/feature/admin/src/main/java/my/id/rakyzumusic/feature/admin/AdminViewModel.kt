package my.id.rakyzumusic.feature.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import my.id.rakyzumusic.core.data.admin.AdminActionResult
import my.id.rakyzumusic.core.data.admin.AdminAuditExportResult
import my.id.rakyzumusic.core.data.admin.AdminDashboard
import my.id.rakyzumusic.core.data.admin.AdminDashboardResult
import my.id.rakyzumusic.core.data.admin.AdminFailure
import my.id.rakyzumusic.core.data.admin.AdminRepository
import my.id.rakyzumusic.core.data.admin.StaffRole

data class AdminUiState(
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val dashboard: AdminDashboard? = null,
    val message: String? = null,
    val messageIsError: Boolean = false,
    val auditExportCsv: String? = null,
)

class AdminViewModel(
    private val repository: AdminRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = mutableUiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isLoading = true, message = null) }
            when (val result = repository.loadDashboard()) {
                is AdminDashboardResult.Success -> mutableUiState.update {
                    it.copy(isLoading = false, dashboard = result.dashboard)
                }
                AdminDashboardResult.NotAuthenticated -> failure("Sign in again to access staff tools")
                AdminDashboardResult.Unavailable -> failure("Admin services are temporarily unavailable")
            }
        }
    }

    fun createArtist(name: String, email: String?) = runAction { repository.createArtist(name, email) }

    fun updateArtist(id: String, name: String, email: String?) = runAction {
        repository.updateArtist(id, name, email)
    }

    fun archiveArtist(id: String) = runAction { repository.archiveArtist(id) }

    fun createAlbum(artistId: String, title: String, releaseDate: String?) = runAction {
        repository.createAlbum(artistId, title, releaseDate)
    }

    fun updateAlbum(id: String, title: String, releaseDate: String?) = runAction {
        repository.updateAlbum(id, title, releaseDate)
    }

    fun archiveAlbum(id: String) = runAction { repository.archiveAlbum(id) }

    fun createTrack(
        albumId: String,
        title: String,
        durationMs: Int,
        discNumber: Int,
        trackNumber: Int,
        explicit: Boolean,
    ) = runAction {
        repository.createTrack(albumId, title, durationMs, discNumber, trackNumber, explicit)
    }

    fun uploadAudio(trackId: String, quality: String, bytes: ByteArray) = runAction {
        repository.uploadAudio(trackId, quality, bytes)
    }

    fun publishAlbum(albumId: String) = runAction { repository.publishAlbum(albumId) }

    fun createModerationCase(
        subjectType: String,
        subjectId: String,
        reason: String,
        priority: Int,
    ) = runAction {
        repository.createModerationCase(subjectType, subjectId, reason, priority)
    }

    fun moderate(caseId: String, action: String, notes: String) = runAction {
        repository.moderate(caseId, action, notes)
    }

    fun assignStaff(userId: String, role: StaffRole, active: Boolean) = runAction {
        repository.assignStaff(userId, role, active)
    }

    fun enforceContent(
        subjectType: String,
        subjectId: String,
        action: String,
        reason: String,
        caseId: String?,
    ) = runAction { repository.enforceContent(subjectType, subjectId, action, reason, caseId) }

    fun assignCatalogTeam(
        scopeType: String,
        scopeId: String,
        email: String,
        accessLevel: String,
        active: Boolean,
    ) = runAction {
        repository.assignCatalogTeam(scopeType, scopeId, email, accessLevel, active)
    }

    fun createCatalogLabel(name: String) = runAction { repository.createCatalogLabel(name) }

    fun linkCatalogLabelArtist(labelId: String, artistId: String) = runAction {
        repository.linkCatalogLabelArtist(labelId, artistId)
    }

    fun uploadArtwork(albumId: String, bytes: ByteArray) = runAction {
        repository.uploadArtwork(albumId, bytes)
    }

    fun submitReview(reviewType: String, targetId: String, notes: String) = runAction {
        repository.submitReview(reviewType, targetId, notes)
    }

    fun decideReview(reviewId: String, decision: String, notes: String) = runAction {
        repository.decideReview(reviewId, decision, notes)
    }

    fun scheduleAlbum(albumId: String, publishAt: String) = runAction {
        repository.scheduleAlbum(albumId, publishAt)
    }

    fun setAuditRetention(days: Int) = runAction { repository.setAuditRetention(days) }

    fun upsertRecommendation(
        id: String?, title: String, subtitle: String?, position: Int,
        trackId: String?, published: Boolean,
    ) = runAction {
        repository.upsertRecommendation(id, title, subtitle, position, trackId, published)
    }

    fun deleteRecommendation(id: String) = runAction { repository.deleteRecommendation(id) }

    fun uploadRecommendationArtwork(id: String, bytes: ByteArray) = runAction {
        repository.uploadRecommendationArtwork(id, bytes)
    }

    fun enforceAccount(userId: String, action: String, reason: String, expiresAt: String?) = runAction {
        repository.enforceAccount(userId, action, reason, expiresAt)
    }

    fun decideAppeal(id: String, decision: String, notes: String) = runAction {
        repository.decideAppeal(id, decision, notes)
    }

    fun approveDeletion(id: String) = runAction { repository.approveDeletion(id) }

    fun acknowledgeSecurityAlert(id: String, resolved: Boolean) = runAction {
        repository.acknowledgeSecurityAlert(id, resolved)
    }

    fun exportAudit(operation: String?, targetType: String?) {
        if (mutableUiState.value.isWorking) return
        viewModelScope.launch {
            mutableUiState.update { it.copy(isWorking = true, message = null, auditExportCsv = null) }
            when (val result = repository.exportAudit(operation, targetType)) {
                is AdminAuditExportResult.Success -> mutableUiState.update {
                    it.copy(
                        isWorking = false,
                        message = "Audit export ready",
                        messageIsError = false,
                        auditExportCsv = result.csv,
                    )
                }
                is AdminAuditExportResult.Failure -> mutableUiState.update {
                    it.copy(
                        isWorking = false,
                        message = result.reason.toMessage(),
                        messageIsError = true,
                    )
                }
            }
        }
    }

    fun consumeAuditExport() {
        mutableUiState.update { it.copy(auditExportCsv = null) }
    }

    private fun runAction(block: suspend () -> AdminActionResult) {
        if (mutableUiState.value.isWorking) return
        viewModelScope.launch {
            mutableUiState.update { it.copy(isWorking = true, message = null) }
            val result = try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                AdminActionResult.Failure(AdminFailure.ServiceUnavailable)
            }
            when (result) {
                is AdminActionResult.Success -> {
                    mutableUiState.update {
                        it.copy(isWorking = false, message = result.message, messageIsError = false)
                    }
                    refreshAfterAction(result.message)
                }
                is AdminActionResult.Failure -> mutableUiState.update {
                    it.copy(
                        isWorking = false,
                        message = result.reason.toMessage(),
                        messageIsError = true,
                    )
                }
            }
        }
    }

    private suspend fun refreshAfterAction(message: String) {
        when (val result = repository.loadDashboard()) {
            is AdminDashboardResult.Success -> mutableUiState.update {
                it.copy(
                    isLoading = false,
                    dashboard = result.dashboard,
                    message = message,
                    messageIsError = false,
                )
            }
            else -> Unit
        }
    }

    private fun failure(message: String) {
        mutableUiState.update {
            it.copy(isLoading = false, isWorking = false, message = message, messageIsError = true)
        }
    }

    companion object {
        fun factory(repository: AdminRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AdminViewModel(repository) as T
            }
    }
}

private fun AdminFailure.toMessage(): String = when (this) {
    AdminFailure.InvalidInput -> "Check the supplied IDs and metadata"
    AdminFailure.NotAuthenticated -> "Sign in again to continue"
    AdminFailure.Forbidden -> "Your role cannot perform this action"
    AdminFailure.PayloadTooLarge -> "The selected media file is too large"
    AdminFailure.ServiceUnavailable -> "Admin services are temporarily unavailable"
}
