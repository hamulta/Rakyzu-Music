package my.id.rakyzumusic.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import my.id.rakyzumusic.core.data.catalog.CatalogRefreshFailure
import my.id.rakyzumusic.core.data.catalog.CatalogRefreshResult
import my.id.rakyzumusic.core.data.catalog.CatalogRepository
import my.id.rakyzumusic.core.model.CatalogSnapshot

data class HomeUiState(
    val catalog: CatalogSnapshot = EMPTY_CATALOG,
    val isRefreshing: Boolean = true,
    val refreshMessage: String? = null,
) {
    val isShowingSavedCatalog: Boolean
        get() = refreshMessage != null && !catalog.isEmpty
}

class HomeViewModel(
    private val repository: CatalogRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = mutableUiState.asStateFlow()
    private var refreshInProgress = false

    init {
        viewModelScope.launch {
            repository.observeCatalog().collect { catalog ->
                mutableUiState.update { it.copy(catalog = catalog) }
            }
        }
        refresh()
    }

    fun refresh() {
        if (refreshInProgress) return
        refreshInProgress = true
        mutableUiState.update { it.copy(isRefreshing = true, refreshMessage = null) }
        viewModelScope.launch {
            when (val result = repository.refresh()) {
                is CatalogRefreshResult.Success -> mutableUiState.update {
                    it.copy(isRefreshing = false, refreshMessage = null)
                }
                is CatalogRefreshResult.Failure -> mutableUiState.update {
                    it.copy(
                        isRefreshing = false,
                        refreshMessage = result.reason.toSafeMessage(),
                    )
                }
            }
            refreshInProgress = false
        }
    }

    companion object {
        fun factory(repository: CatalogRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(HomeViewModel::class.java))
                    return HomeViewModel(repository) as T
                }
            }
    }
}

private fun CatalogRefreshFailure.toSafeMessage(): String = when (this) {
    CatalogRefreshFailure.NetworkUnavailable -> "You're offline. Check your connection and try again."
    CatalogRefreshFailure.ServiceUnavailable -> "The Rakyzu catalog is temporarily unavailable."
    CatalogRefreshFailure.InvalidPayload -> "The latest catalog update could not be verified."
}

private val EMPTY_CATALOG = CatalogSnapshot(
    artists = emptyList(),
    albums = emptyList(),
    tracks = emptyList(),
    lastSyncedAtEpochMillis = null,
)
