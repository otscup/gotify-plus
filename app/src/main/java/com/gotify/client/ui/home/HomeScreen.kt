package com.gotify.client.ui.home

import androidx.compose.animation.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.gotify.client.R
import com.gotify.client.data.model.*
import com.gotify.client.ui.components.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    messages:            List<GotifyMessage>,
    applications:        List<GotifyApplication>,
    clientToken:         String,
    serverBaseUrl:       String,
    connectionStatus:    ConnectionStatus,
    activeServerName:    String,
    unreadCount:         Int,
    cachedMessageCount:  Int,
    cacheSyncTruncated:  Boolean,
    isLoading:           Boolean,
    isRefreshing:        Boolean,
    hasMorePages:        Boolean,
    errorMessage:        String?,
    selectedAppId:       Int?,
    unreadOnly:          Boolean,
    onSelectApp:         (Int?) -> Unit,
    onToggleUnread:      () -> Unit,
    onRefresh:           () -> Unit,
    onLoadMore:          () -> Unit,
    onMessageClick:      (GotifyMessage) -> Unit,
    onDeleteMessage:     (Long) -> Unit,
    onUndoDelete:        (Long) -> Unit,
    onDeleteAllMessages: () -> Unit,
    onMarkAllAsRead:     () -> Unit,
    onOpenServers:       () -> Unit,
    onOpenSearch:        () -> Unit,
    modifier:            Modifier = Modifier
) {
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    val snackbarHostState   = remember { SnackbarHostState() }
    val deleteWithUndo      = rememberUndoableDelete(snackbarHostState, onDeleteMessage, onUndoDelete)

    val appMap = remember(applications) { applications.associateBy { it.id } }
    val context = LocalContext.current
    // `messages` is already filtered in the database (HomeViewModel), so filters cover every cached message.
    val sections = remember(messages) { messages.groupBy { dayLabel(it.date, context) } }

    val pullRefreshState = rememberPullToRefreshState()
    val listState        = rememberLazyListState()
    val scrollBehavior   = TopAppBarDefaults.pinnedScrollBehavior()
    LoadMoreEffect(listState, hasMorePages && !isLoading, onLoadMore)

    Scaffold(
        modifier       = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        topBar         = {
            HomeTopBar(
                serverName       = activeServerName,
                connectionStatus = connectionStatus,
                onOpenServers    = onOpenServers,
                onOpenSearch     = onOpenSearch,
                onDeleteAll      = { showDeleteAllDialog = true },
                unreadCount      = unreadCount,
                onMarkAllAsRead  = onMarkAllAsRead,
                scrollBehavior   = scrollBehavior
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = onRefresh,
            state        = pullRefreshState,
            modifier     = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            LazyColumn(
                state               = listState,
                modifier            = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
                    .align(Alignment.TopCenter),
                contentPadding      = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                item(key = "summary") {
                    HomeSummaryCard(
                        unreadCount        = unreadCount,
                        cachedCount        = cachedMessageCount,
                        connectionStatus   = connectionStatus,
                        cacheSyncTruncated = cacheSyncTruncated,
                        onMarkAllAsRead    = onMarkAllAsRead
                    )
                }

                errorMessage?.let { error ->
                    item(key = "sync_error") {
                        Surface(
                            shape    = MaterialTheme.shapes.large,
                            color    = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth().animateItem()
                        ) {
                            Row(
                                modifier              = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.CloudOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    error,
                                    style    = MaterialTheme.typography.bodySmall,
                                    color    = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = onRefresh,
                                    colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)
                                ) { Text(stringResource(R.string.action_retry)) }
                            }
                        }
                    }
                }

                // Filter chips — shown whenever there is something to filter, or a filter is active
                if (applications.isNotEmpty() || unreadOnly) {
                    item(key = "filter_chips") {
                        AppFilterChips(
                            applications   = applications,
                            clientToken    = clientToken,
                            authBaseUrl    = serverBaseUrl,
                            selectedAppId  = selectedAppId,
                            unreadOnly     = unreadOnly,
                            onToggleUnread = onToggleUnread,
                            onSelectApp    = onSelectApp,
                            modifier       = Modifier.horizontalBleed(16.dp)
                        )
                    }
                }

                // Loading skeletons
                if (isLoading && messages.isEmpty()) {
                    items(5, key = { "skeleton_$it" }) { MessageCardSkeleton() }
                }

                // Empty state
                if (!isLoading && messages.isEmpty()) {
                    item(key = "empty") {
                        val filtered = unreadOnly || selectedAppId != null
                        // Not fillParentMaxSize: the summary card above would push it into a pointless scroll.
                        Box(Modifier.fillParentMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                            EmptyState(
                                icon     = when {
                                    unreadOnly            -> Icons.Outlined.DoneAll
                                    selectedAppId != null -> Icons.Outlined.FilterAltOff
                                    else                  -> Icons.Outlined.NotificationsNone
                                },
                                title    = when {
                                    unreadOnly            -> stringResource(R.string.empty_caught_up_title)
                                    selectedAppId != null -> stringResource(R.string.empty_app_title)
                                    else                  -> stringResource(R.string.empty_none_title)
                                },
                                subtitle = when {
                                    unreadOnly            -> stringResource(R.string.empty_caught_up_sub)
                                    selectedAppId != null -> stringResource(R.string.empty_app_sub)
                                    else                  -> stringResource(R.string.empty_none_sub)
                                },
                                action   = if (filtered) {
                                    {
                                        FilledTonalButton(onClick = {
                                            if (unreadOnly) onToggleUnread()
                                            if (selectedAppId != null) onSelectApp(null)
                                        }) { Text(stringResource(R.string.action_show_all_messages)) }
                                    }
                                } else null
                            )
                        }
                    }
                }

                sections.forEach { (label, dayMessages) ->
                    stickyHeader(key = "header_$label") {
                        DateHeader(label)
                    }
                    items(dayMessages, key = { it.id }) { message ->
                        val app = appMap[message.appId]
                        MessageCard(
                            title       = message.title,
                            message     = message.message,
                            appName     = app?.name ?: stringResource(R.string.app_with_id, message.appId),
                            appImageUrl = app?.image,
                            clientToken = clientToken,
                            authBaseUrl = serverBaseUrl,
                            priority    = message.priority,
                            date        = message.date,
                            isRead      = message.isRead,
                            onClick     = { onMessageClick(message) },
                            onDelete    = { deleteWithUndo(message.id) },
                            modifier    = Modifier.animateItem()
                        )
                    }
                }

                if (hasMorePages && !isLoading && messages.isNotEmpty()) {
                    item(key = "load_more") { LoadingMoreIndicator() }
                }
            }
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            icon    = { Icon(Icons.Outlined.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title   = { Text(stringResource(R.string.dialog_delete_all_title)) },
            text    = { Text(stringResource(R.string.dialog_delete_all_body)) },
            confirmButton = {
                Button(
                    onClick = { onDeleteAllMessages(); showDeleteAllDialog = false },
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor   = MaterialTheme.colorScheme.onError
                    )
                ) { Text(stringResource(R.string.action_delete_all)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteAllDialog = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun HomeSummaryCard(
    unreadCount: Int,
    cachedCount: Int,
    connectionStatus: ConnectionStatus,
    cacheSyncTruncated: Boolean,
    onMarkAllAsRead: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connectionLabel = when (connectionStatus) {
        ConnectionStatus.CONNECTED    -> stringResource(R.string.conn_live)
        ConnectionStatus.CONNECTING   -> stringResource(R.string.conn_connecting)
        ConnectionStatus.ERROR        -> stringResource(R.string.conn_issue)
        ConnectionStatus.DISCONNECTED -> stringResource(R.string.conn_offline)
    }
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape    = MaterialTheme.shapes.extraLarge,
        color    = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(
            modifier            = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text  = if (unreadCount == 0) stringResource(R.string.summary_all_caught_up) else stringResource(R.string.summary_unread, unreadCount),
                        style = MaterialTheme.typography.headlineSmall,
                        color = onContainer
                    )
                    Text(
                        text  = pluralStringResource(R.plurals.summary_cached, cachedCount, cachedCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = onContainer.copy(alpha = 0.78f)
                    )
                }
                Surface(
                    shape    = CircleShape,
                    color    = onContainer.copy(alpha = 0.10f),
                    modifier = Modifier.semantics { contentDescription = stringResource(R.string.cd_connection, connectionLabel) }
                ) {
                    Row(
                        modifier              = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ConnectionDot(connectionStatus)
                        Text(connectionLabel, style = MaterialTheme.typography.labelMedium, color = onContainer)
                    }
                }
            }
            AnimatedVisibility(visible = unreadCount > 0) {
                TextButton(
                    onClick        = onMarkAllAsRead,
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    colors         = ButtonDefaults.textButtonColors(contentColor = onContainer)
                ) {
                    Icon(Icons.Outlined.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_mark_all_read))
                }
            }
            if (cacheSyncTruncated) {
                Text(
                    stringResource(R.string.sync_truncated_note),
                    style    = MaterialTheme.typography.bodySmall,
                    color    = onContainer.copy(alpha = 0.72f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

// ─── Top Bar ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    serverName:       String,
    connectionStatus: ConnectionStatus,
    onOpenServers:    () -> Unit,
    onOpenSearch:     () -> Unit,
    onDeleteAll:      () -> Unit,
    unreadCount:      Int,
    onMarkAllAsRead:  () -> Unit,
    scrollBehavior:   TopAppBarScrollBehavior
) {
    var showMenu by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            // Tappable server switcher with a proper touch target and ripple bounds.
            Column(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(
                        onClickLabel = stringResource(R.string.action_switch_server),
                        role         = Role.Button,
                        onClick      = onOpenServers
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(stringResource(R.string.nav_messages), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ConnectionDot(status = connectionStatus)
                    Text(
                        text     = serverName,
                        style    = MaterialTheme.typography.labelMedium,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Icon(
                        Icons.Outlined.UnfoldMore, null,
                        modifier = Modifier.size(14.dp),
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onOpenSearch) { Icon(Icons.Outlined.Search, stringResource(R.string.cd_search_messages)) }
            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.cd_more_options)) }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text        = { Text(stringResource(R.string.action_mark_all_read)) },
                        leadingIcon = { Icon(Icons.Outlined.DoneAll, null) },
                        enabled     = unreadCount > 0,
                        onClick     = { onMarkAllAsRead(); showMenu = false }
                    )
                    DropdownMenuItem(
                        text        = { Text(stringResource(R.string.action_switch_server)) },
                        leadingIcon = { Icon(Icons.Outlined.Dns, null) },
                        onClick     = { onOpenServers(); showMenu = false }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text        = { Text(stringResource(R.string.action_delete_all_messages), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(Icons.Outlined.DeleteSweep, null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick     = { onDeleteAll(); showMenu = false }
                    )
                }
            }
        },
        colors         = gotifyTopAppBarColors(),
        scrollBehavior = scrollBehavior
    )
}

// ─── App Filter Chips — fully controlled (no internal state) ──────────────────

@Composable
private fun AppFilterChips(
    applications:   List<GotifyApplication>,
    clientToken:    String,
    authBaseUrl:    String,
    selectedAppId:  Int?,
    unreadOnly:     Boolean,
    onToggleUnread: () -> Unit,
    onSelectApp:    (Int?) -> Unit,
    modifier:       Modifier = Modifier
) {
    LazyRow(
        modifier              = modifier,
        contentPadding        = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        // "Unread" is an independent toggle; keep it visually apart from the single-choice app filter.
        item(key = "unread") {
            FilterChip(
                selected    = unreadOnly,
                onClick     = onToggleUnread,
                label       = { Text(stringResource(R.string.filter_unread)) },
                leadingIcon = {
                    Icon(
                        if (unreadOnly) Icons.Outlined.Check else Icons.Outlined.MarkEmailUnread,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                    )
                }
            )
        }
        item(key = "divider") {
            VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp))
        }
        item(key = "all") {
            FilterChip(
                selected = selectedAppId == null,
                onClick  = { onSelectApp(null) },
                label    = { Text(stringResource(R.string.filter_all_apps)) }
            )
        }
        items(applications, key = { "app_${it.id}" }) { app ->
            FilterChip(
                selected    = selectedAppId == app.id,
                onClick     = { onSelectApp(app.id) },
                label       = { Text(app.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = {
                    AppIcon(
                        imageUrl    = app.image,
                        appName     = app.name,
                        clientToken = clientToken,
                        authBaseUrl = authBaseUrl,
                        size        = 20.dp,
                        modifier    = Modifier.clip(CircleShape)
                    )
                }
            )
        }
    }
}
