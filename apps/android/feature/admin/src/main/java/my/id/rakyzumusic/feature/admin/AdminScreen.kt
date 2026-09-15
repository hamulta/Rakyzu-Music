package my.id.rakyzumusic.feature.admin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.id.rakyzumusic.core.data.admin.AdminDashboard
import my.id.rakyzumusic.core.data.admin.AdminRepository
import my.id.rakyzumusic.core.data.admin.ModerationCase
import my.id.rakyzumusic.core.data.admin.StaffPermission
import my.id.rakyzumusic.core.data.admin.StaffRole
import my.id.rakyzumusic.core.designsystem.theme.RakyzuAqua
import my.id.rakyzumusic.core.designsystem.theme.RakyzuBlack
import my.id.rakyzumusic.core.designsystem.theme.RakyzuSurface

@Composable
fun AdminRoute(
    repository: AdminRepository,
    viewModel: AdminViewModel = viewModel(factory = AdminViewModel.factory(repository)),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AdminScreen(
        state = state,
        onRefresh = viewModel::refresh,
        onCreateArtist = viewModel::createArtist,
        onCreateAlbum = viewModel::createAlbum,
        onCreateTrack = viewModel::createTrack,
        onUploadAudio = viewModel::uploadAudio,
        onPublishAlbum = viewModel::publishAlbum,
        onCreateModerationCase = viewModel::createModerationCase,
        onModerate = viewModel::moderate,
        onAssignStaff = viewModel::assignStaff,
        modifier = modifier,
    )
}

@Composable
internal fun AdminScreen(
    state: AdminUiState,
    onRefresh: () -> Unit,
    onCreateArtist: (String) -> Unit,
    onCreateAlbum: (String, String, String?) -> Unit,
    onCreateTrack: (String, String, Int, Int, Int, Boolean) -> Unit,
    onUploadAudio: (String, String, ByteArray) -> Unit,
    onPublishAlbum: (String) -> Unit,
    onCreateModerationCase: (String, String, String, Int) -> Unit,
    onModerate: (String, String, String) -> Unit,
    onAssignStaff: (String, StaffRole, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dashboard = state.dashboard
    when {
        state.isLoading && dashboard == null -> LoadingAdmin(modifier)
        dashboard == null -> UnavailableAdmin(state.message, onRefresh, modifier)
        !dashboard.context.isStaff || !dashboard.context.can(StaffPermission.AdminAccess) ->
            ListenerAdminBoundary(onRefresh, modifier)
        else -> StaffAdminPanel(
            dashboard = dashboard,
            state = state,
            onRefresh = onRefresh,
            onCreateArtist = onCreateArtist,
            onCreateAlbum = onCreateAlbum,
            onCreateTrack = onCreateTrack,
            onUploadAudio = onUploadAudio,
            onPublishAlbum = onPublishAlbum,
            onCreateModerationCase = onCreateModerationCase,
            onModerate = onModerate,
            onAssignStaff = onAssignStaff,
            modifier = modifier,
        )
    }
}

@Composable
private fun StaffAdminPanel(
    dashboard: AdminDashboard,
    state: AdminUiState,
    onRefresh: () -> Unit,
    onCreateArtist: (String) -> Unit,
    onCreateAlbum: (String, String, String?) -> Unit,
    onCreateTrack: (String, String, Int, Int, Int, Boolean) -> Unit,
    onUploadAudio: (String, String, ByteArray) -> Unit,
    onPublishAlbum: (String) -> Unit,
    onCreateModerationCase: (String, String, String, Int) -> Unit,
    onModerate: (String, String, String) -> Unit,
    onAssignStaff: (String, StaffRole, Boolean) -> Unit,
    modifier: Modifier,
) {
    val access = dashboard.context
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("admin-panel"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Rakyzu Control Room",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    access.displayRole ?: access.role?.displayName.orEmpty(),
                    color = RakyzuAqua,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (access.fullAccess) "Full organization access" else
                        "Server-verified least-privilege access",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onRefresh, enabled = !state.isWorking) {
                    Text("Refresh control room")
                }
            }
        }
        state.message?.let { message ->
            item {
                Text(
                    message,
                    color = if (state.messageIsError) MaterialTheme.colorScheme.error else RakyzuAqua,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        if (state.isWorking) item { CircularProgressIndicator(color = RakyzuAqua) }

        if (access.can(StaffPermission.ModerationView)) {
            item {
                ModerationComposer(
                    enabled = !state.isWorking && access.can(StaffPermission.ModerationTriage),
                    onCreate = onCreateModerationCase,
                )
            }
            item {
                SectionHeading("Moderation queue", "${dashboard.moderationCases.size} active cases")
            }
            if (dashboard.moderationCases.isEmpty()) {
                item { EmptyCard("No active moderation cases") }
            } else {
                items(dashboard.moderationCases, key = { it.id }) { moderationCase ->
                    ModerationCaseCard(
                        moderationCase = moderationCase,
                        canTriage = access.can(StaffPermission.ModerationTriage),
                        canDecide = access.can(StaffPermission.ModerationDecide),
                        enabled = !state.isWorking,
                        onAction = onModerate,
                    )
                }
            }
        }

        if (access.can(StaffPermission.CatalogDraft)) {
            item {
                CatalogComposer(
                    canUpload = access.can(StaffPermission.CatalogUploadAudio),
                    canPublish = access.can(StaffPermission.CatalogPublish),
                    enabled = !state.isWorking,
                    onCreateArtist = onCreateArtist,
                    onCreateAlbum = onCreateAlbum,
                    onCreateTrack = onCreateTrack,
                    onUploadAudio = onUploadAudio,
                    onPublishAlbum = onPublishAlbum,
                )
            }
            item {
                SectionHeading("Catalog workspace", "${dashboard.catalog.size} recent records")
            }
            items(dashboard.catalog.take(30), key = { "${it.kind}-${it.id}" }) { draft ->
                InfoCard(
                    title = draft.title,
                    detail = buildString {
                        append(draft.kind.replaceFirstChar(Char::uppercase))
                        append(if (draft.published) " • Published" else " • Draft")
                        if (draft.hasStandardAudio != null) {
                            append(if (draft.hasStandardAudio == true) " • Audio ready" else " • Audio required")
                        }
                    },
                    id = draft.id,
                )
            }
        }

        if (access.can(StaffPermission.StaffManage)) {
            item {
                StaffComposer(
                    actorRole = access.role ?: StaffRole.Officer,
                    enabled = !state.isWorking,
                    onAssignStaff = onAssignStaff,
                )
            }
            item { SectionHeading("Organization access", "${dashboard.staff.size} assignments") }
            items(dashboard.staff, key = { it.userId }) { member ->
                InfoCard(
                    title = member.displayName,
                    detail = "${member.role.displayName} • ${member.email} • ${if (member.active) "Active" else "Disabled"}",
                    id = member.userId,
                )
            }
        }
    }
}

@Composable
private fun ModerationComposer(
    enabled: Boolean,
    onCreate: (String, String, String, Int) -> Unit,
) {
    var type by remember { mutableStateOf("playlist") }
    var subjectId by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var priority by remember { mutableIntStateOf(50) }
    AdminSection("Open moderation case") {
        Text("Officer and above can triage reports. Final decisions require Supervisor or above.")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf("profile", "playlist", "artist", "album", "track").forEach { value ->
                FilterChip(
                    selected = type == value,
                    onClick = { type = value },
                    label = { Text(value.replaceFirstChar(Char::uppercase)) },
                    enabled = enabled,
                )
            }
        }
        OutlinedTextField(
            value = subjectId,
            onValueChange = { subjectId = it },
            label = { Text("Subject UUID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        )
        OutlinedTextField(
            value = reason,
            onValueChange = { reason = it.take(500) },
            label = { Text("Reason") },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(25, 50, 75, 100).forEach { value ->
                FilterChip(
                    selected = priority == value,
                    onClick = { priority = value },
                    label = { Text("P$value") },
                    enabled = enabled,
                )
            }
        }
        PrimaryAction(
            "Create case",
            enabled && subjectId.isNotBlank() && reason.trim().length >= 3,
        ) { onCreate(type, subjectId, reason, priority) }
    }
}

@Composable
private fun ModerationCaseCard(
    moderationCase: ModerationCase,
    canTriage: Boolean,
    canDecide: Boolean,
    enabled: Boolean,
    onAction: (String, String, String) -> Unit,
) {
    var notes by remember(moderationCase.id) { mutableStateOf("") }
    AdminSection("${moderationCase.subjectType.replaceFirstChar(Char::uppercase)} • P${moderationCase.priority}") {
        Text(moderationCase.reason, style = MaterialTheme.typography.bodyLarge)
        Text(
            "${moderationCase.status.replace('_', ' ')} • ${moderationCase.subjectId}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it.take(500) },
            label = { Text("Decision notes") },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        )
        if (canTriage) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedButton(onClick = { onAction(moderationCase.id, "claim", notes) }, enabled = enabled) {
                    Text("Claim")
                }
                OutlinedButton(onClick = { onAction(moderationCase.id, "escalate", notes) }, enabled = enabled) {
                    Text("Escalate")
                }
            }
        }
        if (canDecide) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onAction(moderationCase.id, "approve", notes) },
                    enabled = enabled && notes.trim().length >= 3,
                ) { Text("Action") }
                OutlinedButton(
                    onClick = { onAction(moderationCase.id, "dismiss", notes) },
                    enabled = enabled && notes.trim().length >= 3,
                ) { Text("Dismiss") }
            }
        }
    }
}

@Composable
private fun CatalogComposer(
    canUpload: Boolean,
    canPublish: Boolean,
    enabled: Boolean,
    onCreateArtist: (String) -> Unit,
    onCreateAlbum: (String, String, String?) -> Unit,
    onCreateTrack: (String, String, Int, Int, Int, Boolean) -> Unit,
    onUploadAudio: (String, String, ByteArray) -> Unit,
    onPublishAlbum: (String) -> Unit,
) {
    var artistName by remember { mutableStateOf("") }
    var artistId by remember { mutableStateOf("") }
    var albumTitle by remember { mutableStateOf("") }
    var releaseDate by remember { mutableStateOf("") }
    var albumId by remember { mutableStateOf("") }
    var trackTitle by remember { mutableStateOf("") }
    var durationSeconds by remember { mutableStateOf("180") }
    var trackNumber by remember { mutableStateOf("1") }
    var explicit by remember { mutableStateOf(false) }
    var trackId by remember { mutableStateOf("") }
    var quality by remember { mutableStateOf("standard") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && trackId.isNotBlank()) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) { readBoundedAudio(context, uri) }
                onUploadAudio(trackId, quality, bytes ?: ByteArray(0))
            }
        }
    }

    AdminSection("Catalog publishing") {
        Text("Manager drafts metadata. C-Level and CEO upload audio and publish releases.")
        OutlinedTextField(
            artistName, { artistName = it.take(120) }, label = { Text("New artist name") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = enabled,
        )
        PrimaryAction("Create artist profile", enabled && artistName.isNotBlank()) {
            onCreateArtist(artistName)
        }
        OutlinedTextField(
            artistId, { artistId = it }, label = { Text("Artist UUID") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = enabled,
        )
        OutlinedTextField(
            albumTitle, { albumTitle = it.take(160) }, label = { Text("Album title") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = enabled,
        )
        OutlinedTextField(
            releaseDate, { releaseDate = it.take(10) }, label = { Text("Release date (YYYY-MM-DD, optional)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = enabled,
        )
        PrimaryAction("Create album draft", enabled && artistId.isNotBlank() && albumTitle.isNotBlank()) {
            onCreateAlbum(artistId, albumTitle, releaseDate.ifBlank { null })
        }
        OutlinedTextField(
            albumId, { albumId = it }, label = { Text("Album UUID") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = enabled,
        )
        OutlinedTextField(
            trackTitle, { trackTitle = it.take(160) }, label = { Text("Track title") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = enabled,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                durationSeconds, { durationSeconds = it.filter(Char::isDigit).take(5) },
                label = { Text("Seconds") }, modifier = Modifier.weight(1f), enabled = enabled,
            )
            OutlinedTextField(
                trackNumber, { trackNumber = it.filter(Char::isDigit).take(3) },
                label = { Text("Track #") }, modifier = Modifier.weight(1f), enabled = enabled,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Explicit content", modifier = Modifier.weight(1f))
            Switch(checked = explicit, onCheckedChange = { explicit = it }, enabled = enabled)
        }
        PrimaryAction(
            "Create track draft",
            enabled && albumId.isNotBlank() && trackTitle.isNotBlank() &&
                durationSeconds.toIntOrNull() != null && trackNumber.toIntOrNull() != null,
        ) {
            onCreateTrack(
                albumId,
                trackTitle,
                (durationSeconds.toIntOrNull() ?: 0) * 1000,
                1,
                trackNumber.toIntOrNull() ?: 0,
                explicit,
            )
        }
        if (canUpload) {
            OutlinedTextField(
                trackId, { trackId = it }, label = { Text("Track UUID for audio") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = enabled,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("low", "standard", "high").forEach { value ->
                    FilterChip(
                        selected = quality == value,
                        onClick = { quality = value },
                        label = { Text(value.replaceFirstChar(Char::uppercase)) },
                        enabled = enabled,
                    )
                }
            }
            PrimaryAction("Select MP3 and upload", enabled && trackId.isNotBlank()) {
                audioPicker.launch(arrayOf("audio/mpeg", "audio/mp3"))
            }
        }
        if (canPublish) {
            PrimaryAction("Publish album and tracks", enabled && albumId.isNotBlank()) {
                onPublishAlbum(albumId)
            }
        }
    }
}

@Composable
private fun StaffComposer(
    actorRole: StaffRole,
    enabled: Boolean,
    onAssignStaff: (String, StaffRole, Boolean) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(StaffRole.Officer) }
    var active by remember { mutableStateOf(true) }
    val assignableRoles = StaffRole.entries.filter { candidate ->
        actorRole == StaffRole.Ceo || candidate.ordinal < actorRole.ordinal
    }
    AdminSection("Organization roles") {
        Text("Only a higher office can manage lower ranks. Only the CEO can grant CEO access.")
        OutlinedTextField(
            value = email,
            onValueChange = { email = it.take(254) },
            label = { Text("Account email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = enabled,
        )
        assignableRoles.forEach { value ->
            FilterChip(
                selected = role == value,
                onClick = { role = value },
                label = { Text(value.displayName) },
                enabled = enabled,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Active access", modifier = Modifier.weight(1f))
            Switch(checked = active, onCheckedChange = { active = it }, enabled = enabled)
        }
        PrimaryAction("Apply role assignment", enabled && email.contains('@')) {
            onAssignStaff(email, role, active)
        }
    }
}

@Composable
private fun AdminSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = RakyzuSurface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun PrimaryAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = RakyzuAqua, contentColor = RakyzuBlack),
    ) { Text(label, fontWeight = FontWeight.Bold) }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoCard(title: String, detail: String, id: String) {
    AdminSection(title) {
        Text(detail, color = RakyzuAqua)
        Text(id, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EmptyCard(message: String) = InfoCard(message, "Queue is clear", "Rakyzu moderation")

@Composable
private fun LoadingAdmin(modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = RakyzuAqua)
        Text("Verifying staff access", modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun UnavailableAdmin(message: String?, onRefresh: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message ?: "Admin services are unavailable")
        OutlinedButton(onClick = onRefresh, modifier = Modifier.padding(top = 16.dp)) {
            Text("Try again")
        }
    }
}

@Composable
private fun ListenerAdminBoundary(onRefresh: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Admin Panel unavailable", style = MaterialTheme.typography.headlineSmall)
        Text(
            "This account is a listener and has no organization role.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        OutlinedButton(onClick = onRefresh) { Text("Recheck access") }
    }
}

private fun readBoundedAudio(context: android.content.Context, uri: Uri): ByteArray? {
    val output = ByteArrayOutputStream()
    return context.contentResolver.openInputStream(uri)?.use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_AUDIO_BYTES) return@use null
            output.write(buffer, 0, count)
        }
        output.toByteArray().takeIf { it.size >= 4 }
    }
}

private const val MAX_AUDIO_BYTES = 50 * 1024 * 1024
