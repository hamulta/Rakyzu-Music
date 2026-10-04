package my.id.rakyzumusic.feature.search

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.id.rakyzumusic.core.data.media.ArtworkRequest
import my.id.rakyzumusic.core.data.media.ArtworkRequestResult
import my.id.rakyzumusic.core.designsystem.theme.RakyzuAqua
import my.id.rakyzumusic.core.designsystem.theme.RakyzuBlack
import my.id.rakyzumusic.core.model.EditorialGroupMutation
import my.id.rakyzumusic.core.model.EditorialPlacement
import my.id.rakyzumusic.core.model.Track

internal typealias ExploreArtworkProvider = (String) -> ArtworkRequestResult

internal val unavailableExploreArtworkProvider: ExploreArtworkProvider = {
    ArtworkRequestResult.Failure(
        my.id.rakyzumusic.core.data.media.ArtworkRequestFailure.InvalidConfiguration,
    )
}

@Composable
internal fun ExploreLanding(
    state: SearchUiState,
    canManageEditorial: Boolean,
    artworkProvider: ExploreArtworkProvider,
    recommendationArtworkProvider: ExploreArtworkProvider,
    onTrackPlay: (List<Track>, Int) -> Unit,
    onOpenGroup: (String) -> Unit,
    onSaveGroup: (EditorialGroupMutation, ByteArray?) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onDeleteArtwork: (String) -> Unit,
) {
    val topGenres = state.browseCategories.filter {
        it.position in EditorialPlacement.ExploreTopGenre.positionRange
    }
    val browseAll = state.browseCategories.filter {
        it.position in EditorialPlacement.ExploreBrowse.positionRange
    }
    Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
        if (state.recommendedTracks.isNotEmpty()) {
            CompactRecommendationSection(
                tracks = state.recommendedTracks,
                artworkProvider = artworkProvider,
                onTrackPlay = onTrackPlay,
            )
        }
        ExploreGroupGrid(
            title = "Your Top Genres",
            groups = topGenres,
            placement = EditorialPlacement.ExploreTopGenre,
            canManageEditorial = canManageEditorial,
            catalogTracks = state.catalog.tracks,
            artworkProvider = artworkProvider,
            recommendationArtworkProvider = recommendationArtworkProvider,
            onOpenGroup = onOpenGroup,
            onSaveGroup = onSaveGroup,
            onDeleteGroup = onDeleteGroup,
            onDeleteArtwork = onDeleteArtwork,
        )
        ExploreGroupGrid(
            title = "Browse All",
            groups = browseAll,
            placement = EditorialPlacement.ExploreBrowse,
            canManageEditorial = canManageEditorial,
            catalogTracks = state.catalog.tracks,
            artworkProvider = artworkProvider,
            recommendationArtworkProvider = recommendationArtworkProvider,
            onOpenGroup = onOpenGroup,
            onSaveGroup = onSaveGroup,
            onDeleteGroup = onDeleteGroup,
            onDeleteArtwork = onDeleteArtwork,
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun CompactRecommendationSection(
    tracks: List<Track>,
    artworkProvider: ExploreArtworkProvider,
    onTrackPlay: (List<Track>, Int) -> Unit,
) {
    ExploreHeading("Recommended For You")
    LazyRow(
        contentPadding = PaddingValues(horizontal = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(tracks.take(18), key = { _, item -> item.id }) { index, track ->
            Surface(
                color = Color(0xFF202020),
                shape = RoundedCornerShape(5.dp),
                onClick = { onTrackPlay(tracks, index) },
                modifier = Modifier.width(182.dp).height(55.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ExploreArtwork(
                        id = track.albumId,
                        provider = artworkProvider,
                        color = EXPLORE_COLORS[index % EXPLORE_COLORS.size],
                        modifier = Modifier.size(54.dp),
                        shape = RoundedCornerShape(3.dp),
                    )
                    Column(Modifier.padding(horizontal = 10.dp)) {
                        Text(
                            track.title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            track.artist,
                            color = Color.White.copy(alpha = 0.62f),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExploreHeading(title: String) {
    Text(
        title,
        color = Color.White,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 37.dp).semantics { heading() },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExploreGroupGrid(
    title: String,
    groups: List<BrowseCategory>,
    placement: EditorialPlacement,
    canManageEditorial: Boolean,
    catalogTracks: List<Track>,
    artworkProvider: ExploreArtworkProvider,
    recommendationArtworkProvider: ExploreArtworkProvider,
    onOpenGroup: (String) -> Unit,
    onSaveGroup: (EditorialGroupMutation, ByteArray?) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onDeleteArtwork: (String) -> Unit,
) {
    var editing by remember { mutableStateOf<BrowseCategory?>(null) }
    var adding by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ExploreHeading(title)
        val cells: List<BrowseCategory?> = buildList {
            addAll(groups.take(placement.maximumCards))
            if (canManageEditorial && groups.size < placement.maximumCards) add(null)
        }
        cells.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 37.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                rowItems.forEach { group ->
                    if (group == null) {
                        AddGroupCard(
                            onClick = { adding = true },
                            modifier = Modifier.weight(1f).aspectRatio(167f / 98f),
                        )
                    } else {
                        ExploreGroupCard(
                            group = group,
                            artworkProvider = artworkProvider,
                            recommendationArtworkProvider = recommendationArtworkProvider,
                            modifier = Modifier.weight(1f).aspectRatio(167f / 98f)
                                .combinedClickable(
                                    role = Role.Button,
                                    onClickLabel = "Open ${group.title}",
                                    onLongClickLabel = if (canManageEditorial) "Edit ${group.title}" else null,
                                    onClick = { onOpenGroup(group.id) },
                                    onLongClick = { if (canManageEditorial) editing = group },
                                ),
                        )
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    editing?.let { group ->
        EditorialGroupEditor(
            existing = group,
            placement = placement,
            catalogTracks = catalogTracks,
            onDismiss = { editing = null },
            onSave = { mutation, bytes -> editing = null; onSaveGroup(mutation, bytes) },
            onDelete = { editing = null; onDeleteGroup(group.id) },
            onDeleteArtwork = if (group.hasCustomArtwork) {
                { editing = null; onDeleteArtwork(group.id) }
            } else null,
        )
    }
    if (adding) {
        EditorialGroupEditor(
            existing = null,
            placement = placement,
            catalogTracks = catalogTracks,
            initialDisplayPosition = groups.size + 1,
            onDismiss = { adding = false },
            onSave = { mutation, bytes -> adding = false; onSaveGroup(mutation, bytes) },
        )
    }
}

@Composable
private fun ExploreGroupCard(
    group: BrowseCategory,
    artworkProvider: ExploreArtworkProvider,
    recommendationArtworkProvider: ExploreArtworkProvider,
    modifier: Modifier,
) {
    val color = group.colorHex.toComposeColor()
    Box(modifier.clip(RoundedCornerShape(4.dp)).background(color)) {
        val firstTrack = group.tracks.firstOrNull()
        if (firstTrack != null) {
            ExploreArtwork(
                id = if (group.hasCustomArtwork) group.id else firstTrack.albumId,
                provider = if (group.hasCustomArtwork) recommendationArtworkProvider else artworkProvider,
                color = color,
                shape = RoundedCornerShape(3.dp),
                modifier = Modifier.align(Alignment.CenterEnd).graphicsLayer {
                    rotationZ = -27.5f
                    translationX = 13.dp.toPx()
                    translationY = 9.dp.toPx()
                }.size(85.dp),
            )
        }
        Text(
            group.cardLabel?.ifBlank { group.title } ?: group.title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 17.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 15.dp, top = 12.dp, end = 15.dp),
        )
    }
}

@Composable
private fun AddGroupCard(onClick: () -> Unit, modifier: Modifier) {
    val aqua = RakyzuAqua
    Box(
        modifier = modifier.clip(RoundedCornerShape(7.dp)).drawBehind {
            drawRoundRect(
                color = aqua.copy(alpha = 0.8f),
                style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx()),
            )
        }.combinedClickable(role = Role.Button, onClickLabel = "Add Card Group", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = aqua, modifier = Modifier.size(34.dp))
    }
}

@Composable
internal fun EditorialGroupDetail(
    category: BrowseCategory,
    artworkProvider: ExploreArtworkProvider,
    recommendationArtworkProvider: ExploreArtworkProvider,
    onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onShuffle: (List<Track>) -> Unit,
    onAddToQueue: (List<Track>) -> Unit,
    onDownload: (BrowseCategory) -> Unit,
    onTrackMore: (Track) -> Unit,
) {
    val context = LocalContext.current
    val color = category.colorHex.toComposeColor()
    var showInfo by remember { mutableStateOf(false) }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(category.title) },
            text = { Text(category.subtitle ?: "Global Card Group curated by Rakyzu Music.") },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Done") } },
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth().background(
            Brush.verticalGradient(listOf(color.copy(alpha = 0.92f), color.copy(alpha = 0.42f), RakyzuBlack)),
        ),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(8.dp).size(48.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to Explore", tint = Color.White)
        }
        if (category.hasCustomArtwork) {
            ExploreArtwork(
                id = category.id,
                provider = recommendationArtworkProvider,
                color = color,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(224.dp).align(Alignment.CenterHorizontally),
            )
        } else {
            Box(
                modifier = Modifier.size(224.dp).align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(8.dp)).background(color),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.86f), modifier = Modifier.size(52.dp))
            }
        }
        Text(
            category.title,
            color = Color.White,
            fontSize = 30.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 22.dp).semantics { heading() },
        )
        category.subtitle?.let {
            Text(it, color = Color.White.copy(alpha = 0.72f), modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
        Text(
            "Rakyzu Music · ${category.tracks.size} songs",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onAddToQueue(category.tracks) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add all songs to queue", tint = Color.White)
            }
            IconButton(onClick = { onDownload(category) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Download, "Download Card Group", tint = Color.White)
            }
            IconButton(onClick = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "${category.title} on Rakyzu Music")
                }
                context.startActivity(Intent.createChooser(intent, "Share Card Group"))
            }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Share, "Share Card Group", tint = Color.White)
            }
            IconButton(onClick = { showInfo = true }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.MoreVert, "Card Group information", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { onShuffle(category.tracks) }, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Rounded.Shuffle, "Shuffle ${category.title}", tint = RakyzuAqua)
            }
            Surface(
                color = RakyzuAqua,
                shape = CircleShape,
                onClick = { if (category.tracks.isNotEmpty()) onPlay(category.tracks, 0) },
                modifier = Modifier.size(58.dp),
            ) {
                Icon(Icons.Rounded.PlayArrow, "Play ${category.title}", tint = RakyzuBlack, modifier = Modifier.padding(14.dp))
            }
        }
        category.tracks.take(50).forEachIndexed { index, track ->
            Surface(
                color = Color.Transparent,
                onClick = { onPlay(category.tracks, index) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        (index + 1).toString(), color = Color.White.copy(alpha = 0.58f),
                        modifier = Modifier.width(30.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(track.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(track.artist, color = Color.White.copy(alpha = 0.58f), maxLines = 1)
                    }
                    IconButton(onClick = { onTrackMore(track) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.MoreVert, "More options for ${track.title}", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorialGroupEditor(
    existing: BrowseCategory?,
    placement: EditorialPlacement,
    catalogTracks: List<Track>,
    initialDisplayPosition: Int = 1,
    onDismiss: () -> Unit,
    onSave: (EditorialGroupMutation, ByteArray?) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDeleteArtwork: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var subtitle by remember(existing?.id) { mutableStateOf(existing?.subtitle.orEmpty()) }
    var colorHex by remember(existing?.id) { mutableStateOf(existing?.colorHex ?: "#4A558F") }
    var position by remember(existing?.id) {
        mutableStateOf(existing?.let { placement.displayPosition(it.position).toString() } ?: initialDisplayPosition.toString())
    }
    var published by remember(existing?.id) { mutableStateOf(true) }
    var selectedTrackIds by remember(existing?.id) { mutableStateOf(existing?.tracks?.map(Track::id)?.toSet().orEmpty()) }
    var artworkBytes by remember(existing?.id) { mutableStateOf<ByteArray?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var pickArtworkAfterPermission by remember { mutableStateOf(false) }
    val artworkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) { readExploreArtwork(context, uri) }
            if (result != null) {
                artworkBytes = result.first
                colorHex = result.second
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pickArtworkAfterPermission) artworkPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
        pickArtworkAfterPermission = false
    }
    fun pickArtwork() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else Manifest.permission.READ_EXTERNAL_STORAGE
        if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            artworkPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
        } else {
            pickArtworkAfterPermission = true
            permissionLauncher.launch(permission)
        }
    }
    if (showDeleteConfirmation && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete ${existing?.title}?") },
            text = { Text("This removes the global Card Group for every listener. Songs stay in the catalog.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") } },
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add Card Group" else "Edit Card Group") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 570.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    title, { title = it.take(80) }, label = { Text("Card Group name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    subtitle, { subtitle = it.take(160) }, label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    colorHex, { colorHex = it.take(7).uppercase() }, label = { Text("Color #RRGGBB") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EXPLORE_COLORS.forEach { color ->
                        val hex = color.toHex()
                        Surface(
                            color = color,
                            shape = CircleShape,
                            onClick = { colorHex = hex },
                            modifier = Modifier.size(42.dp),
                        ) {
                            if (colorHex.equals(hex, true)) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.padding(10.dp))
                        }
                    }
                }
                OutlinedTextField(
                    position, { position = it.filter(Char::isDigit).take(2) },
                    label = { Text("Position (1-${placement.maximumCards})") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Visible globally", modifier = Modifier.weight(1f))
                    Switch(published, { published = it })
                }
                Text("Songs (${selectedTrackIds.size}/50)", fontWeight = FontWeight.Bold)
                if (catalogTracks.isEmpty()) Text("Upload and publish songs before creating a Card Group.")
                catalogTracks.take(80).forEach { track ->
                    FilterChip(
                        selected = track.id in selectedTrackIds,
                        onClick = {
                            selectedTrackIds = if (track.id in selectedTrackIds) {
                                selectedTrackIds - track.id
                            } else if (selectedTrackIds.size < 50) selectedTrackIds + track.id else selectedTrackIds
                        },
                        label = { Text("${track.title} · ${track.artist}", maxLines = 1) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Button(onClick = ::pickArtwork, modifier = Modifier.fillMaxWidth()) {
                    Icon(if (existing?.hasCustomArtwork == true || artworkBytes != null) Icons.Rounded.Edit else Icons.Rounded.Image, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (existing?.hasCustomArtwork == true || artworkBytes != null) "Change image" else "Add image")
                }
                if (artworkBytes != null) {
                    TextButton(onClick = { artworkBytes = null }, modifier = Modifier.fillMaxWidth()) {
                        Text("Remove selected image")
                    }
                }
                onDeleteArtwork?.let { callback ->
                    TextButton(onClick = callback, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Delete, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete current image")
                    }
                }
                onDelete?.let {
                    TextButton(onClick = { showDeleteConfirmation = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Delete, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete Card Group")
                    }
                }
            }
        },
        confirmButton = {
            val displayPosition = position.toIntOrNull()
            TextButton(
                enabled = title.isNotBlank() && selectedTrackIds.isNotEmpty() &&
                    displayPosition in 1..placement.maximumCards && COLOR_HEX.matches(colorHex),
                onClick = {
                    onSave(
                        EditorialGroupMutation(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            title = title.trim(),
                            subtitle = subtitle.trim().ifBlank { null },
                            placement = placement,
                            displayPosition = requireNotNull(displayPosition),
                            cardLabel = title.trim(),
                            colorHex = colorHex.uppercase(),
                            published = published,
                            trackIds = selectedTrackIds.toList(),
                        ),
                        artworkBytes,
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ExploreArtwork(
    id: String,
    provider: ExploreArtworkProvider,
    color: Color,
    modifier: Modifier,
    shape: RoundedCornerShape,
) {
    val request = remember(id, provider) { (provider(id) as? ArtworkRequestResult.Ready)?.request }
    Box(modifier.clip(shape).background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.52f)))), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(30.dp))
        request?.let {
            AsyncImage(
                model = it.toExploreCoilRequest(LocalContext.current),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun ArtworkRequest.toExploreCoilRequest(context: Context): ImageRequest {
    val headers = NetworkHeaders.Builder().apply {
        requestHeaders().forEach { (name, value) -> set(name, value) }
    }.build()
    val cache = if (url.contains("/v1/recommendations/")) CachePolicy.DISABLED else CachePolicy.ENABLED
    return ImageRequest.Builder(context).data(url).httpHeaders(headers)
        .memoryCacheKey("rakyzu-explore:$albumId").diskCacheKey("rakyzu-explore:$albumId")
        .memoryCachePolicy(cache).diskCachePolicy(cache).networkCachePolicy(cache).build()
}

private fun String.toComposeColor(): Color = runCatching {
    Color(android.graphics.Color.parseColor(this))
}.getOrDefault(Color(0xFF4A558F))

private fun Color.toHex(): String = String.format(
    "#%02X%02X%02X",
    (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(),
)

private fun readExploreArtwork(context: Context, uri: Uri): Pair<ByteArray, String>? {
    val source = context.contentResolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (output.size() + read > MAX_ARTWORK_SOURCE_BYTES) return@use null
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    } ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
    if (bounds.outWidth !in 1..12_000 || bounds.outHeight !in 1..12_000) return null
    var sample = 1
    while (bounds.outWidth / sample > 2_048 || bounds.outHeight / sample > 2_048) sample *= 2
    val bitmap = BitmapFactory.decodeByteArray(source, 0, source.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return null
    return try {
        val color = bitmap.dominantColorHex()
        val output = ByteArrayOutputStream()
        if (!bitmap.compress(exploreWebpFormat(), 84, output)) null else output.toByteArray()
            .takeIf { it.size in 12..MAX_ARTWORK_BYTES }?.let { it to color }
    } finally {
        bitmap.recycle()
    }
}

@Suppress("DEPRECATION")
private fun exploreWebpFormat(): Bitmap.CompressFormat =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY
    else Bitmap.CompressFormat.WEBP

private fun Bitmap.dominantColorHex(): String {
    val stepX = (width / 32).coerceAtLeast(1)
    val stepY = (height / 32).coerceAtLeast(1)
    val buckets = linkedMapOf<Int, Int>()
    for (y in 0 until height step stepY) for (x in 0 until width step stepX) {
        val pixel = getPixel(x, y)
        if (android.graphics.Color.alpha(pixel) < 160) continue
        val r = android.graphics.Color.red(pixel)
        val g = android.graphics.Color.green(pixel)
        val b = android.graphics.Color.blue(pixel)
        if (r + g + b < 42 || r + g + b > 720) continue
        val key = ((r / 32) shl 10) or ((g / 32) shl 5) or (b / 32)
        buckets[key] = (buckets[key] ?: 0) + 1
    }
    val key = buckets.maxByOrNull { it.value }?.key ?: return "#4A558F"
    val r = (((key shr 10) and 31) * 32 + 16).coerceAtMost(255)
    val g = (((key shr 5) and 31) * 32 + 16).coerceAtMost(255)
    val b = ((key and 31) * 32 + 16).coerceAtMost(255)
    return String.format("#%02X%02X%02X", r, g, b)
}

private val COLOR_HEX = Regex("^#[0-9A-Fa-f]{6}$")
private val EXPLORE_COLORS = listOf(
    Color(0xFF70CF18), Color(0xFFD3229D), Color(0xFF4A558F), Color(0xFFBD6220),
    Color(0xFF1E82AC), Color(0xFF76259C), Color(0xFF25319C), Color(0xFF9C2542),
    Color(0xFF9C7425), Color(0xFF479775),
)
private const val MAX_ARTWORK_SOURCE_BYTES = 15 * 1024 * 1024
private const val MAX_ARTWORK_BYTES = 5 * 1024 * 1024
