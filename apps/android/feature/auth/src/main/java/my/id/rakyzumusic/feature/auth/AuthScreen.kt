package my.id.rakyzumusic.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import my.id.rakyzumusic.core.data.auth.AuthRepository
import my.id.rakyzumusic.core.designsystem.theme.RakyzuAqua
import my.id.rakyzumusic.core.designsystem.theme.RakyzuBlack
import my.id.rakyzumusic.core.designsystem.theme.RakyzuMusicTheme
import my.id.rakyzumusic.core.designsystem.theme.RakyzuPurple

@Composable
fun AuthRoute(
    repository: AuthRepository,
    sessionMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    val viewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory(repository))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AuthScreen(
        state = state,
        sessionMessage = sessionMessage,
        onEmailChanged = viewModel::updateEmail,
        onPasswordChanged = viewModel::updatePassword,
        onPasswordConfirmationChanged = viewModel::updatePasswordConfirmation,
        onTogglePasswordVisibility = viewModel::togglePasswordVisibility,
        onSwitchMode = viewModel::switchMode,
        onSubmit = viewModel::submit,
        modifier = modifier,
    )
}

@Composable
fun AuthScreen(
    state: AuthUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onPasswordConfirmationChanged: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSwitchMode: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    sessionMessage: String? = null,
) {
    val isSignIn = state.mode == AuthMode.SignIn
    val message = state.message ?: sessionMessage
    val messageIsError = state.message?.let { state.messageIsError } ?: (sessionMessage != null)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF29164B), RakyzuBlack, RakyzuBlack),
                ),
            )
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
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
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Rakyzu Music",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = if (isSignIn) "Sign in to keep listening" else "Create your listening space",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(28.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = if (isSignIn) "Welcome back" else "Join Rakyzu Music",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                OutlinedTextField(
                    value = state.email,
                    onValueChange = onEmailChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_email"),
                    enabled = !state.isSubmitting,
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                )
                OutlinedTextField(
                    value = state.password,
                    onValueChange = onPasswordChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_password"),
                    enabled = !state.isSubmitting,
                    label = { Text("Password") },
                    supportingText = if (!isSignIn) {
                        { Text("Use 8+ characters with uppercase, lowercase, and a number.") }
                    } else {
                        null
                    },
                    singleLine = true,
                    visualTransformation = if (state.isPasswordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = if (isSignIn) ImeAction.Done else ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (isSignIn) onSubmit() },
                    ),
                    trailingIcon = {
                        IconButton(onClick = onTogglePasswordVisibility) {
                            Icon(
                                imageVector = if (state.isPasswordVisible) {
                                    Icons.Rounded.VisibilityOff
                                } else {
                                    Icons.Rounded.Visibility
                                },
                                contentDescription = if (state.isPasswordVisible) {
                                    "Hide password"
                                } else {
                                    "Show password"
                                },
                            )
                        }
                    },
                )
                if (!isSignIn) {
                    OutlinedTextField(
                        value = state.passwordConfirmation,
                        onValueChange = onPasswordConfirmationChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_password_confirmation"),
                        enabled = !state.isSubmitting,
                        label = { Text("Confirm password") },
                        singleLine = true,
                        visualTransformation = if (state.isPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    )
                }

                if (message != null) {
                    AuthMessage(
                        message = message,
                        isError = messageIsError,
                    )
                }

                Button(
                    onClick = onSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("auth_submit"),
                    enabled = !state.isSubmitting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RakyzuAqua,
                        contentColor = RakyzuBlack,
                    ),
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = RakyzuBlack,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = if (isSignIn) "Sign in" else "Create account",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isSignIn) "New to Rakyzu Music?" else "Already have an account?",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = onSwitchMode,
                        enabled = !state.isSubmitting,
                    ) {
                        Text(if (isSignIn) "Sign up" else "Sign in")
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Your session is encrypted on this device.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun AuthMessage(
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
            onSwitchMode = {},
            onSubmit = {},
        )
    }
}
