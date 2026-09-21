package my.id.rakyzumusic.feature.profile

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import my.id.rakyzumusic.core.data.profile.ListenerProfile
import my.id.rakyzumusic.core.data.profile.ProfileAppearance

@Composable
fun IdentityName(
    profile: ListenerProfile,
    modifier: Modifier = Modifier,
) {
    val palette = rolePalette(profile.role, profile.identityKind)
    val transition = rememberInfiniteTransition(label = "identity-name")
    val animatedColor by transition.animateColor(
        initialValue = palette.first,
        targetValue = palette.second,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = palette.third),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "identity-color",
    )
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = profile.displayName,
            color = if (profile.appearance == ProfileAppearance.Role && profile.verified) {
                animatedColor
            } else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
        )
        if (profile.verified) {
            Text(
                text = "✓",
                color = Color(0xFF2F8CFF),
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
fun ArtistWelcomeDialog(
    profile: ListenerProfile,
    isWorking: Boolean,
    onConfirm: (String) -> Unit,
) {
    val artist = profile.artist ?: return
    val termsVersion = artist.termsVersion?.takeIf(String::isNotBlank) ?: return
    if (!artist.requiresConsent) return
    var checked by remember(artist.id, artist.termsVersion) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Welcome to the world of Artists") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${artist.name}, your Artist profile is ready.", fontWeight = FontWeight.Bold)
                Text(artist.termsSummary.orEmpty())
                Text(
                    artist.termsText.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { checked = it },
                        enabled = !isWorking,
                    )
                    Text("I have read and agree to the Artist terms")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(termsVersion) },
                enabled = checked && !isWorking,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (isWorking) "Confirming…" else "Confirm Artist profile") }
        },
    )
}

private fun rolePalette(role: String?, identityKind: String): Triple<Color, Color, Int> = when (role) {
    "ceo" -> Triple(Color(0xFFFFC857), Color(0xFFFF3D81), 1_700)
    "c_level_executive" -> Triple(Color(0xFF9D7BFF), Color(0xFF46E5FF), 2_100)
    "manager" -> Triple(Color(0xFF42F59E), Color(0xFF2F8CFF), 2_400)
    "supervisor" -> Triple(Color(0xFFFF8A3D), Color(0xFFFFD166), 2_700)
    "officer" -> Triple(Color(0xFF3DE0D0), Color(0xFF79A7FF), 3_000)
    else -> if (identityKind == "artist") {
        Triple(Color(0xFFE66BFF), Color(0xFF53E4FF), 2_300)
    } else Triple(Color.White, Color.White, 3_000)
}
