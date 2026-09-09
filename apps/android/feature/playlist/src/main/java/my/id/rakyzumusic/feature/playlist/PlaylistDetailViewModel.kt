package my.id.rakyzumusic.feature.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import my.id.rakyzumusic.core.data.catalog.CatalogRepository
import my.id.rakyzumusic.core.data.playlist.PlaylistActionResult
import my.id.rakyzumusic.core.data.playlist.PlaylistArtworkResult
import my.id.rakyzumusic.core.data.playlist.PlaylistMutation
import my.id.rakyzumusic.core.data.playlist.PlaylistRepository
import my.id.rakyzumusic.core.model.PlaylistDetail
import my.id.rakyzumusic.core.model.PlaylistItem
import my.id.rakyzumusic.core.model.Track

data class PlaylistDetailUiState(
    val detail: PlaylistDetail? = null,
    val catalogTracks: List<Track> = emptyList(),
    val busy: Boolean = false,
    val verified: Boolean = false,
    val pendingOrder: List<String>? = null,
    val message: String? = null,
    val editing: Boolean = false,
    val name: String = "",
    val description: String = "",
    val artwork: ByteArray? = null,
    val artworkBusy: Boolean = false,
) {
    val canMutate: Boolean get() = detail != null && verified && !busy
    val orderedItems: List<PlaylistItem> get() {
        val items = detail?.items.orEmpty()
        val order = pendingOrder ?: return items
        val byId = items.associateBy { it.trackId }
        return if (order.toSet() == byId.keys) order.map { byId.getValue(it) } else items
    }
    val playableTracks: List<Track> get() = orderedItems.mapNotNull { it.track }
}

class PlaylistDetailViewModel internal constructor(
    private val userId: String,
    private val playlistId: String,
    private val repository: PlaylistRepository,
    catalog: CatalogRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(PlaylistDetailUiState(
        editing = savedState["editing"] ?: false,
        name = savedState.get<String>("name").orEmpty().take(100),
        description = savedState.get<String>("description").orEmpty().take(300),
    ))
    val uiState = mutableState.asStateFlow()

    init {
        viewModelScope.launch { repository.observeDetail(userId, playlistId).collect { detail ->
            mutableState.update { it.copy(detail = detail) }
        } }
        viewModelScope.launch { catalog.observeCatalog().collect { snapshot ->
            mutableState.update { it.copy(catalogTracks = snapshot.tracks) }
        } }
        refresh()
        loadArtwork()
    }

    fun refresh() {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val result = repository.refreshDetail(userId, playlistId)
            mutableState.update { it.copy(busy = false, verified = result is PlaylistActionResult.Success,
                message = (result as? PlaylistActionResult.Failure)?.reason?.toMessage(it.detail != null)) }
        }
    }

    fun add(track: Track) {
        if (mutableState.value.detail?.items?.any { it.trackId == track.id } == true) return
        mutate(PlaylistMutation.Add(track.id))
    }

    fun remove(trackId: String) = mutate(PlaylistMutation.Remove(trackId))

    fun move(trackId: String, delta: Int) {
        val state = mutableState.value
        if (!state.canMutate || delta !in listOf(-1, 1)) return
        val ids = state.orderedItems.map { it.trackId }.toMutableList()
        val from = ids.indexOf(trackId)
        val to = from + delta
        if (from < 0 || to !in ids.indices) return
        ids.add(to, ids.removeAt(from))
        mutate(PlaylistMutation.Reorder(ids), ids)
    }

    fun edit() {
        val detail = mutableState.value.detail ?: return
        if (!mutableState.value.canMutate) return
        savedState["editing"] = true
        savedState["editing-revision"] = detail.playlist.revision
        updateName(detail.playlist.name)
        updateDescription(detail.playlist.description)
        mutableState.update { it.copy(editing = true) }
    }

    fun cancelEdit() {
        if (mutableState.value.busy) return
        savedState["editing"] = false
        mutableState.update { it.copy(editing = false) }
    }

    fun updateName(value: String) {
        if (mutableState.value.busy) return
        savedState["name"] = value.take(100)
        mutableState.update { it.copy(name = value.take(100)) }
    }

    fun updateDescription(value: String) {
        if (mutableState.value.busy) return
        savedState["description"] = value.take(300)
        mutableState.update { it.copy(description = value.take(300)) }
    }

    fun saveMetadata() {
        val state = mutableState.value
        if (!state.editing || state.name.isBlank()) return
        mutate(PlaylistMutation.Metadata(state.name, state.description))
    }

    private fun mutate(mutation: PlaylistMutation, pendingOrder: List<String>? = null) {
        val state = mutableState.value
        val detail = state.detail ?: return
        if (!state.canMutate) return
        val expectedRevision = if (mutation is PlaylistMutation.Metadata) {
            savedState.get<Long>("editing-revision") ?: return
        } else detail.playlist.revision
        mutableState.update { it.copy(busy = true, pendingOrder = pendingOrder, message = null) }
        viewModelScope.launch {
            val result = repository.mutate(userId, playlistId, expectedRevision, mutation)
            val success = result is PlaylistActionResult.Success
            if (success && mutation is PlaylistMutation.Metadata) savedState["editing"] = false
            mutableState.update { it.copy(busy = false, pendingOrder = null,
                // A failed/ambiguous write must be reconciled before the next mutation.
                verified = success,
                editing = if (success && mutation is PlaylistMutation.Metadata) false else it.editing,
                message = if (success) "Playlist updated." else
                    (result as PlaylistActionResult.Failure).reason.toMessage(true)) }
        }
    }

    fun artworkError() { mutableState.update { it.copy(message = "Cover could not be read. Choose another image.") } }

    fun loadArtwork() {
        if (mutableState.value.artworkBusy) return
        mutableState.update { it.copy(artworkBusy = true) }
        viewModelScope.launch {
            val result = repository.artwork(userId, playlistId)
            mutableState.update { it.copy(artworkBusy = false,
                artwork = (result as? PlaylistArtworkResult.Image)?.bytes,
                message = if (result == PlaylistArtworkResult.Failed) "Cover unavailable. Retry cover when connected." else it.message) }
        }
    }

    fun updateArtwork(png: ByteArray?) {
        if (mutableState.value.artworkBusy || !mutableState.value.canMutate) return
        mutableState.update { it.copy(artworkBusy = true) }
        viewModelScope.launch {
            val result = repository.updateArtwork(userId, playlistId, png)
            val success = result == PlaylistArtworkResult.Updated
            mutableState.update { it.copy(artworkBusy = false,
                artwork = if (success) png else it.artwork,
                message = if (success) "Cover updated." else "Cover update failed. Retry cover before trying again.") }
        }
    }

    companion object {
        fun factory(userId: String, playlistId: String, repository: PlaylistRepository, catalog: CatalogRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                    PlaylistDetailViewModel(userId, playlistId, repository, catalog, extras.createSavedStateHandle()) as T
            }
    }
}
