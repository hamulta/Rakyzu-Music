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

    fun createArtist(name: String) = runAction { repository.createArtist(name) }

    fun createAlbum(artistId: String, title: String, releaseDate: String?) = runAction {
        repository.createAlbum(artistId, title, releaseDate)
    }

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
    AdminFailure.PayloadTooLarge -> "The selected audio file is too large"
    AdminFailure.ServiceUnavailable -> "Admin services are temporarily unavailable"
}
