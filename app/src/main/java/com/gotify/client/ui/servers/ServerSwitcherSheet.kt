package com.gotify.client.ui.servers

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.res.stringResource
import com.gotify.client.R
import com.gotify.client.data.model.GotifyServer
import com.gotify.client.ui.components.ConnectionDot
import com.gotify.client.ui.components.ConnectionStatus
import com.gotify.client.ui.components.SectionHeader
import com.gotify.client.ui.viewmodel.ServerInfoUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSwitcherSheet(
    servers: List<GotifyServer>,
    connectionStatus: ConnectionStatus,
    serverInfo: ServerInfoUiState,
    onSwitchServer: (Long) -> Unit,
    onRemoveServer: (Long) -> Unit,
    onRefreshInfo: () -> Unit,
    onAddServer: () -> Unit,
    onDismiss: () -> Unit
) {
    var serverToRemove by remember { mutableStateOf<GotifyServer?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Many saved servers must stay reachable on short screens.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.servers_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            ConnectionCard(
                connectionStatus = connectionStatus,
                serverInfo       = serverInfo,
                onRefreshInfo    = onRefreshInfo
            )

            if (servers.isNotEmpty()) {
                SectionHeader(
                    title    = stringResource(R.string.section_saved_servers),
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }

            servers.forEach { server ->
                ServerRow(
                    server = server,
                    connectionStatus = connectionStatus,
                    onClick = { if (!server.isActive) { onSwitchServer(server.id); onDismiss() } },
                    onRemove = { serverToRemove = server }
                )
            }

            if (servers.isEmpty()) {
                Text(
                    stringResource(R.string.servers_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            OutlinedButton(
                onClick = { onDismiss(); onAddServer() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.action_add_server))
            }
        }
    }

    serverToRemove?.let { server ->
        AlertDialog(
            onDismissRequest = { serverToRemove = null },
            icon = { Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.dialog_remove_server_title, server.name)) },
            text = {
                Text(
                    if (server.isActive && servers.size > 1)
                        stringResource(R.string.remove_server_body_switch)
                    else stringResource(R.string.remove_server_body)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveServer(server.id)
                        serverToRemove = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { serverToRemove = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
private fun ConnectionCard(
    connectionStatus: ConnectionStatus,
    serverInfo: ServerInfoUiState,
    onRefreshInfo: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ConnectionDot(connectionStatus)
                    Text(
                        when (connectionStatus) {
                            ConnectionStatus.CONNECTED -> stringResource(R.string.stream_live)
                            ConnectionStatus.CONNECTING -> stringResource(R.string.action_connecting)
                            ConnectionStatus.ERROR -> stringResource(R.string.stream_error)
                            ConnectionStatus.DISCONNECTED -> stringResource(R.string.stream_offline)
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                val healthText = serverInfo.health?.let { stringResource(R.string.label_health, it) }
                val dbText = serverInfo.database?.let { stringResource(R.string.label_db, it) }
                val details = when {
                    serverInfo.isLoading -> stringResource(R.string.checking_server)
                    serverInfo.version != null || serverInfo.health != null ->
                        listOfNotNull(serverInfo.version?.let { "Gotify $it" }, healthText, dbText).joinToString(" · ")
                    else -> serverInfo.errorMessage
                }
                if (details != null) {
                    Text(
                        details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                    )
                }
            }
            if (serverInfo.isLoading) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            } else {
                IconButton(onClick = onRefreshInfo) {
                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.cd_check_server))
                }
            }
        }
    }
}

@Composable
private fun ServerRow(
    server: GotifyServer,
    connectionStatus: ConnectionStatus,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (server.isActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (server.isActive) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (server.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        if (server.isActive) Icons.Outlined.Check else Icons.Outlined.Dns,
                        contentDescription = if (server.isActive) stringResource(R.string.cd_active_server) else null,
                        tint = if (server.isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (server.isActive) ConnectionDot(status = connectionStatus)
                }
                Text(
                    text = server.baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.cd_remove_server, server.name),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
