package com.gotify.client.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "GotifyFcm"

/**
 * FCM push path, active while "Keep connection alive" is off.
 *
 * The server only sends a signal ({type:"gotify", messageId, appid}); the
 * message body is never in the FCM payload. The pull is handed to
 * [SyncJobService] as a one-shot job: FCM stops this service right after
 * onMessageReceived returns and onDestroy cancels [scope], so network work
 * started here would be cancelled mid-flight (observed as "Job was
 * cancelled" ~300ms after the wake-up). The job keeps the process alive until
 * the pull finishes, and MessageIngestor still dedups the ingest.
 */
@AndroidEntryPoint
class GotifyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var fcmRegistration: FcmDeviceRegistration

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        // Signal-only payloads; content is fetched by the sync job below.
        // "gotify" pulls new Gotify messages; "unifiedpush" pulls queued
        // UnifiedPush payloads via the same job (see SyncJobService).
        // Anything else is ignored to stay robust against misrouted pushes.
        val type = message.data["type"]
        if (type != null && type != "gotify" && type != "unifiedpush") {
            Log.d(TAG, "Ignoring non-gotify FCM message")
            return
        }
        SyncJobService.scheduleImmediate(this)
    }

    override fun onNewToken(token: String) {
        scope.launch { fcmRegistration.refresh() }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
