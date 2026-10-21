package com.gotify.client.ui.apps

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.gotify.client.R
import com.gotify.client.data.model.GotifyApplication
import com.gotify.client.ui.components.*
import java.net.URI

// Gotify returns "static/defaultapp.jpg" (its own logo) for apps with no custom
// icon uploaded — treat it as "no image" so the initials avatar shows instead.
// Gotify server (api/application.go withResolvedImage) returns "static/defaultapp.png" for apps
// without an uploaded icon. Match any extension and relative/absolute/sub-path forms.
private val GOTIFY_DEFAULT_APP_IMAGE = Regex("""(^|/)static/defaultapp\.[a-z]+$""", RegexOption.IGNORE_CASE)

fun resolveAppImageUrl(baseUrl: String, relativePath: String): String? {
    if (baseUrl.isBlank() || relativePath.isBlank()) return null
    if (GOTIFY_DEFAULT_APP_IMAGE.containsMatchIn(relativePath.substringBefore('?'))) return null
    val candidate = if (relativePath.startsWith("https://", ignoreCase = true) ||
        relativePath.startsWith("http://", ignoreCase = true)
    ) {
        relativePath
    } else {
        "${baseUrl.trimEnd('/')}/${relativePath.trimStart('/')}"
    }
    val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
    return candidate.takeIf {
        uri.scheme.equals("http", ignoreCase = true) ||
            uri.scheme.equals("https", ignoreCase = true)
    }
}

private fun muteLabel(untilMillis: Long, context: android.content.Context): String {
    if (untilMillis == Long.MAX_VALUE) return context.getString(R.string.muted_label)
    val until = java.time.Instant.ofEpochMilli(untilMillis).atZone(java.time.ZoneId.systemDefault())
    val pattern = if (until.toLocalDate() == java.time.LocalDate.now()) "HH:mm" else "EEE HH:mm"
    val formatted = until.format(java.time.format.DateTimeFormatter.ofPattern(pattern, context.resources.configuration.locales.get(0)))
    return context.getString(R.string.muted_until, formatted)
}

@Composable
fun AppIconResolved(
    resolvedImageUrl: String?,
    appName: String,
    modifier: Modifier = Modifier,
    clientToken: String = "",
    authBaseUrl: String = "",
    size: Dp = 40.dp
) {
    AppIcon(
        imageUrl = resolvedImageUrl,
        appName = appName,
        modifier = modifier,
        clientToken = clientToken,
        authBaseUrl = authBaseUrl,
        size = size
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicationsScreen(
    applications: List<GotifyApplication>,
    messageCounts: Map<Int, Int>,
    mutedUntil: Map<Int, Long>,
    onMuteApp: (appId: Int, untilMillis: Long?) -> Unit,
    serverBaseUrl: String,
    isLoading: Boolean,
    clientToken: String,
    isRefreshing: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onAppClick: (GotifyApplication) -> Unit,
    onDeleteApp: (Int) -> Unit,
    onDeleteAppMessages: (Int) -> Unit,
    onCreateApp: (name: String, description: String) -> Unit,
    onEditApp: (appId: Int, name: String, description: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val pullState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        modifier     = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title   = { Text(stringResource(R.string.apps_title), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                        Icon(Icons.Outlined.Refresh, stringResource(R.string.cd_refresh_apps))
                    }
                },
                colors         = gotifyTopAppBarColors(),
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            if (applications.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick        = { showCreateSheet = true },
                    expanded       = fabExpanded,
                    icon           = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text           = { Text(stringResource(R.string.action_new_app)) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor   = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = onRefresh,
            state        = pullState,
            modifier     = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading && applications.isEmpty()) {
                LazyColumn(
                    modifier            = Modifier
                        .fillMaxSize()
                        .widthIn(max = 840.dp)
                        .align(Alignment.TopCenter),
                    userScrollEnabled   = false,
                    contentPadding      = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(4) { ApplicationCardSkeleton() }
                }
            } else if (applications.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon     = Icons.Outlined.Apps,
                        title    = stringResource(R.string.apps_empty_title),
                        subtitle = stringResource(R.string.apps_empty_sub),
                        action   = {
                            Button(onClick = { showCreateSheet = true }) {
                                Icon(Icons.Outlined.Add, null, Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text(stringResource(R.string.action_create_application))
                            }
                        }
                    )
                }
            } else {
                LazyColumn(
                    state               = listState,
                    modifier            = Modifier
                        .fillMaxSize()
                        .widthIn(max = 840.dp)
                        .align(Alignment.TopCenter),
                    // Bottom padding keeps the last card clear of the FAB.
                    contentPadding      = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "count") {
                        SectionHeader(
                            title    = pluralStringResource(R.plurals.apps_count, applications.size, applications.size),
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                        )
                    }

                    items(applications, key = { it.id }) { app ->

                        val resolvedImageUrl = resolveAppImageUrl(serverBaseUrl, app.image)

                        ApplicationCard(
                            application      = app,
                            resolvedImageUrl = resolvedImageUrl,
                            clientToken      = clientToken,
                            authBaseUrl      = serverBaseUrl,
                            messageCount     = messageCounts[app.id] ?: 0,
                            mutedUntil       = mutedUntil[app.id],
                            onMute           = { until -> onMuteApp(app.id, until) },
                            onClick          = { onAppClick(app) },
                            onDelete         = { onDeleteApp(app.id) },
                            onClearMessages  = { onDeleteAppMessages(app.id) },
                            onEdit          = { name, description ->
                                onEditApp(app.id, name, description)
                            },
                            modifier         = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        ApplicationEditorSheet(
            title     = stringResource(R.string.sheet_new_app),
            onSave   = { name, desc -> onCreateApp(name, desc); showCreateSheet = false },
            onDismiss = { showCreateSheet = false }
        )
    }
}

@Composable
private fun ApplicationCard(
    application:      GotifyApplication,
    resolvedImageUrl: String?,
    clientToken: String,
    authBaseUrl: String,
    messageCount:     Int,
    mutedUntil:       Long?,
    onClick:          () -> Unit,
    onDelete:         () -> Unit,
    onClearMessages:  () -> Unit,
    onMute:           (untilMillis: Long?) -> Unit,
    onEdit:           (name: String, description: String) -> Unit,
    modifier:         Modifier = Modifier
) {
    var showMenu    by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showMuteDialog by remember { mutableStateOf(false) }
    val isMuted = mutedUntil != null && mutedUntil > System.currentTimeMillis()

    Card(
        onClick   = onClick,
        modifier  = modifier.fillMaxWidth(),
        shape     = MaterialTheme.shapes.large,
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier              = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            AppIconResolved(
                resolvedImageUrl = resolvedImageUrl,
                appName          = application.name,
                clientToken      = clientToken,
                authBaseUrl      = authBaseUrl,
                size             = 48.dp
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text       = application.name,
                        style      = MaterialTheme.typography.titleMedium,
                        color      = MaterialTheme.colorScheme.onSurface,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier.weight(1f, fill = false)
                    )
                    if (application.internal) {
                        InfoChip(
                            text           = stringResource(R.string.chip_internal),
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor   = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }

                if (application.description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text     = application.description,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    InfoChip(
                        text           = if (messageCount == 0) stringResource(R.string.chip_no_messages)
                                         else pluralStringResource(R.plurals.chip_message_count, messageCount, messageCount),
                        icon           = Icons.Outlined.Inbox,
                        containerColor = if (messageCount > 0) MaterialTheme.colorScheme.primaryContainer
                                         else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor   = if (messageCount > 0) MaterialTheme.colorScheme.onPrimaryContainer
                                         else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isMuted) {
                        InfoChip(
                            text = muteLabel(mutedUntil, LocalContext.current),
                            icon = Icons.Outlined.NotificationsOff,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.cd_options_for, application.name),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text        = { Text(stringResource(R.string.menu_view_messages)) },
                        leadingIcon = { Icon(Icons.Outlined.Inbox, null) },
                        onClick     = { onClick(); showMenu = false }
                    )
                    if (!application.internal) {
                        DropdownMenuItem(
                            text        = { Text(stringResource(R.string.menu_edit_app)) },
                            leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                            onClick     = { showEditSheet = true; showMenu = false }
                        )
                    }
                    if (isMuted) {
                        DropdownMenuItem(
                            text        = { Text(stringResource(R.string.menu_unmute)) },
                            leadingIcon = { Icon(Icons.Outlined.Notifications, null) },
                            onClick     = { onMute(null); showMenu = false }
                        )
                    } else {
                        DropdownMenuItem(
                            text        = { Text(stringResource(R.string.menu_mute)) },
                            leadingIcon = { Icon(Icons.Outlined.NotificationsOff, null) },
                            onClick     = { showMuteDialog = true; showMenu = false }
                        )
                    }
                    DropdownMenuItem(
                        text        = { Text(stringResource(R.string.menu_clear_messages)) },
                        leadingIcon = { Icon(Icons.Outlined.ClearAll, null) },
                        onClick     = { showClearConfirm = true; showMenu = false }
                    )
                    if (!application.internal) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text        = { Text(stringResource(R.string.menu_delete_app), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                            onClick     = { showConfirm = true; showMenu = false }
                        )
                    }
                }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            icon    = { Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title   = { Text(stringResource(R.string.dialog_delete_app_title, application.name)) },
            text    = { Text(stringResource(R.string.dialog_delete_app_body)) },
            confirmButton = {
                Button(onClick = { onDelete(); showConfirm = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    if (showMuteDialog) {
        val hour = 60 * 60 * 1000L
        val options = listOf(
            stringResource(R.string.mute_1h) to hour,
            stringResource(R.string.mute_8h) to 8 * hour,
            stringResource(R.string.mute_24h) to 24 * hour,
            stringResource(R.string.mute_forever) to null
        )
        AlertDialog(
            onDismissRequest = { showMuteDialog = false },
            icon  = { Icon(Icons.Outlined.NotificationsOff, null) },
            title = { Text(stringResource(R.string.dialog_mute_title, application.name)) },
            text  = {
                Column {
                    Text(
                        stringResource(R.string.mute_dialog_body),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    options.forEach { (label, duration) ->
                        ListItem(
                            headlineContent = { Text(label) },
                            leadingContent = {
                                Icon(
                                    if (duration == null) Icons.Outlined.NotificationsOff else Icons.Outlined.Timer,
                                    contentDescription = null
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    onMute(duration?.let { System.currentTimeMillis() + it } ?: Long.MAX_VALUE)
                                    showMuteDialog = false
                                }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showMuteDialog = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            icon = { Icon(Icons.Outlined.ClearAll, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.dialog_clear_app_title, application.name)) },
            text = { Text(stringResource(R.string.dialog_clear_app_body)) },
            confirmButton = {
                Button(
                    onClick = { onClearMessages(); showClearConfirm = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text(stringResource(R.string.menu_clear_messages)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showEditSheet) {
        ApplicationEditorSheet(
            title = stringResource(R.string.sheet_edit_app),
            initialName = application.name,
            initialDescription = application.description,
            onSave = { name, description ->
                onEdit(name, description)
                showEditSheet = false
            },
            onDismiss = { showEditSheet = false }
        )
    }
}

@Composable
private fun ApplicationCardSkeleton() {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = MaterialTheme.shapes.large,
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier              = Modifier.padding(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ShimmerBox(Modifier.size(48.dp), shape = MaterialTheme.shapes.medium)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShimmerBox(Modifier.fillMaxWidth(0.5f).height(14.dp))
                ShimmerBox(Modifier.fillMaxWidth(0.8f).height(10.dp))
                ShimmerBox(Modifier.size(width = 90.dp, height = 18.dp), shape = CircleShape)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApplicationEditorSheet(
    title: String,
    initialName: String = "",
    initialDescription: String = "",
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name        by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    val isNew = initialName.isBlank()
    val canSave = name.isNotBlank() && (isNew || name.trim() != initialName || description.trim() != initialDescription)
    val nameFocus = remember { FocusRequester() }
    val save = { if (canSave) onSave(name.trim(), description.trim()) }

    // Fully expanded so the keyboard never hides the save button.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (isNew) {
                    Text(
                        stringResource(R.string.sheet_new_app_sub),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                label = { Text(stringResource(R.string.field_name)) }, placeholder = { Text(stringResource(R.string.field_name_hint)) },
                singleLine = true, shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                value = description, onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_description_optional)) },
                maxLines = 3, shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() })
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_cancel)) }
                Button(
                    onClick  = { save() },
                    enabled  = canSave,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text(stringResource(if (isNew) R.string.action_create else R.string.action_save)) }
            }
        }
    }
    LaunchedEffect(Unit) { if (isNew) nameFocus.requestFocus() }
}
