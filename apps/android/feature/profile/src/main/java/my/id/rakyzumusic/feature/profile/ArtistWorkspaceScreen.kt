package my.id.rakyzumusic.feature.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.id.rakyzumusic.core.data.artist.ArtistWorkspace
import my.id.rakyzumusic.core.data.artist.ArtistWorkspaceActionResult
import my.id.rakyzumusic.core.data.artist.ArtistWorkspaceFailure
import my.id.rakyzumusic.core.data.artist.ArtistWorkspaceRepository
import my.id.rakyzumusic.core.data.artist.ArtistWorkspaceResult
import my.id.rakyzumusic.core.designsystem.theme.RakyzuAqua

data class ArtistWorkspaceUiState(
    val workspace: ArtistWorkspace? = null,
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
)

class ArtistWorkspaceViewModel(
    private val repository: ArtistWorkspaceRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ArtistWorkspaceUiState())
    val state: StateFlow<ArtistWorkspaceUiState> = mutableState.asStateFlow()

    init { refresh() }

    fun refresh() {
        if (mutableState.value.isWorking) return
        mutableState.update { it.copy(isLoading = true, message = null) }
        viewModelScope.launch {
            when (val result = repository.load()) {
                is ArtistWorkspaceResult.Success -> mutableState.update {
                    it.copy(workspace = result.workspace, isLoading = false,
                        message = null, messageIsError = false)
                }
                is ArtistWorkspaceResult.Failure -> mutableState.update {
                    it.copy(isLoading = false, message = result.reason.message(), messageIsError = true)
                }
            }
        }
    }

    fun assignTeam(email: String, accessLevel: String, active: Boolean) = perform {
        repository.assignTeam(email, accessLevel, active)
    }

    fun createAlbum(title: String, releaseDate: String?) {
        val artistId = mutableState.value.workspace?.artistId ?: return
        perform { repository.createAlbum(artistId, title, releaseDate) }
    }

    fun createTrack(albumId: String, title: String, durationMs: Int, explicit: Boolean) = perform {
        repository.createTrack(albumId, title, durationMs, 1, 1, explicit)
    }

    fun uploadArtistArtwork(bytes: ByteArray) = perform { repository.uploadArtistArtwork(bytes) }
    fun uploadAlbumArtwork(albumId: String, bytes: ByteArray) = perform {
        repository.uploadAlbumArtwork(albumId, bytes)
    }
    fun uploadTrackAudio(trackId: String, bytes: ByteArray) = perform {
        repository.uploadTrackAudio(trackId, "standard", bytes)
    }
    fun submitReview(type: String, albumId: String, notes: String) = perform {
        repository.submitReview(type, albumId, notes)
    }

    private fun perform(block: suspend () -> ArtistWorkspaceActionResult) {
        if (mutableState.value.isWorking) return
        mutableState.update { it.copy(isWorking = true, message = null) }
        viewModelScope.launch {
            when (val result = block()) {
                is ArtistWorkspaceActionResult.Success -> {
                    mutableState.update { it.copy(isWorking = false, message = result.message,
                        messageIsError = false) }
                    refresh()
                }
                is ArtistWorkspaceActionResult.Failure -> mutableState.update {
                    it.copy(isWorking = false, message = result.reason.message(), messageIsError = true)
                }
            }
        }
    }

    companion object {
        fun factory(repository: ArtistWorkspaceRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(ArtistWorkspaceViewModel::class.java))
                    return ArtistWorkspaceViewModel(repository) as T
                }
            }
    }
}

@Composable
fun ArtistWorkspaceRoute(
    viewModel: ArtistWorkspaceViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var targetAlbumId by remember { mutableStateOf<String?>(null) }
    var targetTrackId by remember { mutableStateOf<String?>(null) }
    val artistArtwork = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { scope.launch { decodeWebp(context.contentResolver.openInputStream(it))
            ?.let(viewModel::uploadArtistArtwork) } }
    }
    val albumArtwork = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val albumId = targetAlbumId
        if (uri != null && albumId != null) scope.launch {
            decodeWebp(context.contentResolver.openInputStream(uri))
                ?.let { viewModel.uploadAlbumArtwork(albumId, it) }
        }
    }
    val trackAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val trackId = targetTrackId
        if (uri != null && trackId != null) scope.launch {
            readBounded(context.contentResolver.openInputStream(uri), 50 * 1024 * 1024)
                ?.let { viewModel.uploadTrackAudio(trackId, it) }
        }
    }

    if (state.isLoading && state.workspace == null) {
        Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator(color = RakyzuAqua)
            Text("Loading Artist workspace")
        }
        return
    }
    val workspace = state.workspace
    if (workspace == null) {
        Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text(state.message ?: "Artist workspace is unavailable.")
            Button(onClick = viewModel::refresh) { Text("Try again") }
        }
        return
    }

    var albumTitle by remember { mutableStateOf("") }
    var releaseDate by remember { mutableStateOf("") }
    var trackAlbumId by remember { mutableStateOf("") }
    var trackTitle by remember { mutableStateOf("") }
    var durationSeconds by remember { mutableStateOf("") }
    var explicit by remember { mutableStateOf(false) }
    var teamEmail by remember { mutableStateOf("") }
    var teamLevel by remember { mutableStateOf("viewer") }
    var reviewAlbumId by remember { mutableStateOf("") }
    var reviewType by remember { mutableStateOf("artwork") }
    var reviewNotes by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Artist Studio", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black, modifier = Modifier.semantics { heading() })
        Text("${workspace.artistName} · review-gated publishing", color = RakyzuAqua)
        state.message?.let { ProfileMessage(it, state.messageIsError) }
        Button(onClick = { artistArtwork.launch(arrayOf("image/jpeg", "image/png", "image/webp")) },
            enabled = !state.isWorking, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(if (workspace.hasArtwork) "Change Artist image" else "Add Artist image")
        }

        workspace.analytics?.let { analytics ->
            WorkspaceHeading("Last 30 days")
            Text("${analytics.streams} streams · ${analytics.completedStreams} completed")
            Text(
                if (analytics.privacyThresholdMet) {
                    "${analytics.listeners ?: 0} listeners · ${analytics.followers} followers · ${analytics.releases} releases"
                } else {
                    "Listener and geography detail appears after 5 distinct listeners"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            analytics.geography.take(5).forEach { country ->
                Text("${country.country} · ${country.streams} streams · ${country.listeners} listeners",
                    style = MaterialTheme.typography.bodySmall)
            }
        }

        WorkspaceHeading("Album drafts")
        OutlinedTextField(albumTitle, { albumTitle = it.take(160) }, Modifier.fillMaxWidth(),
            label = { Text("Album title") }, enabled = !state.isWorking)
        OutlinedTextField(releaseDate, { releaseDate = it.take(10) }, Modifier.fillMaxWidth(),
            label = { Text("Release date (YYYY-MM-DD, optional)") }, enabled = !state.isWorking)
        Button(onClick = { viewModel.createAlbum(albumTitle, releaseDate.ifBlank { null }) },
            enabled = !state.isWorking && albumTitle.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("Create album draft")
        }
        workspace.albums.forEach { album ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(album.title, fontWeight = FontWeight.Bold)
                Text("${album.id} · ${if (album.hasArtwork) "artwork ready" else "artwork needed"}",
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = { targetAlbumId = album.id
                    albumArtwork.launch(arrayOf("image/jpeg", "image/png", "image/webp")) },
                    enabled = !state.isWorking && !album.published) { Text("Upload artwork") }
            }
        }

        HorizontalDivider()
        WorkspaceHeading("Track drafts")
        OutlinedTextField(trackAlbumId, { trackAlbumId = it.take(36) }, Modifier.fillMaxWidth(),
            label = { Text("Album ID") }, enabled = !state.isWorking)
        OutlinedTextField(trackTitle, { trackTitle = it.take(160) }, Modifier.fillMaxWidth(),
            label = { Text("Track title") }, enabled = !state.isWorking)
        OutlinedTextField(durationSeconds, { durationSeconds = it.filter(Char::isDigit).take(6) },
            Modifier.fillMaxWidth(), label = { Text("Duration in seconds") }, enabled = !state.isWorking)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(explicit, { explicit = it }, enabled = !state.isWorking)
            Text("Explicit content")
        }
        Button(onClick = { viewModel.createTrack(trackAlbumId, trackTitle,
            (durationSeconds.toIntOrNull() ?: 0) * 1_000, explicit) },
            enabled = !state.isWorking && trackAlbumId.isNotBlank() && trackTitle.isNotBlank() &&
                (durationSeconds.toIntOrNull() ?: 0) > 0, modifier = Modifier.fillMaxWidth()) {
            Text("Create track draft")
        }
        workspace.tracks.forEach { track ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(track.title, fontWeight = FontWeight.Bold)
                Text("${track.id} · ${if (track.hasStandardAudio) "audio ready" else "audio needed"}",
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = { targetTrackId = track.id
                    trackAudio.launch(arrayOf("audio/mpeg", "audio/aac", "audio/mp4", "audio/webm",
                        "audio/wav", "audio/flac")) }, enabled = !state.isWorking && !track.published) {
                    Text("Upload standard audio")
                }
            }
        }

        HorizontalDivider()
        WorkspaceHeading("Artist team")
        OutlinedTextField(teamEmail, { teamEmail = it.take(254) }, Modifier.fillMaxWidth(),
            label = { Text("Existing account email") }, enabled = !state.isWorking)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(teamLevel == "viewer", { teamLevel = "viewer" }, { Text("Viewer") })
            FilterChip(teamLevel == "editor", { teamLevel = "editor" }, { Text("Editor") })
        }
        Button(onClick = { viewModel.assignTeam(teamEmail, teamLevel, true) },
            enabled = !state.isWorking && teamEmail.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("Add or update team member")
        }
        workspace.team.forEach { Text("${it.email} · ${it.accessLevel}${if (it.active) "" else " · disabled"}") }

        HorizontalDivider()
        WorkspaceHeading("Independent review")
        Text("Artists cannot publish directly. Submit artwork or a complete release for staff review.")
        OutlinedTextField(reviewAlbumId, { reviewAlbumId = it.take(36) }, Modifier.fillMaxWidth(),
            label = { Text("Album ID") }, enabled = !state.isWorking)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(reviewType == "artwork", { reviewType = "artwork" }, { Text("Artwork") })
            FilterChip(reviewType == "release", { reviewType = "release" }, { Text("Release") })
        }
        OutlinedTextField(reviewNotes, { reviewNotes = it.take(500) }, Modifier.fillMaxWidth(),
            label = { Text("Review notes (optional)") }, enabled = !state.isWorking, minLines = 2)
        Button(onClick = { viewModel.submitReview(reviewType, reviewAlbumId, reviewNotes) },
            enabled = !state.isWorking && reviewAlbumId.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("Submit for review")
        }
        workspace.reviews.forEach { Text("${it.reviewType} · ${it.status} · ${it.submittedAt}") }
        if (state.isWorking) CircularProgressIndicator(color = RakyzuAqua)
    }
}

@Composable
private fun WorkspaceHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { heading() })
}

private suspend fun decodeWebp(input: java.io.InputStream?): ByteArray? = withContext(Dispatchers.IO) {
    val source = readBounded(input, MAX_ARTWORK_SOURCE_BYTES) ?: return@withContext null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
        bounds.outWidth > MAX_ARTWORK_DIMENSION || bounds.outHeight > MAX_ARTWORK_DIMENSION
    ) return@withContext null
    var sample = 1
    while (bounds.outWidth / sample > MAX_ARTWORK_EDGE ||
        bounds.outHeight / sample > MAX_ARTWORK_EDGE
    ) sample *= 2
    val bitmap = BitmapFactory.decodeByteArray(
        source,
        0,
        source.size,
        BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return@withContext null
    val longest = maxOf(bitmap.width, bitmap.height)
    val scaled = if (longest <= MAX_ARTWORK_EDGE) bitmap else {
        val ratio = MAX_ARTWORK_EDGE.toFloat() / longest
        Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1), true)
    }
    try {
        ByteArrayOutputStream().use { output ->
            if (!scaled.compress(artistWebpLossyFormat(), 88, output)) null
            else output.toByteArray().takeIf { it.size in 12..MAX_ARTWORK_BYTES }
        }
    } finally {
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
    }
}

@Suppress("DEPRECATION")
private fun artistWebpLossyFormat(): Bitmap.CompressFormat =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY
    else Bitmap.CompressFormat.WEBP

private const val MAX_ARTWORK_BYTES = 5 * 1024 * 1024
private const val MAX_ARTWORK_SOURCE_BYTES = 15 * 1024 * 1024
private const val MAX_ARTWORK_DIMENSION = 12_000
private const val MAX_ARTWORK_EDGE = 1_600

private suspend fun readBounded(input: java.io.InputStream?, maximum: Int): ByteArray? =
    withContext(Dispatchers.IO) {
        input?.use { source ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val read = source.read(buffer)
                if (read < 0) break
                if (output.size() + read > maximum) return@withContext null
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }

private fun ArtistWorkspaceFailure.message(): String = when (this) {
    ArtistWorkspaceFailure.InvalidInput -> "Check the Artist workspace fields and selected file."
    ArtistWorkspaceFailure.NotAuthenticated -> "Your session expired. Sign in again."
    ArtistWorkspaceFailure.Forbidden -> "This account cannot perform that Artist action."
    ArtistWorkspaceFailure.PayloadTooLarge -> "The selected file is too large."
    ArtistWorkspaceFailure.ServiceUnavailable -> "Artist services are temporarily unavailable."
}
