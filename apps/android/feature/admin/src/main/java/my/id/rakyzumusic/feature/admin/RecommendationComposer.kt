package my.id.rakyzumusic.feature.admin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import my.id.rakyzumusic.core.data.admin.RecommendationCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun RecommendationComposer(
    enabled: Boolean,
    recommendations: List<RecommendationCard>,
    onSave: (String?, String, String?, Int, String?, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onUploadArtwork: (String, ByteArray) -> Unit,
) {
    var id by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    var trackId by remember { mutableStateOf("") }
    var position by remember { mutableStateOf("0") }
    var published by remember { mutableStateOf(true) }
    var artworkTargetId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val artworkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val targetId = artworkTargetId
        if (uri != null && targetId != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) { readBoundedArtwork(context, uri) }
                if (bytes != null) onUploadArtwork(targetId, bytes)
            }
        }
        artworkTargetId = null
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Home recommendation cards")
            Text("Create or edit a card, choose its playable track, and control its exact order.")
            recommendations.forEach { card ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("${card.position}. ${card.title}")
                        Text(if (card.published) "Published" else "Hidden")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    id = card.id
                                    title = card.title
                                    subtitle = card.subtitle.orEmpty()
                                    trackId = card.trackId.orEmpty()
                                    position = card.position.toString()
                                    published = card.published
                                },
                                enabled = enabled,
                                modifier = Modifier.weight(1f),
                            ) { Text("Edit") }
                            Button(
                                onClick = { onDelete(card.id) },
                                enabled = enabled,
                                modifier = Modifier.weight(1f),
                            ) { Text("Delete") }
                        }
                        Button(
                            onClick = {
                                artworkTargetId = card.id
                                artworkPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
                            },
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (card.hasArtwork) "Change card image" else "Add card image") }
                    }
                }
            }
            OutlinedTextField(
                id, { id = it }, label = { Text("Card UUID (empty creates new)") },
                modifier = Modifier.fillMaxWidth(), enabled = enabled, singleLine = true,
            )
            OutlinedTextField(
                title, { title = it.take(80) }, label = { Text("Card title") },
                modifier = Modifier.fillMaxWidth(), enabled = enabled, singleLine = true,
            )
            OutlinedTextField(
                subtitle, { subtitle = it.take(160) }, label = { Text("Subtitle (optional)") },
                modifier = Modifier.fillMaxWidth(), enabled = enabled,
            )
            OutlinedTextField(
                trackId, { trackId = it }, label = { Text("Featured audio track UUID (optional)") },
                modifier = Modifier.fillMaxWidth(), enabled = enabled, singleLine = true,
            )
            OutlinedTextField(
                position, { position = it.filter(Char::isDigit).take(4) },
                label = { Text("Home order") }, modifier = Modifier.fillMaxWidth(), enabled = enabled,
                singleLine = true,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Published", modifier = Modifier.weight(1f))
                Switch(checked = published, onCheckedChange = { published = it }, enabled = enabled)
            }
            Button(
                onClick = {
                    onSave(
                        id.ifBlank { null }, title, subtitle.ifBlank { null },
                        position.toIntOrNull() ?: 0, trackId.ifBlank { null }, published,
                    )
                },
                enabled = enabled && title.isNotBlank() &&
                    position.toIntOrNull()?.let { it in 0..1_000 } == true,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save recommendation card") }
        }
    }
}
