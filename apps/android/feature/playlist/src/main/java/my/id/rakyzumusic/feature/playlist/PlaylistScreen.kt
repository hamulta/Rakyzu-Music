package my.id.rakyzumusic.feature.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.id.rakyzumusic.core.designsystem.theme.RakyzuAqua
import my.id.rakyzumusic.core.designsystem.theme.RakyzuSurface
import my.id.rakyzumusic.core.model.PlaylistSummary

@Composable
fun PlaylistRoute(
    viewModel: PlaylistViewModel,
    onPlaylistClick: (PlaylistSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PlaylistScreen(
        state = state,
        onNameChange = viewModel::updateName,
        onDescriptionChange = viewModel::updateDescription,
        onCreate = viewModel::create,
        onRefresh = viewModel::refresh,
        onInviteTokenChange = viewModel::updateInviteToken,
        onAcceptInvite = viewModel::acceptInvite,
        onLoadMore = viewModel::loadMore,
        modifier = modifier,
        onPlaylistClick = onPlaylistClick,
    )
}

@Composable
internal fun PlaylistScreen(
    state: PlaylistUiState,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onCreate: () -> Unit,
    onRefresh: () -> Unit,
    onInviteTokenChange: (String) -> Unit = {},
    onAcceptInvite: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    modifier: Modifier = Modifier,
    onPlaylistClick: (PlaylistSummary) -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("playlist-list"),
        contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Create",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = "Make it yours, one playlist at a time.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRefresh, enabled = !state.isRefreshing, modifier = Modifier.size(48.dp)) {
                    if (state.isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh playlists")
                    }
                }
            }
        }
        item {
            Surface(color = RakyzuSurface, shape = MaterialTheme.shapes.large) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("New playlist", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = state.name,
                        enabled = !state.isCreating,
                        onValueChange = onNameChange,
                        label = { Text("Name") },
                        supportingText = { Text("${state.name.length}/$MAX_PLAYLIST_NAME_LENGTH") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.description,
                        enabled = !state.isCreating,
                        onValueChange = onDescriptionChange,
                        label = { Text("Description (optional)") },
                        supportingText = {
                            Text("${state.description.length}/$MAX_PLAYLIST_DESCRIPTION_LENGTH")
                        },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = onCreate,
                        enabled = state.canCreate,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        if (state.isCreating) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Creating playlist…")
                        } else {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Text("Create playlist", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
        item {
            Surface(color = RakyzuSurface, shape = MaterialTheme.shapes.large) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Join a shared playlist", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Paste the Rakyzu Music invite link. Invitations expire after 7 days.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = state.inviteToken,
                        onValueChange = onInviteTokenChange,
                        enabled = !state.isAcceptingInvite,
                        label = { Text("Invite link or code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = onAcceptInvite,
                        enabled = state.inviteToken.length == 36 && !state.isAcceptingInvite,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(if (state.isAcceptingInvite) "Joining…" else "Join playlist")
                    }
                }
            }
        }
        state.message?.let { message ->
            item {
                Text(
                    text = message,
                    color = if (state.messageIsError) MaterialTheme.colorScheme.error else RakyzuAqua,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        item {
            Text(
                text = "Your playlists",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text("Most recently updated · up to 100 playlists", style = MaterialTheme.typography.bodySmall)
        }
        if (state.hasObservedPlaylists && state.playlists.isEmpty()) {
            item {
                Text(
                    text = "No playlists yet. Create your first one above.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(state.playlists, key = PlaylistSummary::id) { playlist ->
                PlaylistRow(playlist, onClick = { onPlaylistClick(playlist) })
            }
        }
        if (state.hasMore) {
            item {
                Button(
                    onClick = onLoadMore,
                    enabled = !state.isLoadingMore,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(if (state.isLoadingMore) "Loading more…" else "Load more playlists")
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(playlist: PlaylistSummary, onClick: () -> Unit) {
    Surface(onClick = onClick, color = RakyzuSurface, shape = MaterialTheme.shapes.medium) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(color = RakyzuAqua.copy(alpha = 0.14f), shape = MaterialTheme.shapes.medium) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    contentDescription = null,
                    tint = RakyzuAqua,
                    modifier = Modifier.padding(12.dp).size(28.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${playlist.trackCount} songs · ${playlist.accessRole.name} · " +
                        playlist.visibility.name,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
