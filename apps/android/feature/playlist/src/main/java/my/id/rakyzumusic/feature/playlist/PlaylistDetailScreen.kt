package my.id.rakyzumusic.feature.playlist

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import my.id.rakyzumusic.core.model.Track
import my.id.rakyzumusic.core.model.PlaylistRole
import my.id.rakyzumusic.core.model.PlaylistVisibility

@Composable
fun PlaylistDetailRoute(viewModel: PlaylistDetailViewModel, onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.contentResolver
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preparingArtwork by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            preparingArtwork = true
            scope.launch {
                try {
                    val png = preparePlaylistArtwork(resolver, uri)
                    if (png == null) viewModel.artworkError() else viewModel.updateArtwork(png)
                } finally { preparingArtwork = false }
            }
        }
    }
    PlaylistDetailScreen(state, onBack, onPlay, viewModel::refresh, viewModel::add, viewModel::remove,
        viewModel::move, viewModel::edit, viewModel::cancelEdit, viewModel::updateName,
        viewModel::updateDescription, viewModel::saveMetadata,
        onLoadMore = viewModel::loadMore, onRetryPending = viewModel::retryPending,
        onInviteRole = viewModel::selectInviteRole, onCreateInvite = viewModel::createInvite,
        onShareInvite = { link ->
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, link)
                    },
                    "Share Rakyzu Music invite",
                ),
            )
        },
        onRemoveMember = viewModel::removeMember, onLeave = viewModel::leave,
        onVisibility = viewModel::setVisibility, onFollowing = viewModel::setFollowing,
        onToggleDiagnostics = viewModel::toggleDiagnostics,
        onChooseArtwork = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onRemoveArtwork = { viewModel.updateArtwork(null) }, onRetryArtwork = viewModel::loadArtwork,
        preparingArtwork = preparingArtwork, modifier = modifier)
}

@Composable
internal fun PlaylistDetailScreen(
    state: PlaylistDetailUiState, onBack: () -> Unit, onPlay: (List<Track>, Int) -> Unit,
    onRefresh: () -> Unit, onAdd: (Track) -> Unit, onRemove: (String) -> Unit,
    onMove: (String, Int) -> Unit, onEdit: () -> Unit, onCancelEdit: () -> Unit,
    onName: (String) -> Unit, onDescription: (String) -> Unit, onSave: () -> Unit,
    onLoadMore: () -> Unit = {}, onRetryPending: () -> Unit = {},
    onInviteRole: (PlaylistRole) -> Unit = {}, onCreateInvite: () -> Unit = {},
    onShareInvite: (String) -> Unit = {}, onRemoveMember: (String) -> Unit = {},
    onLeave: () -> Unit = {}, onVisibility: (PlaylistVisibility) -> Unit = {},
    onFollowing: (Boolean) -> Unit = {}, onToggleDiagnostics: () -> Unit = {},
    onChooseArtwork: () -> Unit, onRemoveArtwork: () -> Unit, onRetryArtwork: () -> Unit,
    preparingArtwork: Boolean = false, modifier: Modifier = Modifier,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val items = state.orderedItems
    val queue = state.playableTracks
    val existing = remember(items) { items.map { it.trackId }.toSet() }
    val candidates = remember(state.catalogTracks, existing, query) {
        state.catalogTracks.filter { it.id !in existing &&
            (it.title.contains(query, true) || it.artist.contains(query, true)) }.take(50)
    }
    val configuration = LocalConfiguration.current
    val adaptivePadding = if (configuration.screenWidthDp >= 600) 40.dp else 20.dp
    val coverSize = if (configuration.screenWidthDp >= 600) 200.dp else 160.dp
    LazyColumn(modifier.fillMaxSize().testTag("playlist-detail"), contentPadding = PaddingValues(adaptivePadding, 20.dp, adaptivePadding, 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to playlists")
                }
                Text(state.detail?.playlist?.name ?: "Playlist", style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f).semantics { heading() })
            }
            TextButton(onClick = onRefresh, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Refresh playlist")
            }
            if (state.busy) CircularProgressIndicator(Modifier.size(24.dp))
            if (!state.verified && state.detail != null) Text("Saved on this device. Refresh before editing.")
            if (state.pendingMutationCount > 0) {
                Button(
                    onClick = onRetryPending,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .semantics { stateDescription = "${state.pendingMutationCount} changes waiting" },
                ) { Text("Retry ${state.pendingMutationCount} saved change") }
            }
        }
        state.message?.let { message -> item {
            Text(message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        } }
        if (state.detail == null && !state.busy) item { Text("Playlist unavailable. Check your connection and retry.") }
        state.detail?.let { detail ->
            item {
                Text(detail.playlist.description)
                Text(
                    "${detail.playlist.accessRole.name} · ${detail.playlist.visibility.name} · " +
                        "${items.size} of ${detail.totalItems} songs",
                )
                val bitmap = remember(state.artwork) { state.artwork?.let(::decodePlaylistArtwork)?.asImageBitmap() }
                if (bitmap != null) Image(bitmap, "Playlist cover", Modifier.size(coverSize))
                Column {
                    TextButton(onClick = onChooseArtwork,
                        enabled = state.canEditMetadata && !state.artworkBusy && !preparingArtwork,
                        modifier = Modifier.heightIn(min = 48.dp)) { Text("Choose cover") }
                    TextButton(onClick = onRemoveArtwork,
                        enabled = state.canEditMetadata && !state.artworkBusy && !preparingArtwork && state.artwork != null,
                        modifier = Modifier.heightIn(min = 48.dp)) { Text("Remove cover") }
                    TextButton(onClick = onRetryArtwork, enabled = !state.artworkBusy,
                        modifier = Modifier.heightIn(min = 48.dp)) { Text("Retry cover") }
                    if (state.artworkBusy || preparingArtwork) Text("Processing cover…",
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                Button(onClick = { onPlay(queue, 0) }, enabled = queue.isNotEmpty() && state.pendingOrder == null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Play playlist") }
                TextButton(onClick = onEdit, enabled = state.canEditMetadata,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("Edit playlist details") }
            }
            item {
                Surface(shape = MaterialTheme.shapes.large, tonalElevation = 2.dp) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Access", style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() })
                        if (detail.playlist.accessRole == PlaylistRole.Owner) {
                            Text("Only you can manage visibility, invites, and members.")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = { onVisibility(PlaylistVisibility.Private) },
                                    enabled = !state.accessBusy &&
                                        detail.playlist.visibility != PlaylistVisibility.Private,
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) { Text("Make private") }
                                TextButton(
                                    onClick = { onVisibility(PlaylistVisibility.Public) },
                                    enabled = !state.accessBusy &&
                                        detail.playlist.visibility != PlaylistVisibility.Public,
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) { Text("Make public") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = { onInviteRole(PlaylistRole.Editor) },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) { Text(if (state.inviteRole == PlaylistRole.Editor) "Editor selected" else "Editor") }
                                TextButton(
                                    onClick = { onInviteRole(PlaylistRole.Viewer) },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) { Text(if (state.inviteRole == PlaylistRole.Viewer) "Viewer selected" else "Viewer") }
                            }
                            Button(
                                onClick = onCreateInvite,
                                enabled = !state.accessBusy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) { Text("Create 7-day invite") }
                            state.inviteLink?.let { link ->
                                Button(
                                    onClick = { onShareInvite(link) },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                ) { Text("Share invite securely") }
                            }
                        } else if (detail.playlist.accessRole == PlaylistRole.Follower) {
                            Button(
                                onClick = { onFollowing(!detail.playlist.isFollowing) },
                                enabled = !state.accessBusy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) { Text(if (detail.playlist.isFollowing) "Unfollow playlist" else "Follow playlist") }
                        } else {
                            Text("The owner controls membership and visibility.")
                            TextButton(
                                onClick = onLeave,
                                enabled = !state.accessBusy,
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) { Text("Leave playlist") }
                        }
                        detail.members.forEach { member ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${member.displayName} · ${member.role.name}" +
                                        if (member.isCurrentUser) " · You" else "",
                                    modifier = Modifier.weight(1f),
                                )
                                if (detail.playlist.accessRole == PlaylistRole.Owner &&
                                    member.role != PlaylistRole.Owner
                                ) {
                                    TextButton(
                                        onClick = { onRemoveMember(member.userId) },
                                        enabled = !state.accessBusy,
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    ) { Text("Remove ${member.displayName}") }
                                }
                            }
                        }
                    }
                }
            }
            if (state.editing) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(state.name, onName, label = { Text("Playlist name") },
                        enabled = !state.busy, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        supportingText = { Text("${state.name.length}/100") })
                    OutlinedTextField(state.description, onDescription, label = { Text("Playlist description") },
                        enabled = !state.busy, modifier = Modifier.fillMaxWidth(), maxLines = 4,
                        supportingText = { Text("${state.description.length}/300") })
                    Button(onClick = onSave, enabled = state.canEditMetadata && state.name.isNotBlank(),
                        modifier = Modifier.heightIn(min = 48.dp)) { Text("Save details") }
                    TextButton(onClick = onCancelEdit, enabled = !state.busy,
                        modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel editing") }
                }
            }
            item {
                Button(onClick = { adding = !adding }, enabled = state.canMutate,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(if (adding) "Close song picker" else "Add songs") }
            }
            if (adding) {
                item {
                    Text("Choose from the catalog saved on this device (up to 50 matches).")
                    OutlinedTextField(query, { query = it.take(100) }, label = { Text("Find songs to add") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    if (candidates.isEmpty()) Text("No matching songs to add. Refresh the Home catalog for more songs.")
                }
                items(candidates, key = { "candidate-${it.id}" }) { track ->
                    TextButton(onClick = { onAdd(track) }, enabled = state.canMutate && items.size < 500,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Add ${track.title} · ${track.artist}")
                    }
                }
            }
            if (items.isEmpty()) item { Text("This playlist is empty. Add your first song above.") }
            itemsIndexed(items, key = { _, item -> item.trackId }) { index, item ->
                Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 2.dp) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        val label = item.track?.title ?: "Unavailable song"
                        TextButton(onClick = {
                            val start = queue.indexOfFirst { it.id == item.trackId }
                            if (start >= 0) onPlay(queue, start)
                        }, enabled = item.track != null && state.pendingOrder == null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("${index + 1}. $label${item.track?.artist?.let { " · $it" }.orEmpty()}")
                        }
                        Row {
                            IconButton(onClick = { onMove(item.trackId, -1) }, enabled = state.canMutate && index > 0,
                                modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Rounded.ArrowUpward, "Move $label up")
                            }
                            IconButton(onClick = { onMove(item.trackId, 1) }, enabled = state.canMutate && index < items.lastIndex,
                                modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Rounded.ArrowDownward, "Move $label down")
                            }
                            IconButton(onClick = { onRemove(item.trackId) }, enabled = state.canMutate,
                                modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Rounded.Delete, "Remove $label from playlist")
                            }
                        }
                    }
                }
            }
            if (detail.nextOffset != null) {
                item {
                    Button(
                        onClick = onLoadMore,
                        enabled = !state.loadingMore,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text(if (state.loadingMore) "Loading more songs…" else "Load more songs") }
                }
            }
            item {
                TextButton(
                    onClick = onToggleDiagnostics,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(if (state.diagnosticsExpanded) "Hide sync details" else "Show sync details") }
                if (state.diagnosticsExpanded) {
                    Text(
                        "Loaded ${items.size}/${detail.totalItems} · pending " +
                            "${state.pendingMutationCount} · revision ${detail.playlist.revision}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Diagnostics stay on this device and contain no song, account, or invite identifiers.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
