package my.id.rakyzumusic.feature.auth

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import my.id.rakyzumusic.core.data.auth.AuthRepository
import my.id.rakyzumusic.core.data.auth.OAuthProvider
import my.id.rakyzumusic.core.designsystem.theme.RakyzuMusicTheme

private val AuthBackground = Color(0xFF121111)
private val AuthField = Color(0xFF1E1E1E)
private val AuthBorder = Color(0xFFDAE7E7)
private val AuthPrimary = Color(0xFF059FB4)
private val AuthAccent = Color(0xFF7BEEFF)
private val AuthMuted = Color(0xFFC9C7CC)

@Composable
fun AuthRoute(
    repository: AuthRepository,
    googleWebClientId: String,
    sessionMessage: String? = null,
    passwordRecoveryRequired: Boolean = false,
    onExit: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val externalAuthScope = rememberCoroutineScope()
    val viewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory(repository))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCredentials by rememberSaveable(passwordRecoveryRequired, sessionMessage) {
        mutableStateOf(passwordRecoveryRequired || sessionMessage != null)
    }

    LaunchedEffect(passwordRecoveryRequired) {
        if (passwordRecoveryRequired) viewModel.showPasswordReset()
    }

    LaunchedEffect(state.navigateToGateway) {
        if (state.navigateToGateway) {
            showCredentials = false
            viewModel.onGatewayNavigationHandled()
        }
    }

    val startGoogleSignIn: () -> Unit = {
        if (viewModel.beginGoogleSignIn()) {
            externalAuthScope.launch {
                try {
                    when (
                        val result = requestNativeGoogleCredential(
                            context = context,
                            webClientId = googleWebClientId,
                        )
                    ) {
                        is NativeGoogleAuthResult.Success -> viewModel.completeGoogleSignIn(
                            idToken = result.idToken,
                            rawNonce = result.rawNonce,
                        )
                        NativeGoogleAuthResult.Cancelled -> viewModel.cancelGoogleSignIn()
                        NativeGoogleAuthResult.NoAccount -> viewModel.failGoogleSignIn(
                            "No Google account is available on this device.",
                        )
                        NativeGoogleAuthResult.InvalidCredential -> viewModel.failGoogleSignIn(
                            "Google returned an invalid sign-in credential. Try again.",
                        )
                        NativeGoogleAuthResult.Unavailable -> viewModel.failGoogleSignIn(
                            "Google sign-in is unavailable right now. Try again.",
                        )
                        NativeGoogleAuthResult.InvalidConfiguration -> viewModel.failGoogleSignIn(
                            "Google sign-in is not configured for this build.",
                        )
                    }
                } catch (error: CancellationException) {
                    viewModel.cancelGoogleSignIn()
                    throw error
                }
            }
        }
    }

    if (showCredentials) {
        AuthScreen(
            state = state,
            sessionMessage = sessionMessage,
            onEmailChanged = viewModel::updateEmail,
            onPasswordChanged = viewModel::updatePassword,
            onPasswordConfirmationChanged = viewModel::updatePasswordConfirmation,
            onTogglePasswordVisibility = viewModel::togglePasswordVisibility,
            onRememberMeChanged = viewModel::updateRememberMe,
            onForgotPassword = viewModel::showForgotPassword,
            onCancelRecovery = viewModel::cancelRecovery,
            onSwitchToSignUp = viewModel::showSignUp,
            onSwitchToSignIn = viewModel::showSignIn,
            onGoogleSelected = startGoogleSignIn,
            onFacebookSelected = viewModel::signInWithFacebook,
            onSubmit = viewModel::submit,
            onBack = {
                viewModel.showGateway()
                showCredentials = false
            },
            modifier = modifier,
        )
    } else {
        SignInGatewayScreen(
            state = state,
            onBack = onExit,
            onGoogleSelected = startGoogleSignIn,
            onFacebookSelected = viewModel::signInWithFacebook,
            onPasswordLogin = {
                viewModel.showSignIn()
                showCredentials = true
            },
            onSignUp = {
                viewModel.showSignUp()
                showCredentials = true
            },
            modifier = modifier,
        )
    }
}

@Composable
fun AuthScreen(
    state: AuthUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onPasswordConfirmationChanged: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onRememberMeChanged: (Boolean) -> Unit,
    onForgotPassword: () -> Unit,
    onCancelRecovery: () -> Unit,
    onSwitchToSignUp: () -> Unit,
    onSwitchToSignIn: () -> Unit,
    onGoogleSelected: () -> Unit,
    onFacebookSelected: () -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    sessionMessage: String? = null,
) {
    val isSignIn = state.mode == AuthMode.SignIn
    val isSignUp = state.mode == AuthMode.SignUp
    val isForgotPassword = state.mode == AuthMode.ForgotPassword
    val isResetPassword = state.mode == AuthMode.ResetPassword
    val isCredentialMode = isSignIn || isSignUp
    val interactionLocked = state.isSubmitting || state.externalProvider != null
    val message = state.message ?: sessionMessage
    val messageIsError = state.message?.let { state.messageIsError } ?: (sessionMessage != null)
    var contentVisible by remember(state.mode) { mutableStateOf(false) }
    var showAppleComingSoon by rememberSaveable { mutableStateOf(false) }
    val contentProgress by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "auth-content",
    )

    LaunchedEffect(state.mode) {
        contentVisible = true
    }

    if (showAppleComingSoon) {
        AppleComingSoonDialog(onDismiss = { showAppleComingSoon = false })
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuthBackground)
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
                onClick = if (isCredentialMode) onBack else onCancelRecovery,
                enabled = !interactionLocked,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(48.dp)
                    .testTag("auth_back"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = if (isCredentialMode) {
                        "Back to sign in options"
                    } else {
                        "Back to log in"
                    },
                    tint = Color.White,
                )
            }
        }

        Image(
            painter = painterResource(R.drawable.rakyzu_logo),
            contentDescription = "Rakyzu Music logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .widthIn(max = 224.dp)
                .fillMaxWidth(0.58f)
                .aspectRatio(539f / 463f)
                .graphicsLayer {
                    alpha = contentProgress
                    translationY = (1f - contentProgress) * 20.dp.toPx()
                },
        )

        Text(
            text = when (state.mode) {
                AuthMode.SignIn -> "Login to your account"
                AuthMode.SignUp -> "Create your account"
                AuthMode.ForgotPassword -> "Reset your password"
                AuthMode.ResetPassword -> "Choose a new password"
            },
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 428.dp)
                .semantics { heading() }
                .graphicsLayer { alpha = contentProgress },
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 30.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            ),
        )

        Spacer(Modifier.height(28.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 428.dp)
                .graphicsLayer {
                    alpha = contentProgress
                    translationY = (1f - contentProgress) * 16.dp.toPx()
                },
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!isResetPassword) {
                AuthTextField(
                    value = state.email,
                    onValueChange = onEmailChanged,
                    label = "Email",
                    enabled = !interactionLocked,
                    leadingIcon = {
                        Icon(imageVector = Icons.Rounded.Email, contentDescription = null)
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = if (isForgotPassword) ImeAction.Done else ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (isForgotPassword) onSubmit() },
                    ),
                    testTag = "auth_email",
                )
            }

            if (!isForgotPassword) {
                AuthPasswordField(
                    value = state.password,
                    onValueChange = onPasswordChanged,
                    label = if (isResetPassword) "New password" else "Password",
                    visible = state.isPasswordVisible,
                    enabled = !interactionLocked,
                    onToggleVisibility = onTogglePasswordVisibility,
                    helperText = if (isSignUp || isResetPassword) {
                        "Use 8+ characters with uppercase, lowercase, and a number."
                    } else {
                        null
                    },
                    imeAction = if (isSignIn) ImeAction.Done else ImeAction.Next,
                    onDone = { if (isSignIn) onSubmit() },
                    testTag = "auth_password",
                )
            }

            if (isSignUp || isResetPassword) {
                AuthPasswordField(
                    value = state.passwordConfirmation,
                    onValueChange = onPasswordConfirmationChanged,
                    label = "Confirm password",
                    visible = state.isPasswordVisible,
                    enabled = !interactionLocked,
                    onToggleVisibility = onTogglePasswordVisibility,
                    imeAction = ImeAction.Done,
                    onDone = onSubmit,
                    testTag = "auth_password_confirmation",
                )
            }

            if (isSignIn) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(
                            value = state.rememberMe,
                            enabled = !interactionLocked,
                            role = Role.Checkbox,
                            onValueChange = onRememberMeChanged,
                        )
                        .testTag("auth_remember_me"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = state.rememberMe,
                        onCheckedChange = null,
                        enabled = !interactionLocked,
                        colors = CheckboxDefaults.colors(
                            checkedColor = AuthPrimary,
                            uncheckedColor = AuthPrimary,
                            checkmarkColor = Color.White,
                        ),
                    )
                    Text(
                        text = "Remember me",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (message != null) {
                AuthMessage(message = message, isError = messageIsError)
            }

            Button(
                onClick = onSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 59.dp)
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(50),
                        ambientColor = AuthPrimary,
                        spotColor = AuthPrimary,
                    )
                    .testTag("auth_submit"),
                enabled = !interactionLocked,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AuthPrimary,
                    contentColor = Color.White,
                    disabledContainerColor = AuthPrimary.copy(alpha = 0.5f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f),
                ),
                shape = RoundedCornerShape(50),
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = when (state.mode) {
                            AuthMode.SignIn -> "Log In"
                            AuthMode.SignUp -> "Sign Up"
                            AuthMode.ForgotPassword -> "Send Reset Link"
                            AuthMode.ResetPassword -> "Update Password"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.72.sp,
                    )
                }
            }

            if (isSignIn) {
                TextButton(
                    onClick = onForgotPassword,
                    enabled = !interactionLocked,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .heightIn(min = 48.dp)
                        .testTag("auth_forgot_password"),
                ) {
                    Text(
                        text = "Forgot the password?",
                        color = AuthAccent,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (isCredentialMode) {
                AuthSocialSection(
                    state = state,
                    enabled = !interactionLocked,
                    onGoogleSelected = onGoogleSelected,
                    onFacebookSelected = onFacebookSelected,
                    onAppleSelected = { showAppleComingSoon = true },
                )

                AuthModeFooter(
                    isSignIn = isSignIn,
                    enabled = !interactionLocked,
                    onSwitchToSignUp = onSwitchToSignUp,
                    onSwitchToSignIn = onSwitchToSignIn,
                )
            } else if (isForgotPassword) {
                TextButton(
                    onClick = onCancelRecovery,
                    enabled = !interactionLocked,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .heightIn(min = 48.dp),
                ) {
                    Text("Back to Log In", color = AuthAccent, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    testTag: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 59.dp)
            .testTag(testTag),
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            disabledTextColor = Color.White.copy(alpha = 0.5f),
            focusedContainerColor = AuthField,
            unfocusedContainerColor = AuthField,
            disabledContainerColor = AuthField.copy(alpha = 0.65f),
            focusedBorderColor = AuthPrimary,
            unfocusedBorderColor = AuthBorder.copy(alpha = 0.35f),
            disabledBorderColor = AuthBorder.copy(alpha = 0.16f),
            focusedLabelColor = AuthAccent,
            unfocusedLabelColor = AuthMuted,
            focusedLeadingIconColor = AuthAccent,
            unfocusedLeadingIconColor = AuthMuted,
            focusedTrailingIconColor = AuthAccent,
            unfocusedTrailingIconColor = AuthMuted,
            cursorColor = AuthAccent,
            focusedSupportingTextColor = AuthMuted,
            unfocusedSupportingTextColor = AuthMuted,
        ),
    )
}

@Composable
private fun AuthPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    enabled: Boolean,
    onToggleVisibility: () -> Unit,
    imeAction: ImeAction,
    onDone: () -> Unit,
    testTag: String,
    helperText: String? = null,
) {
    AuthTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        enabled = enabled,
        leadingIcon = {
            Icon(imageVector = Icons.Rounded.Lock, contentDescription = null)
        },
        trailingIcon = {
            IconButton(
                onClick = onToggleVisibility,
                enabled = enabled,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password",
                )
            }
        },
        supportingText = helperText?.let { text -> { Text(text) } },
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        testTag = testTag,
    )
}

@Composable
private fun AuthSocialSection(
    state: AuthUiState,
    enabled: Boolean,
    onGoogleSelected: () -> Unit,
    onFacebookSelected: () -> Unit,
    onAppleSelected: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Color.White.copy(alpha = 0.7f),
        )
        Text(
            text = "or continue with",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Color.White.copy(alpha = 0.7f),
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AuthSocialButton(
            iconResource = R.drawable.auth_google,
            contentDescription = "Continue with Google",
            enabled = enabled,
            isLoading = state.externalProvider == OAuthProvider.Google,
            testTag = "credential_oauth_google",
            onClick = onGoogleSelected,
        )
        AuthSocialButton(
            iconResource = R.drawable.auth_facebook,
            contentDescription = "Continue with Facebook",
            enabled = enabled,
            isLoading = state.externalProvider == OAuthProvider.Facebook,
            testTag = "credential_oauth_facebook",
            onClick = onFacebookSelected,
        )
        AuthSocialButton(
            iconResource = R.drawable.auth_apple,
            contentDescription = "Sign in with Apple",
            enabled = enabled,
            isLoading = false,
            testTag = "credential_oauth_apple",
            onClick = onAppleSelected,
        )
    }
}

@Composable
private fun AuthSocialButton(
    iconResource: Int,
    contentDescription: String,
    enabled: Boolean,
    isLoading: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(52.dp)
            .testTag(testTag),
        shape = CircleShape,
        color = AuthField,
        contentColor = Color.White,
        border = BorderStroke(0.5.dp, AuthBorder.copy(alpha = 0.6f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = AuthAccent,
                    strokeWidth = 2.dp,
                )
            } else {
                Image(
                    painter = painterResource(iconResource),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun AuthModeFooter(
    isSignIn: Boolean,
    enabled: Boolean,
    onSwitchToSignUp: () -> Unit,
    onSwitchToSignIn: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (isSignIn) "Don’t have an account?" else "Already have an account?",
            modifier = Modifier.weight(1f, fill = false),
            color = Color.White,
            textAlign = TextAlign.End,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
        TextButton(
            onClick = if (isSignIn) onSwitchToSignUp else onSwitchToSignIn,
            enabled = enabled,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag(if (isSignIn) "auth_sign_up_now" else "auth_sign_in"),
        ) {
            Text(
                text = if (isSignIn) "Sign Up Now" else "Sign In",
                color = AuthAccent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun AppleComingSoonDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign In With Apple", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "Next-level quality is just around the corner. " +
                    "The ultimate upgrade you’ve been waiting for is coming soon!",
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it")
            }
        },
        containerColor = AuthField,
        titleContentColor = Color.White,
        textContentColor = Color.White.copy(alpha = 0.82f),
    )
}

@Composable
internal fun AuthMessage(
    message: String,
    isError: Boolean,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        },
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            color = if (isError) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onTertiaryContainer
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun AuthScreenPreview() {
    RakyzuMusicTheme(darkTheme = true) {
        AuthScreen(
            state = AuthUiState(),
            onEmailChanged = {},
            onPasswordChanged = {},
            onPasswordConfirmationChanged = {},
            onTogglePasswordVisibility = {},
            onRememberMeChanged = {},
            onForgotPassword = {},
            onCancelRecovery = {},
            onSwitchToSignUp = {},
            onSwitchToSignIn = {},
            onGoogleSelected = {},
            onFacebookSelected = {},
            onSubmit = {},
            onBack = {},
        )
    }
}
