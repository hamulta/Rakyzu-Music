package my.id.rakyzumusic.feature.auth

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.id.rakyzumusic.core.data.auth.OAuthProvider

private val GatewayBackground = Color(0xFF121111)
private val GatewayButton = Color(0xFF1E1E1E)
private val GatewayBorder = Color(0xFFDAE7E7)
private val GatewayPrimary = Color(0xFF059FB4)
private val GatewayAccent = Color(0xFF7BEEFF)

@Composable
fun SignInGatewayScreen(
    state: AuthUiState,
    onBack: () -> Unit,
    onGoogleSelected: () -> Unit,
    onFacebookSelected: () -> Unit,
    onPasswordLogin: () -> Unit,
    onSignUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var contentVisible by remember { mutableStateOf(false) }
    var showAppleComingSoon by rememberSaveable { mutableStateOf(false) }
    val contentProgress by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "sign-in-gateway-content",
    )

    LaunchedEffect(Unit) {
        contentVisible = true
    }

    if (showAppleComingSoon) {
        AlertDialog(
            onDismissRequest = { showAppleComingSoon = false },
            title = {
                Text(
                    text = "Sign In With Apple",
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "Next-level quality is just around the corner. " +
                        "The ultimate upgrade you’ve been waiting for is coming soon!",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showAppleComingSoon = false },
                    modifier = Modifier.testTag("apple_coming_soon_confirm"),
                ) {
                    Text("Got it")
                }
            },
            containerColor = GatewayButton,
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.82f),
            modifier = Modifier.testTag("apple_coming_soon_dialog"),
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GatewayBackground)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 428.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(48.dp)
                    .testTag("sign_in_gateway_back"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                )
            }
        }

        Image(
            painter = painterResource(R.drawable.rakyzu_logo),
            contentDescription = "Rakyzu Music logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .widthIn(max = 300.dp)
                .aspectRatio(539f / 463f)
                .graphicsLayer {
                    alpha = contentProgress
                    translationY = (1f - contentProgress) * 28.dp.toPx()
                },
        )

        Text(
            text = "Let’s get you in",
            modifier = Modifier
                .widthIn(max = 420.dp)
                .semantics { heading() }
                .graphicsLayer { alpha = contentProgress },
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = 44.sp,
                lineHeight = 50.sp,
                letterSpacing = 1.1.sp,
                fontWeight = FontWeight.Bold,
            ),
        )

        Spacer(Modifier.height(40.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .graphicsLayer {
                    alpha = contentProgress
                    translationY = (1f - contentProgress) * 20.dp.toPx()
                },
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OAuthButton(
                provider = OAuthProvider.Google,
                label = "Continue with Google",
                iconResource = R.drawable.auth_google,
                activeProvider = state.externalProvider,
                onClick = onGoogleSelected,
            )
            OAuthButton(
                provider = OAuthProvider.Facebook,
                label = "Continue with Facebook",
                iconResource = R.drawable.auth_facebook,
                activeProvider = state.externalProvider,
                onClick = onFacebookSelected,
            )
            GatewayProviderButton(
                label = "Sign In With Apple",
                iconResource = R.drawable.auth_apple,
                testTag = "oauth_apple",
                enabled = state.externalProvider == null,
                isActive = false,
                onClick = { showAppleComingSoon = true },
            )

            if (state.message != null) {
                AuthMessage(
                    message = state.message,
                    isError = state.messageIsError,
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 376.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.8f)),
            )
            Text(
                text = "or",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.8f)),
            )
        }

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = onPasswordLogin,
            enabled = state.externalProvider == null,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .heightIn(min = 59.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(50),
                    ambientColor = GatewayPrimary,
                    spotColor = GatewayPrimary,
                )
                .testTag("sign_in_with_password"),
            colors = ButtonDefaults.buttonColors(
                containerColor = GatewayPrimary,
                contentColor = Color.White,
            ),
            shape = RoundedCornerShape(50),
        ) {
            Text(
                text = "Log in with a password",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.72.sp,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Don’t have an account?",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            TextButton(
                onClick = onSignUp,
                enabled = state.externalProvider == null,
                modifier = Modifier.testTag("sign_up_now"),
            ) {
                Text(
                    text = "Sign Up Now",
                    color = GatewayAccent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun OAuthButton(
    provider: OAuthProvider,
    label: String,
    iconResource: Int,
    activeProvider: OAuthProvider?,
    onClick: () -> Unit,
) {
    val isActive = activeProvider == provider
    GatewayProviderButton(
        label = label,
        iconResource = iconResource,
        testTag = "oauth_${provider.name.lowercase()}",
        enabled = activeProvider == null,
        isActive = isActive,
        onClick = onClick,
    )
}

@Composable
private fun GatewayProviderButton(
    label: String,
    iconResource: Int,
    testTag: String,
    enabled: Boolean,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 59.dp)
            .border(
                width = 0.5.dp,
                color = GatewayBorder,
                shape = RoundedCornerShape(10.dp),
            )
            .testTag(testTag),
        shape = RoundedCornerShape(10.dp),
        border = null,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = GatewayButton,
            contentColor = Color.White,
            disabledContainerColor = GatewayButton.copy(alpha = 0.72f),
            disabledContentColor = Color.White.copy(alpha = 0.62f),
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(iconResource),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(32.dp),
            )
            Text(
                text = label,
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.72.sp,
            )
            if (isActive) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(22.dp),
                    color = GatewayAccent,
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}
