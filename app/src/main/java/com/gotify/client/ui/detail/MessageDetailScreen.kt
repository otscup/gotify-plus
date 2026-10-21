package com.gotify.client.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.gotify.client.R
import com.gotify.client.data.model.GotifyApplication
import com.gotify.client.data.model.GotifyMessage
import com.gotify.client.ui.apps.resolveAppImageUrl
import com.gotify.client.ui.components.*
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import dev.jeziellago.compose.markdowntext.MarkdownText
import com.gotify.client.util.launchGotifyAction
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailScreen(
    message: GotifyMessage,
    application: GotifyApplication?,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onOpenAppInbox: () -> Unit,
    modifier: Modifier = Modifier,
    markdownEnabled: Boolean = true,
    clientToken: String = "",
    serverBaseUrl: String = "",
    errorMessage: String? = null
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val applicationImageUrl = application?.image?.let { image ->
        resolveAppImageUrl(serverBaseUrl, image)
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    val isMarkdown = markdownEnabled && message.extras?.display?.contentType == "text/markdown"
    val hasRemoteMarkdownImage = isMarkdown && remember(message.message) {
        Regex("!\\[[^]]*]\\(\\s*https?://", RegexOption.IGNORE_CASE).containsMatchIn(message.message)
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val fullTimestamp = remember(message.date) {
        parseGotifyDate(message.date)
            ?.atZone(java.time.ZoneId.systemDefault())
            ?.format(java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM, java.time.format.FormatStyle.SHORT))
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar   = {
            TopAppBar(
                title          = { Text(stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("Gotify message", message.message)
                            )
                            coroutineScope.launch { snackbarHostState.showSnackbar(context.getString(R.string.msg_copied)) }
                        }
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.cd_copy_message))
                    }
                    IconButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    listOfNotNull(
                                        message.title.takeIf { it.isNotBlank() },
                                        message.message
                                    ).joinToString("\n\n")
                                )
                            }
                            runCatching {
                                context.startActivity(
                                    Intent.createChooser(shareIntent, context.getString(R.string.share_title))
                                )
                            }.onFailure {
                                coroutineScope.launch { snackbarHostState.showSnackbar(context.getString(R.string.err_no_share_app)) }
                            }
                        }
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.cd_share_message))
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.cd_delete_message))
                    }
                },
                colors = gotifyTopAppBarColors(),
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->

        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter
        ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            if (hasRemoteMarkdownImage) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Outlined.PrivacyTip, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(
                            stringResource(R.string.remote_images_warning),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Surface(
                onClick = onOpenAppInbox,
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier  = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 12.dp),
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AppIcon(
                        imageUrl = applicationImageUrl,
                        appName  = application?.name ?: stringResource(R.string.app_fallback),
                        clientToken = clientToken,
                        authBaseUrl = serverBaseUrl,
                        size     = 48.dp
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = application?.name ?: stringResource(R.string.app_with_id, message.appId),
                            style      = MaterialTheme.typography.titleMedium,
                            color      = MaterialTheme.colorScheme.onSurface,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            RelativeTime(isoDate = message.date)
                            if (message.priority != 0) {
                                Text("·", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                PriorityBadge(priority = message.priority)
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.action_view_all),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (message.title.isNotBlank()) {
                    SelectionContainer {
                        Text(
                            text       = message.title,
                            style      = MaterialTheme.typography.headlineSmall,
                            color      = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                if (fullTimestamp != null) {
                    Text(
                        text  = fullTimestamp,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    if (isMarkdown) {
                        MarkdownText(
                            markdown  = message.message,
                            style     = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    } else {
                        // Long-press to select and copy part of a message.
                        SelectionContainer {
                            Text(
                                text  = message.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            val bigImageUrl = message.extras?.notification?.bigImageUrl
            if (!bigImageUrl.isNullOrBlank()) {
                MessageImageCard(
                    imageUrl = resolveAppImageUrl(serverBaseUrl, bigImageUrl) ?: bigImageUrl,
                    clientToken = clientToken,
                    authBaseUrl = serverBaseUrl
                )
            }

            val actionUrl = message.extras?.notification?.click?.url
                ?: message.extras?.action?.onClick?.intentUrl
                ?: message.extras?.action?.onReceive?.intentUrl
            if (!actionUrl.isNullOrBlank()) {
                Button(
                    onClick  = {
                        if (!context.launchGotifyAction(actionUrl)) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.err_open_action))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape    = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.action_open_link))
                }
            }

            val notifyActions = message.extras?.notifyActions.orEmpty()
            if (notifyActions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    notifyActions.forEach { act ->
                        if (act.label.isNotBlank() && act.url.isNotBlank()) {
                            FilledTonalButton(
                                onClick = {
                                    if (!context.launchGotifyAction(act.url)) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(context.getString(R.string.err_open_action))
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text(act.label)
                            }
                        }
                    }
                }
            }

            DetailMetadataCard(message = message)
        }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Outlined.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.dialog_delete_message_title)) },
            text = { Text(stringResource(R.string.dialog_delete_message_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}


@Composable
private fun MessageImageCard(
    imageUrl: String,
    clientToken: String,
    authBaseUrl: String
) {
    val context = LocalContext.current
    val request = remember(imageUrl, clientToken, authBaseUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .apply {
                if (shouldAttachGotifyKey(imageUrl, clientToken, authBaseUrl)) {
                    addHeader("X-Gotify-Key", clientToken)
                }
            }
            .crossfade(true)
            .build()
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.Image, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    stringResource(R.string.attachment),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))

            SubcomposeAsyncImage(
                model = request,
                contentDescription = stringResource(R.string.cd_message_attachment),
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .clip(MaterialTheme.shapes.medium),
                loading = {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                },
                error = {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.BrokenImage, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.err_load_attachment))
                    }
                }
            )
        }
    }
}

@Composable
private fun DetailMetadataCard(message: GotifyMessage) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape  = MaterialTheme.shapes.large,
        color  = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.animateContentSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = stringResource(if (expanded) R.string.cd_hide_details else R.string.cd_show_details)) { expanded = !expanded }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.Info, null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.message_details),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetaRow(stringResource(R.string.meta_message_id),  "#${message.id}")
                    MetaRow(stringResource(R.string.meta_app_id),      "#${message.appId}")
                    MetaRow(stringResource(R.string.meta_priority),    "${message.priority} (${priorityLabel(message.priority)})")
                    MetaRow(stringResource(R.string.meta_timestamp), parseGotifyDate(message.date)
                        ?.atZone(java.time.ZoneId.systemDefault())
                        ?.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm:ss"))
                        ?: message.date)
                    if (message.extras != null) {
                        MetaRow(stringResource(R.string.meta_content_type),
                            message.extras.display?.contentType ?: "text/plain")
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text       = value,
            style      = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color      = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}
