package my.id.rakyzumusic.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import my.id.rakyzumusic.core.designsystem.theme.RakyzuAqua
import my.id.rakyzumusic.core.designsystem.theme.RakyzuSurface
import my.id.rakyzumusic.core.model.Track
import my.id.rakyzumusic.core.model.formattedDuration

internal data class TrackContextCapabilities(
    val canViewArtist: Boolean,
    val canViewAlbum: Boolean,
)

internal fun Track.contextCapabilities() = TrackContextCapabilities(
    canViewArtist = artistId.isNotBlank(),
    canViewAlbum = albumId.isNotBlank(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackContextSheet(
    track: Track,
    onDismiss: () -> Unit,
    onPlay: (() -> Unit)? = null,
    onViewArtist: (() -> Unit)? = null,
    onViewAlbum: (() -> Unit)? = null,
) {
    val capabilities = track.contextCapabilities()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = RakyzuSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Track details",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = track.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close track details")
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TrackMetadataRow("Artist", track.artist.ifBlank { "Unknown artist" })
                TrackMetadataRow("Album", track.albumTitle.ifBlank { "Unknown album" })
                TrackMetadataRow("Duration", track.formattedDuration())
                TrackMetadataRow("Disc", track.discNumber.coerceAtLeast(1).toString())
                TrackMetadataRow("Track", track.trackNumber.coerceAtLeast(1).toString())
                TrackMetadataRow("Explicit", if (track.isExplicit) "Yes" else "No")
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            onPlay?.let { action ->
                TrackContextAction(
                    icon = Icons.Rounded.PlayArrow,
                    label = "Play track",
                    onClickLabel = "Play ${track.title.trim()}",
                    onClick = action,
                )
            }
            if (capabilities.canViewArtist) {
                onViewArtist?.let { action ->
                    TrackContextAction(
                        icon = Icons.Rounded.Person,
                        label = "View artist",
                        onClickLabel = "View ${track.artist.trim()} artist",
                        onClick = action,
                    )
                }
            }
            if (capabilities.canViewAlbum) {
                onViewAlbum?.let { action ->
                    TrackContextAction(
                        icon = Icons.Rounded.Album,
                        label = "View album",
                        onClickLabel = "View ${track.albumTitle.trim()} album",
                        onClick = action,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackMetadataRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TrackContextAction(
    icon: ImageVector,
    label: String,
    onClickLabel: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(
                onClickLabel = onClickLabel,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = RakyzuAqua)
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
