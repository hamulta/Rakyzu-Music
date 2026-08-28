package my.id.rakyzumusic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import my.id.rakyzumusic.core.data.auth.AuthActionResult
import my.id.rakyzumusic.core.data.auth.AuthFailure
import my.id.rakyzumusic.core.data.auth.AuthRepository
import my.id.rakyzumusic.core.data.auth.AuthSessionState
import my.id.rakyzumusic.core.designsystem.theme.RakyzuAqua
import my.id.rakyzumusic.core.designsystem.theme.RakyzuBlack
import my.id.rakyzumusic.core.designsystem.theme.RakyzuPurple
import my.id.rakyzumusic.core.designsystem.theme.RakyzuSurface
import my.id.rakyzumusic.feature.auth.AuthRoute
import my.id.rakyzumusic.feature.home.HomeScreen
import my.id.rakyzumusic.navigation.RakyzuRoute
import my.id.rakyzumusic.navigation.selectTopLevelRoute
import kotlinx.coroutines.launch

private data class TopLevelDestination(
    val route: RakyzuRoute,
    val label: String,
    val icon: ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(RakyzuRoute.Home, "Home", Icons.Rounded.Home),
    TopLevelDestination(RakyzuRoute.Search, "Search", Icons.Rounded.Search),
    TopLevelDestination(RakyzuRoute.Library, "Your Library", Icons.Rounded.LibraryMusic),
)

@Composable
fun RakyzuMusicApp(
    versionName: String,
    authRepository: AuthRepository,
    modifier: Modifier = Modifier,
) {
    val sessionState by authRepository.sessionState.collectAsStateWithLifecycle()

    when (val state = sessionState) {
        AuthSessionState.Initializing -> SessionLoadingScreen(modifier)
        AuthSessionState.SignedOut -> AuthRoute(
            repository = authRepository,
            modifier = modifier,
        )
        is AuthSessionState.RecoveryRequired -> AuthRoute(
            repository = authRepository,
            sessionMessage = state.failure.toSessionMessage(),
            modifier = modifier,
        )
        is AuthSessionState.SignedIn -> AuthenticatedRakyzuMusicApp(
            versionName = versionName,
            email = state.email,
            authRepository = authRepository,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3AdaptiveNavigationSuiteApi::class)
@Composable
private fun AuthenticatedRakyzuMusicApp(
    versionName: String,
    email: String?,
    authRepository: AuthRepository,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(RakyzuRoute.Home)
    val currentRoute = backStack.lastOrNull()
    val coroutineScope = rememberCoroutineScope()
    var showAccount by remember { mutableStateOf(false) }
    var isSigningOut by remember { mutableStateOf(false) }
    var signOutMessage by remember { mutableStateOf<String?>(null) }

    if (showAccount) {
        AccountSheet(
            email = email,
            isSigningOut = isSigningOut,
            message = signOutMessage,
            onDismiss = { if (!isSigningOut) showAccount = false },
            onSignOut = {
                if (!isSigningOut) {
                    coroutineScope.launch {
                        isSigningOut = true
                        signOutMessage = null
                        when (val result = authRepository.signOut()) {
                            AuthActionResult.Success -> showAccount = false
                            is AuthActionResult.ConfirmationRequired -> showAccount = false
                            is AuthActionResult.Failure -> {
                                signOutMessage = result.reason.toSignOutMessage()
                            }
                        }
                        isSigningOut = false
                    }
                }
            },
        )
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            topLevelDestinations.forEach { destination ->
                item(
                    selected = currentRoute == destination.route,
                    onClick = { selectTopLevelRoute(backStack, destination.route) },
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = null,
                        )
                    },
                    label = { Text(destination.label) },
                )
            }
        },
        modifier = modifier.fillMaxSize(),
        containerColor = RakyzuBlack,
        navigationSuiteColors = androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults.colors(
            navigationBarContainerColor = RakyzuSurface,
            navigationRailContainerColor = RakyzuSurface,
            navigationDrawerContainerColor = RakyzuSurface,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavDisplay(
                backStack = backStack,
                entryProvider = entryProvider {
                    entry<RakyzuRoute.Home> {
                        HomeScreen(
                            versionName = versionName,
                            onProfileClick = {
                                signOutMessage = null
                                showAccount = true
                            },
                        )
                    }
                    entry<RakyzuRoute.Search> {
                        FoundationDestination(
                            title = "Search",
                            message = "Search foundations arrive in the 0.2.x release line.",
                            icon = Icons.Rounded.Search,
                        )
                    }
                    entry<RakyzuRoute.Library> {
                        FoundationDestination(
                            title = "Your Library",
                            message = "Library foundations arrive in the 0.3.x release line.",
                            icon = Icons.Rounded.LibraryMusic,
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            MiniPlayer(modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun SessionLoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF211240), RakyzuBlack))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(RakyzuPurple, RakyzuAqua))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "R",
                    color = RakyzuBlack,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                )
            }
            CircularProgressIndicator(color = RakyzuAqua)
            Text(
                text = "Restoring your secure session",
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSheet(
    email: String?,
    isSigningOut: Boolean,
    message: String?,
    onDismiss: () -> Unit,
    onSignOut: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = RakyzuSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Your account",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = email ?: "Signed in to Rakyzu Music",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "Signing out removes this device's encrypted session. Other devices stay signed in.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (message != null) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                onClick = onSignOut,
                enabled = !isSigningOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                if (isSigningOut) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Sign out", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun AuthFailure.toSessionMessage(): String = when (this) {
    AuthFailure.InvalidConfiguration -> "Sign-in is unavailable because this build is missing public configuration."
    AuthFailure.NetworkUnavailable -> "Your saved session could not be refreshed. Check your connection or sign in again."
    AuthFailure.SessionExpired -> "Your session expired. Sign in again to continue."
    else -> "Your secure session could not be restored. Sign in again to continue."
}

private fun AuthFailure.toSignOutMessage(): String = when (this) {
    AuthFailure.NetworkUnavailable -> "Check your connection and try signing out again."
    else -> "Unable to sign out right now. Try again."
}

@Composable
private fun FoundationDestination(
    title: String,
    message: String,
    icon: ImageVector,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF211240), RakyzuBlack, RakyzuBlack),
                ),
            )
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 28.dp)
            .padding(bottom = 76.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = RakyzuAqua,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun MiniPlayer(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        color = Color(0xFF302548),
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(RakyzuPurple, RakyzuAqua))),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(RakyzuBlack.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Midnight Signal",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Rakyzu Sessions",
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = {}) {
                Icon(Icons.Rounded.MoreVert, "More playback options", tint = Color.White)
            }
            IconButton(onClick = {}) {
                Icon(Icons.Rounded.Pause, "Pause Midnight Signal", tint = Color.White)
            }
        }
    }
}
