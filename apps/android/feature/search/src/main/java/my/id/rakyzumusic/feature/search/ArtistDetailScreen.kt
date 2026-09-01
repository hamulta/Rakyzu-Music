package my.id.rakyzumusic.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.id.rakyzumusic.core.designsystem.theme.RakyzuAqua
import my.id.rakyzumusic.core.designsystem.theme.RakyzuBlack
import my.id.rakyzumusic.core.designsystem.theme.RakyzuMusicTheme
import my.id.rakyzumusic.core.designsystem.theme.RakyzuPurple
import my.id.rakyzumusic.core.designsystem.theme.RakyzuSurfaceRaised
import my.id.rakyzumusic.core.model.Album
import my.id.rakyzumusic.core.model.Artist
import my.id.rakyzumusic.core.model.Track
import my.id.rakyzumusic.core.model.formattedDuration

@Composable
fun ArtistDetailRoute(
    viewModel: ArtistDetailViewModel,
    onBack: () -> Unit,
    onTrackPlay: (List<Track>, Int) -> Unit,
    onAlbumClick: ((Album) -> Unit)? = null,
    onTrackArtistClick: ((Track) -> Unit)? = null,
    onTrackAlbumClick: ((Track) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ArtistDetailScreen(
        state = state,
        onBack = onBack,
        onTrackPlay = onTrackPlay,
        onAlbumClick = onAlbumClick,
        onTrackArtistClick = onTrackArtistClick,
        onTrackAlbumClick = onTrackAlbumClick,
        modifier = modifier,
    )
}

@Composable
fun ArtistDetailScreen(
    state: ArtistDetailUiState,
    onBack: () -> Unit,
    onTrackPlay: (List<Track>, Int) -> Unit,
    onAlbumClick: ((Album) -> Unit)? = null,
    onTrackArtistClick: ((Track) -> Unit)? = null,
    onTrackAlbumClick: ((Track) -> Unit)? = null,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 96.dp),
) {
    var contextualTrack by remember { mutableStateOf<Track?>(null) }

    contextualTrack?.let { track ->
        val queueIndex = state.tracks.indexOfFirst { it.id == track.id }
        TrackContextSheet(
            track = track,
            onDismiss = { contextualTrack = null },
            onPlay = if (queueIndex >= 0) {
                {
                    contextualTrack = null
                    onTrackPlay(state.tracks, queueIndex)
                }
            } else {
                null
            },
            onViewArtist = onTrackArtistClick?.let { callback ->
                {
                    contextualTrack = null
                    callback(track)
                }
            },
            onViewAlbum = onTrackAlbumClick?.let { callback ->
                {
                    contextualTrack = null
                    callback(track)
                }
            },
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFF321C4F),
                        0.34f to RakyzuBlack,
                        1f to RakyzuBlack,
                    ),
                ),
            ),
        contentPadding = contentPadding,
    ) {
        item(key = "artist-back") {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .padding(start = 8.dp, top = 12.dp)
                    .size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back to search",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        when {
            !state.hasObservedCatalog -> item(key = "artist-loading") {
                ArtistDetailStatus(
                    title = "Loading artist",
                    message = "Reading the latest verified offline catalog.",
                    showProgress = true,
                )
            }
            state.isUnavailable -> item(key = "artist-unavailable") {
                ArtistDetailStatus(
                    title = "Artist unavailable",
                    message = "This artist is no longer available in your verified catalog.",
                )
            }
            else -> {
                val artist = requireNotNull(state.artist)
                item(key = "artist-header") {
                    ArtistHeader(
                        artist = artist,
                        releaseCount = state.releases.size,
                        trackCount = state.tracks.size,
                        onPlayAll = { onTrackPlay(state.tracks, 0) },
                    )
                }

                if (state.releases.isNotEmpty()) {
                    item(key = "artist-releases-heading") {
                        ArtistSectionHeading("Releases")
                    }
                    items(
                        items = state.releases,
                        key = { "artist-release-${it.album.id}" },
                    ) { release ->
                        ArtistReleaseRow(
                            release = release,
                            onClick = onAlbumClick?.let { callback ->
                                { callback(release.album) }
                            },
                        )
                    }
                }

                if (state.tracks.isNotEmpty()) {
                    item(key = "artist-tracks-heading") {
                        ArtistSectionHeading("Tracks")
                    }
                    itemsIndexed(
                        items = state.tracks,
                        key = { _, track -> "artist-track-${track.id}" },
                    ) { index, track ->
                        ArtistTrackRow(
                            track = track,
                            onPlay = { onTrackPlay(state.tracks, index) },
                            onMoreClick = { contextualTrack = track },
                        )
                    }
                }

                if (state.releases.isEmpty() && state.tracks.isEmpty()) {
                    item(key = "artist-empty") {
                        ArtistDetailStatus(
                            title = "No releases yet",
                            message = "This artist has no published music in your catalog.",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistHeader(
    artist: Artist,
    releaseCount: Int,
    trackCount: Int,
    onPlayAll: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    Brush.linearGradient(listOf(RakyzuPurple, RakyzuAqua)),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = RakyzuBlack,
                modifier = Modifier.size(64.dp),
            )
        }
        Text(
            text = artist.name,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "$releaseCount ${releaseCount.unit("release")} · " +
                "$trackCount ${trackCount.unit("track")}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (trackCount > 0) {
            Button(
                onClick = onPlayAll,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RakyzuAqua,
                    contentColor = RakyzuBlack,
                ),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Text("Play all", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun ArtistSectionHeading(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun ArtistReleaseRow(
    release: ArtistRelease,
    onClick: (() -> Unit)?,
) {
    val interactionModifier = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(
            onClickLabel = "Open ${release.album.title.trim()} album",
            role = Role.Button,
            onClick = onClick,
        )
    }
    Surface(
        color = RakyzuSurfaceRaised,
        shape = RoundedCornerShape(16.dp),
        modifier = interactionModifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 64.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Album, contentDescription = null, tint = RakyzuAqua)
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(
                    text = release.album.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        release.album.releaseDate?.take(4)?.takeIf(String::isNotBlank),
                        "${release.trackCount} ${release.trackCount.unit("track")}",
                    ).joinToString(" · "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ArtistTrackRow(
    track: Track,
    onPlay: () -> Unit,
    onMoreClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(
                onClickLabel = "Play ${track.title.trim()} by ${track.artist.trim()}",
                role = Role.Button,
                onClick = onPlay,
            )
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = RakyzuAqua)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
        ) {
            Text(
                text = track.title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOf(track.albumTitle, track.formattedDuration())
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = RakyzuAqua)
        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "More options for ${track.title.trim()}",
            )
        }
    }
}

@Composable
private fun ArtistDetailStatus(
    title: String,
    message: String,
    showProgress: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 48.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showProgress) CircularProgressIndicator(color = RakyzuAqua)
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun Int.unit(singular: String): String = if (this == 1) singular else "${singular}s"

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ArtistDetailScreenPreview() {
    val artist = Artist("artist-1", "Rakyzu Sessions")
    val album = Album("album-1", artist.id, "Signal Zero", "2026-08-31")
    val track = Track(
        id = "track-1",
        title = "Midnight Signal",
        artist = artist.name,
        durationMs = 180_000L,
        artistId = artist.id,
        albumId = album.id,
        albumTitle = album.title,
    )
    RakyzuMusicTheme(darkTheme = true) {
        ArtistDetailScreen(
            state = ArtistDetailUiState(
                artistId = artist.id,
                hasObservedCatalog = true,
                artist = artist,
                releases = listOf(ArtistRelease(album, 1)),
                tracks = listOf(track),
            ),
            onBack = {},
            onTrackPlay = { _, _ -> },
        )
    }
}
