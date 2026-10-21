package com.gotify.client

import android.app.Application
import android.content.Context
import com.gotify.client.data.api.GotifyWebSocketManager
import com.gotify.client.util.currentLocalized
import com.gotify.client.data.db.MessageDao
import com.gotify.client.data.model.StreamState
import com.gotify.client.data.repository.ServerManager
import com.gotify.client.service.MessageIngestor
import com.gotify.client.widget.UnreadWidgetProvider
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class GotifyApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.currentLocalized())
    }

    @Inject lateinit var webSocketManager: GotifyWebSocketManager
    @Inject lateinit var serverManager: ServerManager
    @Inject lateinit var ingestor: MessageIngestor
    @Inject lateinit var messageDao: MessageDao

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()

        // The one place live messages are stored and notified, whether or not the foreground service runs.
        appScope.launch {
            webSocketManager.streamState.collect { state ->
                val serverId = serverManager.activeServer.value?.id
                if (state is StreamState.Message && serverId != null) {
                    runCatching { ingestor.ingest(serverId, state.message) }
                }
            }
        }

        appScope.launch {
            serverManager.activeServer
                .map { it?.id }
                .distinctUntilChanged()
                .flatMapLatest { serverId ->
                    if (serverId == null) flowOf(0 to null)
                    else combine(
                        messageDao.getUnreadCount(serverId),
                        messageDao.observeLatestMessage(serverId)
                    ) { unread, latest -> unread to (latest?.title?.ifBlank { null } ?: latest?.message) }
                }
                .distinctUntilChanged()
                .collect { (unread, latest) -> UnreadWidgetProvider.push(this@GotifyApplication, unread, latest) }
        }
    }
}
