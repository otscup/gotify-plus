package com.gotify.client.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.gotify.client.data.datastore.PreferencesRepository
import com.gotify.client.data.db.ApplicationDao
import com.gotify.client.data.db.MessageDao
import com.gotify.client.data.db.toDomain
import com.gotify.client.data.db.toEntity
import com.gotify.client.data.model.GotifyMessage
import com.gotify.client.notification.GotifyNotificationManager
import com.gotify.client.util.sanitized
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MessageIngestor"

/** Single write path for incoming messages (live stream and background sync). */
@Singleton
class MessageIngestor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val messageDao: MessageDao,
    private val applicationDao: ApplicationDao,
    private val notifications: GotifyNotificationManager,
    private val prefs: PreferencesRepository
) {

    // WebSocket, periodic sync and FCM-triggered sync can race on the same
    // message; serialise the check-then-insert so it notifies at most once.
    private val ingestLock = Mutex()

    suspend fun ingest(serverId: Long, message: GotifyMessage, notify: Boolean = true) =
        ingestLock.withLock {
            val existing = messageDao.getMessageById(serverId, message.id)
            messageDao.insertMessage(message.toEntity(serverId, existing?.isRead ?: false))
            if (existing != null || !notify) return@withLock

            message.extras?.action?.onReceive?.intentUrl?.takeIf { it.isNotBlank() }?.let { intentUrl ->
                // Server-supplied broadcasts are opt-in: a compromised server must not drive other apps by default.
                if (prefs.userPreferences.first().serverIntentsEnabled) {
                    runCatching { Intent.parseUri(intentUrl, Intent.URI_INTENT_SCHEME) }
                        .onSuccess { context.sendBroadcast(it.sanitized()) }
                        .onFailure { Log.w(TAG, "Ignoring invalid onReceive intent") }
                }
            }

            val app = applicationDao.getApplicationById(serverId, message.appId)?.toDomain()
            if (app != null) notifications.createAppChannels(serverId, app)
            notifications.postMessageNotification(
                message   = message,
                app       = app,
                tapIntent = detailTapIntent(serverId, message.id),
                serverId  = serverId
            )
        }

    private fun detailTapIntent(serverId: Long, messageId: Long): PendingIntent {
        val intent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("message_id", messageId)
                putExtra("server_id", serverId)
            }
            ?: Intent()
        return PendingIntent.getActivity(
            context, 31 * serverId.hashCode() + messageId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
