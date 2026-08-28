package my.id.rakyzumusic.feature.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import my.id.rakyzumusic.core.designsystem.theme.RakyzuAqua
import my.id.rakyzumusic.core.designsystem.theme.RakyzuBlack
import my.id.rakyzumusic.core.designsystem.theme.RakyzuMusicTheme
import my.id.rakyzumusic.core.designsystem.theme.RakyzuPurple
import my.id.rakyzumusic.core.designsystem.theme.RakyzuPurpleSoft
import my.id.rakyzumusic.core.designsystem.theme.RakyzuSurface
import my.id.rakyzumusic.core.designsystem.theme.RakyzuSurfaceRaised

private data class ShelfItem(
    val title: String,
    val subtitle: String,
    val colors: List<Color>,
)

private val recentlyPlayed = listOf(
    ShelfItem("Midnight Signal", "Rakyzu Sessions", listOf(Color(0xFF3B1B75), RakyzuAqua)),
    ShelfItem("Afterglow", "Fresh electronic", listOf(Color(0xFF7A2457), RakyzuPurpleSoft)),
    ShelfItem("Focus Flow", "Instrumental mix", listOf(Color(0xFF123C5A), Color(0xFF59C7F1))),
)

private val madeForYou = listOf(
    ShelfItem("Daily Pulse", "Music shaped around you", listOf(Color(0xFF3D237A), Color(0xFFFF7AB6))),
    ShelfItem("New Horizons", "Emerging sounds this week", listOf(Color(0xFF14394A), RakyzuAqua)),
    ShelfItem("Night Drive", "Low light, high energy", listOf(Color(0xFF502567), Color(0xFFFF8B5C))),
)

@Composable
fun HomeScreen(
    versionName: String,
    modifier: Modifier = Modifier,
) {
    var selectedFilter by remember { mutableStateOf("Music") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFF211240),
                        0.28f to RakyzuBlack,
                        1f to RakyzuBlack,
                    ),
                ),
            ),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 196.dp),
        ) {
            item {
                HomeHeader(versionName = versionName)
            }
            item {
                FilterRow(
                    selectedFilter = selectedFilter,
                    onFilterSelected = { selectedFilter = it },
                )
            }
            item {
                FeaturedCard()
            }
            item {
                Shelf(
                    title = "Jump back in",
                    items = recentlyPlayed,
                )
            }
            item {
                Shelf(
                    title = "Made for you",
                    items = madeForYou,
                )
            }
        }

        PlayerAndNavigation(
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun HomeHeader(versionName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(RakyzuPurple, RakyzuAqua)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "R",
                color = RakyzuBlack,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Good evening",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "Rakyzu Music · v$versionName",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        IconButton(onClick = {}) {
            Icon(
                imageVector = Icons.Rounded.NotificationsNone,
                contentDescription = "Notifications",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        IconButton(onClick = {}) {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = "Profile",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun FilterRow(
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
) {
    val filters = listOf("Music", "Podcasts", "New releases")
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(filters) { filter ->
            val selected = filter == selectedFilter
            FilterChip(
                selected = selected,
                onClick = { onFilterSelected(filter) },
                label = { Text(filter) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = RakyzuSurfaceRaised.copy(alpha = 0.82f),
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    selectedContainerColor = RakyzuAqua,
                    selectedLabelColor = RakyzuBlack,
                ),
                border = null,
            )
        }
    }
}

@Composable
private fun FeaturedCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 22.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF4B248A), Color(0xFF1E6572)),
                    ),
                )
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AlbumCover(
                colors = listOf(RakyzuPurpleSoft, RakyzuAqua),
                modifier = Modifier.size(112.dp),
            )
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "RAKYZU ORIGINAL",
                    color = RakyzuAqua,
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Sound without limits",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "Your new listening space starts here.",
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                )
                Spacer(Modifier.height(10.dp))
                Surface(
                    onClick = {},
                    shape = CircleShape,
                    color = RakyzuAqua,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = RakyzuBlack,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Play",
                            color = RakyzuBlack,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Shelf(
    title: String,
    items: List<ShelfItem>,
) {
    Column(modifier = Modifier.padding(bottom = 28.dp)) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(items) { item ->
                Column(modifier = Modifier.width(156.dp)) {
                    AlbumCover(
                        colors = item.colors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = item.title,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumCover(
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(RakyzuBlack.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(34.dp),
            )
        }
    }
}

@Composable
private fun PlayerAndNavigation(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            color = Color(0xFF302548),
            shape = RoundedCornerShape(14.dp),
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumCover(
                    colors = listOf(RakyzuPurple, RakyzuAqua),
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Midnight Signal",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    Text(
                        text = "Rakyzu Sessions",
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Rounded.MoreVert, "More options", tint = Color.White)
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Rounded.Pause, "Pause", tint = Color.White)
                }
            }
        }
        NavigationBar(
            containerColor = RakyzuSurface.copy(alpha = 0.98f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            BottomDestination.entries.forEach { destination ->
                NavigationBarItem(
                    selected = destination == BottomDestination.Home,
                    onClick = {},
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label,
                        )
                    },
                    label = { Text(destination.label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = RakyzuBlack,
                        selectedTextColor = RakyzuAqua,
                        indicatorColor = RakyzuAqua,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

private enum class BottomDestination(
    val label: String,
    val icon: ImageVector,
) {
    Home("Home", Icons.Rounded.Home),
    Search("Search", Icons.Rounded.Search),
    Library("Your Library", Icons.Rounded.LibraryMusic),
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenPreview() {
    RakyzuMusicTheme(darkTheme = true) {
        HomeScreen(versionName = "0.0.1")
    }
}
