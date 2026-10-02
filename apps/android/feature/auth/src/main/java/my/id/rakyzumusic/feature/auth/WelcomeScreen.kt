package my.id.rakyzumusic.feature.auth

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val WelcomeBackground = Color(0xFF41C3D6)
private val WelcomeSurface = Color(0xFF121111)
private val WelcomeButton = Color(0xFF059FB4)
private val WelcomeAccent = Color(0xFF76D7E5)
private val WelcomeAccentBright = Color(0xFF7BEEFF)
private val WelcomeIndicator = Color(0xFF00C2CB)
private val WelcomeIndicatorMuted = Color(0xFFDAE7E7)

@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var contentVisible by remember { mutableStateOf(false) }
    val artworkAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "welcome-artwork-alpha",
    )
    val panelProgress by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = 90),
        label = "welcome-panel-progress",
    )

    LaunchedEffect(Unit) {
        contentVisible = true
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(WelcomeBackground),
    ) {
        val isShortLayout = maxHeight < 720.dp
        val contentWidth = minOf(maxWidth, 520.dp)
        val contentStart = (maxWidth - contentWidth) / 2
        val artworkTop = maxHeight * if (isShortLayout) 0.10f else 0.169f
        val panelHeight = maxHeight * if (isShortLayout) 0.50f else 0.433f

        Image(
            painter = painterResource(R.drawable.rakyzu_welcome_listener),
            contentDescription = "A listener enjoying music with headphones",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = artworkTop)
                .width(contentWidth)
                .aspectRatio(428f / 407f)
                .graphicsLayer { alpha = artworkAlpha },
        )

        WelcomeCircle(
            size = contentWidth * 0.348f,
            x = contentStart + contentWidth * 0.058f,
            y = maxHeight * 0.035f,
            alpha = artworkAlpha,
        )
        WelcomeCircle(
            size = contentWidth * 0.182f,
            x = contentStart + contentWidth * 0.701f,
            y = maxHeight * 0.084f,
            alpha = artworkAlpha,
        )
        WelcomeCircle(
            size = contentWidth * 0.241f,
            x = contentStart + contentWidth * 0.701f,
            y = maxHeight * 0.245f,
            alpha = artworkAlpha,
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(panelHeight)
                .graphicsLayer {
                    alpha = panelProgress
                    translationY = (1f - panelProgress) * 44.dp.toPx()
                }
                .clip(
                    RoundedCornerShape(
                        topStart = 54.dp,
                        topEnd = 54.dp,
                    ),
                )
                .background(WelcomeSurface)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 24.dp,
                    top = if (isShortLayout) 28.dp else 48.dp,
                    end = 24.dp,
                    bottom = 24.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = welcomeHeadline(),
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .semantics { heading() },
                color = Color.White,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = if (isShortLayout) 21.sp else 24.sp,
                    lineHeight = if (isShortLayout) 27.sp else 30.sp,
                    letterSpacing = 0.75.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )

            Spacer(Modifier.height(if (isShortLayout) 18.dp else 28.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(59.dp)
                        .height(9.dp)
                        .clip(CircleShape)
                        .background(WelcomeIndicator),
                )
                Box(
                    modifier = Modifier
                        .width(53.dp)
                        .height(9.dp)
                        .clip(CircleShape)
                        .background(WelcomeIndicatorMuted),
                )
            }

            Spacer(Modifier.height(if (isShortLayout) 18.dp else 32.dp))

            Button(
                onClick = onGetStarted,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .heightIn(min = 59.dp)
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(50),
                        ambientColor = WelcomeBackground,
                        spotColor = WelcomeBackground,
                    ),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WelcomeButton,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = "Get Started",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.72.sp,
                )
            }
        }
    }
}

@Composable
private fun WelcomeCircle(
    size: androidx.compose.ui.unit.Dp,
    x: androidx.compose.ui.unit.Dp,
    y: androidx.compose.ui.unit.Dp,
    alpha: Float,
) {
    Box(
        modifier = Modifier
            .offset(x = x, y = y)
            .size(size)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(WelcomeSurface),
    )
}

private fun welcomeHeadline(): AnnotatedString = buildAnnotatedString {
    append("From the ")
    pushStyle(SpanStyle(color = WelcomeAccent))
    append("latest")
    pop()
    append(" to the ")
    pushStyle(SpanStyle(color = WelcomeAccentBright))
    append("greatest")
    pop()
    append(" hits, play your favorite tracks on ")
    pushStyle(SpanStyle(color = WelcomeAccent))
    append("Rakyzu")
    pop()
    append(" now!")
}
