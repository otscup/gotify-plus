package com.gotify.client.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.gotify.client.BuildConfig
import com.gotify.client.data.api.NetworkClientFactory
import com.gotify.client.data.datastore.PreferencesRepository
import com.gotify.client.data.db.MessageDao
import com.gotify.client.data.db.ServerDao
import com.gotify.client.data.db.toDomain
import com.gotify.client.data.model.ApiResult
import com.gotify.client.data.repository.MessageRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "GotifyFcm"

/**
 * FCM push path, active while "Keep connection alive" is off.
 *
 * The server only sends a signal ({type:"gotify", messageId, appid}); the
 * message body is never in the FCM payload. On receipt this service pulls
 * the full messages through the normal API and hands them to the single
 * write path (MessageIngestor), exactly like SyncJobService — it never
 * builds a notification itself, so dedup, mute and quiet-hours rules apply.
 */
@AndroidEntryPoint
class GotifyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var prefs: PreferencesRepository
    @Inject lateinit var serverDao: ServerDao
    @Inject lateinit var messageDao: MessageDao
    @Inject lateinit var ingestor: MessageIngestor
    @Inject lateinit var fcmRegistration: FcmDeviceRegistration

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        // Signal-only payload; content is fetched below. Ignore anything
        // that is not a gotify signal to stay robust against misrouted pushes.
        val type = message.data["type"]
        if (type != null && type != "gotify") {
            Log.d(TAG, "Ignoring non-gotify FCM message")
            return
        }
        scope.launch {
            try {
                sync()
            } catch (e: Exception) {
                Log.w(TAG, "FCM-triggered sync failed", e)
            }
        }
    }

    override fun onNewToken(token: String) {
        scope.launch { fcmRegistration.refresh() }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** Mirrors SyncJobService.sync(): pull, filter to unseen, ingest. */
    private suspend fun sync() {
        val preferences = prefs.userPreferences.first()
        if (preferences.keepAliveEnabled) return
        val server = serverDao.getServerById(preferences.activeServerId)?.toDomain() ?: return
        if (server.clientToken.isBlank()) return
        val api = runCatching {
            NetworkClientFactory.create(server, isDebug = BuildConfig.DEBUG)
        }.getOrNull() ?: return

        val lastKnownId = messageDao.maxMessageId(server.id) ?: 0L
        val result = MessageRepository(api).getMessages(limit = 50)
        if (result !is ApiResult.Success) return
        // An empty cache means first sync: store history without a notification burst.
        val notify = lastKnownId > 0
        result.data.messages
            .filter { it.id > lastKnownId }
            .sortedBy { it.id }
            .forEach { ingestor.ingest(server.id, it, notify) }
    }
}
