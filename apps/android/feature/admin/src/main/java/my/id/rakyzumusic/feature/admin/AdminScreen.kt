package my.id.rakyzumusic.feature.admin

import android.content.Intent
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
import androidx.compose.runtime.LaunchedEffect
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
import my.id.rakyzumusic.core.data.admin.AuditSummary
import my.id.rakyzumusic.core.data.admin.CatalogReview
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
    val context = LocalContext.current
    LaunchedEffect(state.auditExportCsv) {
        val csv = state.auditExportCsv ?: return@LaunchedEffect
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Rakyzu Music audit export")
            putExtra(Intent.EXTRA_TEXT, csv)
        }
        context.startActivity(Intent.createChooser(share, "Export Rakyzu audit").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        viewModel.consumeAuditExport()
    }
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
        onEnforceContent = viewModel::enforceContent,
        onAssignCatalogTeam = viewModel::assignCatalogTeam,
        onCreateCatalogLabel = viewModel::createCatalogLabel,
        onLinkCatalogLabelArtist = viewModel::linkCatalogLabelArtist,
        onUploadArtwork = viewModel::uploadArtwork,
        onSubmitReview = viewModel::submitReview,
        onDecideReview = viewModel::decideReview,
        onScheduleAlbum = viewModel::scheduleAlbum,
        onExportAudit = viewModel::exportAudit,
        onSetAuditRetention = viewModel::setAuditRetention,
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
    onEnforceContent: (String, String, String, String, String?) -> Unit,
    onAssignCatalogTeam: (String, String, String, String, Boolean) -> Unit,
    onCreateCatalogLabel: (String) -> Unit,
    onLinkCatalogLabelArtist: (String, String) -> Unit,
    onUploadArtwork: (String, ByteArray) -> Unit,
    onSubmitReview: (String, String, String) -> Unit,
    onDecideReview: (String, String, String) -> Unit,
    onScheduleAlbum: (String, String) -> Unit,
    onExportAudit: (String?, String?) -> Unit,
    onSetAuditRetention: (Int) -> Unit,
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
            onEnforceContent = onEnforceContent,
            onAssignCatalogTeam = onAssignCatalogTeam,
            onCreateCatalogLabel = onCreateCatalogLabel,
            onLinkCatalogLabelArtist = onLinkCatalogLabelArtist,
            onUploadArtwork = onUploadArtwork,
            onSubmitReview = onSubmitReview,
            onDecideReview = onDecideReview,
            onScheduleAlbum = onScheduleAlbum,
            onExportAudit = onExportAudit,
            onSetAuditRetention = onSetAuditRetention,
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
    onEnforceContent: (String, String, String, String, String?) -> Unit,
    onAssignCatalogTeam: (String, String, String, String, Boolean) -> Unit,
    onCreateCatalogLabel: (String) -> Unit,
    onLinkCatalogLabelArtist: (String, String) -> Unit,
    onUploadArtwork: (String, ByteArray) -> Unit,
    onSubmitReview: (String, String, String) -> Unit,
    onDecideReview: (String, String, String) -> Unit,
    onScheduleAlbum: (String, String) -> Unit,
    onExportAudit: (String?, String?) -> Unit,
    onSetAuditRetention: (Int) -> Unit,
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

        if (access.can(StaffPermission.ContentEnforce)) {
            item {
                EnforcementComposer(
                    enabled = !state.isWorking,
                    onEnforce = onEnforceContent,
                )
            }
            item {
                SectionHeading(
                    "Enforcement history",
                    "${dashboard.governance.enforcements.size} recent reversible decisions",
                )
            }
            items(dashboard.governance.enforcements, key = { "enforcement-${it.id}" }) { event ->
                InfoCard(
                    title = "${event.subjectType.replaceFirstChar(Char::uppercase)} • ${event.action.replace('_', ' ')}",
                    detail = event.reason,
                    id = event.subjectId,
                )
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
            item {
                ReleaseGovernanceComposer(
                    canUpload = access.can(StaffPermission.CatalogUploadArtwork),
                    canReview = access.can(StaffPermission.CatalogReview),
                    canPublish = access.can(StaffPermission.CatalogPublish),
                    enabled = !state.isWorking,
                    onUploadArtwork = onUploadArtwork,
                    onSubmitReview = onSubmitReview,
                    onScheduleAlbum = onScheduleAlbum,
                )
            }
            item {
                SectionHeading("Catalog review", "${dashboard.governance.reviews.size} recent submissions")
            }
            items(dashboard.governance.reviews, key = { "review-${it.id}" }) { review ->
                ReviewCard(
                    review = review,
                    canReview = access.can(StaffPermission.CatalogReview),
                    enabled = !state.isWorking,
                    onDecision = onDecideReview,
                )
            }
            items(dashboard.governance.schedules, key = { "schedule-${it.albumId}" }) { schedule ->
                InfoCard(
                    title = "Scheduled release • ${schedule.status}",
                    detail = schedule.publishAt,
                    id = schedule.albumId,
                )
            }
        }

        if (access.can(StaffPermission.CatalogTeamManage)) {
            item {
                CatalogTeamComposer(
                    enabled = !state.isWorking,
                    onAssign = onAssignCatalogTeam,
                    onCreateLabel = onCreateCatalogLabel,
                    onLinkArtist = onLinkCatalogLabelArtist,
                )
            }
            item { SectionHeading("Artist and label teams", "${dashboard.governance.teams.size} scoped members") }
            items(dashboard.governance.labels, key = { "label-${it.id}" }) { label ->
                InfoCard(title = label.name, detail = "Label scope", id = label.id)
            }
            items(dashboard.governance.teams, key = { "team-${it.scopeType}-${it.scopeId}-${it.userId}" }) { member ->
                InfoCard(
                    title = "${member.scopeType.replaceFirstChar(Char::uppercase)} • ${member.accessLevel}",
                    detail = if (member.active) "Active scoped access" else "Disabled scoped access",
                    id = "${member.scopeId} • ${member.userId}",
                )
            }
        }

        if (access.can(StaffPermission.AuditView)) {
            item {
                AuditGovernanceCard(
                    summary = dashboard.governance.auditSummary,
                    canExport = access.can(StaffPermission.AuditExport),
                    canManage = access.can(StaffPermission.GovernanceManage),
                    enabled = !state.isWorking,
                    onExport = onExportAudit,
                    onSetRetention = onSetAuditRetention,
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
private fun EnforcementComposer(
    enabled: Boolean,
    onEnforce: (String, String, String, String, String?) -> Unit,
) {
    var subjectType by remember { mutableStateOf("track") }
    var subjectId by remember { mutableStateOf("") }
    var action by remember { mutableStateOf("quarantine") }
    var reason by remember { mutableStateOf("") }
    var caseId by remember { mutableStateOf("") }
    AdminSection("Content enforcement") {
        Text("Quarantine and takedown hide content immediately. Restore keeps every audit event.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("profile", "playlist", "artist", "album", "track").forEach { value ->
                FilterChip(
                    selected = subjectType == value,
                    onClick = { subjectType = value },
                    label = { Text(value.replaceFirstChar(Char::uppercase)) },
                    enabled = enabled,
                )
            }
        }
        OutlinedTextField(
            subjectId, { subjectId = it.take(36) }, label = { Text("Subject UUID") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = enabled,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("quarantine", "take_down", "restore").forEach { value ->
                FilterChip(
                    selected = action == value,
                    onClick = { action = value },
                    label = { Text(value.replace('_', ' ').replaceFirstChar(Char::uppercase)) },
                    enabled = enabled,
                )
            }
        }
        OutlinedTextField(
            reason, { reason = it.take(500) }, label = { Text("Required reason") },
            modifier = Modifier.fillMaxWidth(), enabled = enabled,
        )
        OutlinedTextField(
            caseId, { caseId = it.take(36) }, label = { Text("Moderation case UUID (optional)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = enabled,
        )
        PrimaryAction("Record enforcement", enabled && subjectId.length == 36 && reason.trim().length >= 5) {
            onEnforce(subjectType, subjectId, action, reason, caseId.ifBlank { null })
        }
    }
}

@Composable
private fun CatalogTeamComposer(
    enabled: Boolean,
    onAssign: (String, String, String, String, Boolean) -> Unit,
    onCreateLabel: (String) -> Unit,
    onLinkArtist: (String, String) -> Unit,
) {
    var scopeType by remember { mutableStateOf("artist") }
    var scopeId by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var accessLevel by remember { mutableStateOf("viewer") }
    var active by remember { mutableStateOf(true) }
    var labelName by remember { mutableStateOf("") }
    var linkLabelId by remember { mutableStateOf("") }
    var linkArtistId by remember { mutableStateOf("") }
    AdminSection("Scoped catalog team") {
        Text("Artist and label access never grants organization-wide Admin privileges.")
        OutlinedTextField(
            labelName, { labelName = it.take(120) }, label = { Text("New label name") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = enabled,
        )
        PrimaryAction("Create label", enabled && labelName.isNotBlank()) { onCreateLabel(labelName) }
        OutlinedTextField(
            linkLabelId, { linkLabelId = it.take(36) }, label = { Text("Label UUID to link") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = enabled,
        )
        OutlinedTextField(
            linkArtistId, { linkArtistId = it.take(36) }, label = { Text("Artist UUID to link") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = enabled,
        )
        PrimaryAction("Link artist to label", enabled && linkLabelId.length == 36 && linkArtistId.length == 36) {
            onLinkArtist(linkLabelId, linkArtistId)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("artist", "label").forEach { value ->
                FilterChip(
                    selected = scopeType == value,
                    onClick = { scopeType = value },
                    label = { Text(value.replaceFirstChar(Char::uppercase)) },
                    enabled = enabled,
                )
            }
        }
        OutlinedTextField(
            scopeId, { scopeId = it.take(36) }, label = { Text("Artist or label UUID") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = enabled,
        )
        OutlinedTextField(
            email, { email = it.take(254) }, label = { Text("Existing account email") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = enabled,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("viewer", "editor", "admin").forEach { value ->
                FilterChip(
                    selected = accessLevel == value,
                    onClick = { accessLevel = value },
                    label = { Text(value.replaceFirstChar(Char::uppercase)) },
                    enabled = enabled,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Active membership", modifier = Modifier.weight(1f))
            Switch(checked = active, onCheckedChange = { active = it }, enabled = enabled)
        }
        PrimaryAction("Apply scoped access", enabled && scopeId.length == 36 && email.contains('@')) {
            onAssign(scopeType, scopeId, email, accessLevel, active)
        }
    }
}

@Composable
private fun ReleaseGovernanceComposer(
    canUpload: Boolean,
    canReview: Boolean,
    canPublish: Boolean,
    enabled: Boolean,
    onUploadArtwork: (String, ByteArray) -> Unit,
    onSubmitReview: (String, String, String) -> Unit,
    onScheduleAlbum: (String, String) -> Unit,
) {
    var albumId by remember { mutableStateOf("") }
    var reviewType by remember { mutableStateOf("release") }
    var notes by remember { mutableStateOf("") }
    var publishAt by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val artworkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && albumId.length == 36) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) { readBoundedArtwork(context, uri) }
                onUploadArtwork(albumId, bytes ?: ByteArray(0))
            }
        }
    }
    AdminSection("Release review and scheduling") {
        Text("Artwork and release metadata require independent approval before publication.")
        OutlinedTextField(
            albumId, { albumId = it.take(36) }, label = { Text("Album UUID") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = enabled,
        )
        if (canUpload) {
            PrimaryAction("Select WebP artwork", enabled && albumId.length == 36) {
                artworkPicker.launch(arrayOf("image/webp"))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("artwork", "release").forEach { value ->
                FilterChip(
                    selected = reviewType == value,
                    onClick = { reviewType = value },
                    label = { Text("${value.replaceFirstChar(Char::uppercase)} review") },
                    enabled = enabled,
                )
            }
        }
        OutlinedTextField(
            notes, { notes = it.take(500) }, label = { Text("Submission notes") },
            modifier = Modifier.fillMaxWidth(), enabled = enabled,
        )
        PrimaryAction("Submit for independent review", enabled && albumId.length == 36) {
            onSubmitReview(reviewType, albumId, notes)
        }
        if (canPublish) {
            OutlinedTextField(
                publishAt, { publishAt = it.take(40) },
                label = { Text("Publish time (ISO-8601)") },
                supportingText = { Text("Example: 2026-10-01T00:00:00Z") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = enabled,
            )
            PrimaryAction(
                "Schedule approved release",
                enabled && albumId.length == 36 && publishAt.contains('T'),
            ) { onScheduleAlbum(albumId, publishAt) }
        }
        if (canReview) Text("You cannot approve your own submission.")
    }
}

@Composable
private fun ReviewCard(
    review: CatalogReview,
    canReview: Boolean,
    enabled: Boolean,
    onDecision: (String, String, String) -> Unit,
) {
    var notes by remember(review.id) { mutableStateOf("") }
    AdminSection("${review.reviewType.replaceFirstChar(Char::uppercase)} • ${review.status}") {
        Text(review.submissionNotes.ifBlank { "No submission notes" })
        Text(review.targetId, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (canReview && review.status == "pending") {
            OutlinedTextField(
                notes, { notes = it.take(500) }, label = { Text("Independent decision notes") },
                modifier = Modifier.fillMaxWidth(), enabled = enabled,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onDecision(review.id, "approved", notes) },
                    enabled = enabled && notes.trim().length >= 3,
                ) { Text("Approve") }
                OutlinedButton(
                    onClick = { onDecision(review.id, "rejected", notes) },
                    enabled = enabled && notes.trim().length >= 3,
                ) { Text("Reject") }
            }
        }
    }
}

@Composable
private fun AuditGovernanceCard(
    summary: AuditSummary?,
    canExport: Boolean,
    canManage: Boolean,
    enabled: Boolean,
    onExport: (String?, String?) -> Unit,
    onSetRetention: (Int) -> Unit,
) {
    var operation by remember { mutableStateOf("") }
    var targetType by remember { mutableStateOf("") }
    var retentionDays by remember(summary?.retentionDays) {
        mutableStateOf(summary?.retentionDays?.toString().orEmpty())
    }
    AdminSection("Audit and anomaly controls") {
        if (summary == null) Text("Audit summary unavailable") else {
            Text("${summary.events24h} privileged events in 24 hours")
            Text("${summary.deniedOrEnforced24h} moderation or enforcement events")
            Text(
                if (summary.highActivity) "High activity detected—review the export" else "Activity within normal threshold",
                color = if (summary.highActivity) MaterialTheme.colorScheme.error else RakyzuAqua,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Text("Retention intent: ${summary.retentionDays} days")
        }
        if (canExport) {
            OutlinedTextField(
                operation, { operation = it.take(80) }, label = { Text("Exact operation filter (optional)") },
                modifier = Modifier.fillMaxWidth(), enabled = enabled,
            )
            OutlinedTextField(
                targetType, { targetType = it.take(40) }, label = { Text("Exact target type (optional)") },
                modifier = Modifier.fillMaxWidth(), enabled = enabled,
            )
            PrimaryAction("Export bounded CSV", enabled) {
                onExport(operation.ifBlank { null }, targetType.ifBlank { null })
            }
        }
        if (canManage) {
            OutlinedTextField(
                retentionDays, { retentionDays = it.filter(Char::isDigit).take(4) },
                label = { Text("Retention days (90–2555)") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, enabled = enabled,
            )
            val days = retentionDays.toIntOrNull()
            PrimaryAction("Update retention intent", enabled && days != null && days in 90..2555) {
                onSetRetention(days ?: 365)
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

private fun readBoundedArtwork(context: android.content.Context, uri: Uri): ByteArray? {
    val output = ByteArrayOutputStream()
    return context.contentResolver.openInputStream(uri)?.use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_ARTWORK_BYTES) return@use null
            output.write(buffer, 0, count)
        }
        output.toByteArray().takeIf { bytes ->
            bytes.size >= 12 && bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
                bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
                bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
                bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()
        }
    }
}

private const val MAX_AUDIO_BYTES = 50 * 1024 * 1024
private const val MAX_ARTWORK_BYTES = 5 * 1024 * 1024
