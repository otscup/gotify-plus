package com.gotify.client.ui.login

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.gotify.client.R

@Composable
fun LoginScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onLoginWithPassword: (serverUrl: String, username: String, password: String, serverName: String) -> Unit,
    onLoginWithToken: (serverUrl: String, token: String, serverName: String) -> Unit,
    modifier: Modifier = Modifier,
    /** Set when adding a second server, so the user can back out. */
    onBack: (() -> Unit)? = null
) {
    // Saveable: rotation or process death must not wipe what the user typed (the password is excluded).
    var serverUrl by rememberSaveable { mutableStateOf("") }
    var serverName by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var useToken by rememberSaveable { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val (nameFocus, userFocus, passFocus) = remember { FocusRequester.createRefs() }

    val canSubmit by remember(serverUrl, username, password, token, useToken, isLoading) {
        derivedStateOf {
            serverUrl.isNotBlank() && !isLoading &&
                if (useToken) token.isNotBlank() else username.isNotBlank() && password.isNotBlank()
        }
    }

    fun submit() {
        if (!canSubmit) return
        focusManager.clearFocus()
        if (useToken) onLoginWithToken(serverUrl.trim(), token.trim(), serverName.trim())
        else onLoginWithPassword(serverUrl.trim(), username.trim(), password, serverName.trim())
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val bars = WindowInsets.systemBars.asPaddingValues()
        val viewportHeight = maxHeight - bars.calculateTopPadding() - bars.calculateBottomPadding()

        // Decorative glow, kept subtle so it never competes with the form.
        Box(
            Modifier
                .fillMaxSize(0.7f)
                .align(Alignment.TopStart)
                .offset(x = (-60).dp, y = (-60).dp)
                .background(
                    Brush.radialGradient(
                        listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), Color.Transparent)
                    ),
                    CircleShape
                )
        )
        Box(
            Modifier
                .fillMaxSize(0.5f)
                .align(Alignment.BottomEnd)
                .offset(x = 60.dp, y = 60.dp)
                .background(
                    Brush.radialGradient(
                        listOf(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f), Color.Transparent)
                    ),
                    CircleShape
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Inner column gets a real min height so `Arrangement.Center` works inside the scroll.
            Column(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .heightIn(min = viewportHeight)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.main_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(22.dp))
                )

                Spacer(Modifier.height(20.dp))

                Text(
                    text = if (onBack != null) stringResource(R.string.login_add_server) else stringResource(R.string.login_welcome),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.login_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(32.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {

                        FieldGroup(label = stringResource(R.string.group_server)) {
                            LoginField(
                                value = serverUrl,
                                onValueChange = { serverUrl = it },
                                label = stringResource(R.string.field_server_url),
                                placeholder = "https://gotify.example.com",
                                icon = Icons.Outlined.Language,
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Next,
                                onNext = { nameFocus.requestFocus() }
                            )
                            LoginField(
                                value = serverName,
                                onValueChange = { serverName = it },
                                label = stringResource(R.string.field_nickname),
                                placeholder = stringResource(R.string.nickname_hint),
                                icon = Icons.Outlined.BookmarkBorder,
                                imeAction = ImeAction.Next,
                                onNext = { userFocus.requestFocus() },
                                modifier = Modifier.focusRequester(nameFocus)
                            )
                        }

                        FieldGroup(label = stringResource(R.string.group_signin)) {
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                SegmentedButton(
                                    selected = !useToken,
                                    onClick = { useToken = false; passwordVisible = false },
                                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                                    label = { Text(stringResource(R.string.auth_password)) }
                                )
                                SegmentedButton(
                                    selected = useToken,
                                    onClick = { useToken = true; passwordVisible = false },
                                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                                    label = { Text(stringResource(R.string.auth_client_token)) }
                                )
                            }
                            Text(
                                text = if (useToken) stringResource(R.string.token_hint) else stringResource(R.string.password_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            AnimatedContent(
                                targetState = useToken,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "credentials"
                            ) { tokenMode ->
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (tokenMode) {
                                        LoginField(
                                            value = token,
                                            onValueChange = { token = it },
                                            label = stringResource(R.string.auth_client_token),
                                            icon = Icons.Outlined.Key,
                                            isPassword = true,
                                            keyboardType = KeyboardType.Password,
                                            passwordVisible = passwordVisible,
                                            onToggleVisible = { passwordVisible = !passwordVisible },
                                            imeAction = ImeAction.Done,
                                            onDone = { submit() },
                                            modifier = Modifier.focusRequester(userFocus)
                                        )
                                    } else {
                                        LoginField(
                                            value = username,
                                            onValueChange = { username = it },
                                            label = stringResource(R.string.field_username),
                                            icon = Icons.Outlined.Person,
                                            imeAction = ImeAction.Next,
                                            onNext = { passFocus.requestFocus() },
                                            modifier = Modifier.focusRequester(userFocus)
                                        )
                                        LoginField(
                                            value = password,
                                            onValueChange = { password = it },
                                            label = stringResource(R.string.auth_password),
                                            icon = Icons.Outlined.Lock,
                                            isPassword = true,
                                            keyboardType = KeyboardType.Password,
                                            passwordVisible = passwordVisible,
                                            onToggleVisible = { passwordVisible = !passwordVisible },
                                            imeAction = ImeAction.Done,
                                            onDone = { submit() },
                                            modifier = Modifier.focusRequester(passFocus)
                                        )
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = errorMessage != null,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            // Hold the last message while the exit animation runs.
                            var lastError by remember { mutableStateOf("") }
                            if (errorMessage != null) lastError = errorMessage
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = lastError,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }

                        Button(
                            onClick = { submit() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            enabled = canSubmit,
                            shape = MaterialTheme.shapes.large
                        ) {
                            AnimatedContent(
                                targetState = isLoading,
                                label = "LoadingButtonAnimation"
                            ) { loading ->
                                if (loading) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = LocalContentColor.current,
                                            strokeWidth = 2.dp
                                        )
                                        Text(stringResource(R.string.action_connecting), style = MaterialTheme.typography.titleSmall)
                                    }
                                } else {
                                    Text(stringResource(R.string.action_connect), style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(4.dp)
                    .align(Alignment.TopStart)
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
        }
    }
}

@Composable
private fun FieldGroup(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )
        content()
    }
}

@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onToggleVisible: (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onNext: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = if (placeholder.isNotBlank()) {
            { Text(placeholder) }
        } else null,
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
        trailingIcon = if (isPassword && onToggleVisible != null) {
            {
                IconButton(onClick = onToggleVisible) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (passwordVisible) stringResource(R.string.cd_hide_field, label) else stringResource(R.string.cd_show_field, label),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else null,
        visualTransformation = if (isPassword && !passwordVisible)
            PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction,
            // URLs, usernames and secrets must not be "corrected" or capitalised by the keyboard.
            autoCorrectEnabled = keyboardType == KeyboardType.Text && label == stringResource(R.string.field_nickname),
            capitalization = KeyboardCapitalization.None
        ),
        keyboardActions = KeyboardActions(
            onNext = { onNext?.invoke() },
            onDone = { onDone?.invoke() }
        ),
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedLeadingIconColor = MaterialTheme.colorScheme.primary
        )
    )
}
