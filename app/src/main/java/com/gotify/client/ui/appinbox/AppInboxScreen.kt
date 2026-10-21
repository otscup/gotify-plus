package com.gotify.client.ui.appinbox

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import com.gotify.client.ui.components.LoadMoreEffect
import com.gotify.client.ui.components.LoadingMoreIndicator
import com.gotify.client.ui.components.gotifyTopAppBarColors
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.gotify.client.R
import com.gotify.client.data.model.GotifyMessage
import com.gotify.client.ui.components.EmptyState
import com.gotify.client.ui.components.MessageCard
import com.gotify.client.ui.components.MessageCardSkeleton
import com.gotify.client.ui.components.DateHeader
import com.gotify.client.ui.components.dayLabel
import com.gotify.client.ui.components.rememberUndoableDelete
import com.gotify.client.ui.apps.AppIconResolved

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppInboxScreen(
    appName: String,
    appImageUrl: String?,
    clientToken: String,
    serverBaseUrl: String,
    messages: List<GotifyMessage>,
    isLoading: Boolean,
    isRefreshing: Boolean,
    hasMorePages: Boolean,
    cacheSyncTruncated: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
    onMessageClick: (GotifyMessage) -> Unit,
    onDeleteMessage: (Long) -> Unit,
    onUndoDelete: (Long) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearDialog by remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()
    val snackbarHostState = remember { SnackbarHostState() }
    val deleteWithUndo = rememberUndoableDelete(snackbarHostState, onDeleteMessage, onUndoDelete)
    val context = LocalContext.current
    val sections = remember(messages) { messages.groupBy { dayLabel(it.date, context) } }
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    LoadMoreEffect(listState, hasMorePages && !isLoading, onLoadMore)

    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppIconResolved(
                            resolvedImageUrl = appImageUrl,
                            appName = appName,
                            clientToken = clientToken,
                            authBaseUrl = serverBaseUrl,
                            size = 32.dp
                        )
                        Text(
                            appName,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = stringResource(R.string.cd_clear_all_messages))
                        }
                    }
                },
                colors = gotifyTopAppBarColors(),
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            state = pullState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading && messages.isEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().widthIn(max = 840.dp).align(Alignment.TopCenter),
                    userScrollEnabled = false,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(5) { MessageCardSkeleton() }
                }
            } else if (messages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Outlined.Inbox,
                        title = stringResource(R.string.empty_none_title),
                        subtitle = stringResource(R.string.inbox_empty_sub, appName)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().widthIn(max = 840.dp).align(Alignment.TopCenter),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    sections.forEach { (label, dayMessages) ->
                        stickyHeader(key = "header_$label") { DateHeader(label) }
                        items(dayMessages, key = { it.id }) { message ->
                            MessageCard(
                                title = message.title,
                                message = message.message,
                                appName = appName,
                                appImageUrl = appImageUrl,
                                clientToken = clientToken,
                                authBaseUrl = serverBaseUrl,
                                priority = message.priority,
                                date = message.date,
                                isRead = message.isRead,
                                onClick = { onMessageClick(message) },
                                onDelete = { deleteWithUndo(message.id) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                    if (cacheSyncTruncated) {
                        item(key = "truncated") {
                            Text(
                                stringResource(R.string.sync_truncated_note_safety),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                    if (hasMorePages && !isLoading) {
                        item(key = "load_more") { LoadingMoreIndicator() }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = {
                Icon(
                    Icons.Outlined.DeleteSweep,
                    null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.dialog_clear_all_title)) },
            text = { Text(stringResource(R.string.dialog_clear_all_body, appName)) },
            confirmButton = {
                Button(
                    onClick = { onClearAll(); showClearDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text(stringResource(R.string.action_clear_all)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}
