package com.gotify.client.service.up

import android.content.Context
import android.util.Base64
import android.util.Log
import com.gotify.client.data.datastore.PreferencesRepository
import com.gotify.client.data.db.ServerDao
import com.gotify.client.data.model.ApiResult
import com.gotify.client.data.repository.UnifiedPushRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "UpForwarder"

/**
 * Delivers UnifiedPush payloads (opaque WebPush ciphertext) to the apps
 * registered with this distributor. Never decrypts, never stores, never
 * notifies: the target app decrypts the bytes and notifies itself.
 *
 * Two sources:
 * - Live websocket frames {"type":"unifiedpush","payload":"<base64>"}: the
 *   server sends no endpoint id, so with exactly one registered app the
 *   payload goes to it; with several it goes to all of them (each app
 *   ignores payloads it cannot decrypt).
 * - GET /up/pending: carries the endpoint token, so delivery is exact.
 */
@Singleton
class UnifiedPushForwarder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val distributor: GotifyDistributor,
    private val prefs: PreferencesRepository,
    private val serverDao: ServerDao,
) {

    /** Live frame from the websocket (no endpoint id). */
    fun forwardLive(payloadB64: String) {
        val bytes = decode(payloadB64) ?: return
        val db = distributor.getDb(context)
        val apps = db.listApps()
        if (apps.isEmpty()) {
            Log.w(TAG, "UP live payload received but no app is registered")
            return
        }
        if (apps.size > 1) {
            Log.w(TAG, "UP live payload: ${apps.size} apps registered, delivering to all")
        }
        for (app in apps) {
            val channelId = db.getChannelId(app.connectorToken) ?: continue
            distributor.sendMessage(context, channelId, bytes)
        }
    }

    /** One queued payload, matched to its endpoint token. */
    fun forwardForEndpoint(endpointToken: String, payloadB64: String) {
        val bytes = decode(payloadB64) ?: return
        val db = distributor.getDb(context)
        val app = db.listApps().firstOrNull { app ->
            app.endpoint?.let { tokenOfEndpoint(it) == endpointToken } == true
        }
        if (app == null) {
            Log.w(TAG, "UP pending payload for unknown endpoint $endpointToken, dropping")
            return
        }
        val channelId = db.getChannelId(app.connectorToken) ?: return
        distributor.sendMessage(context, channelId, bytes)
    }

    /**
     * Pull queued payloads from the active server and forward them.
     * The server deletes returned rows, so only call when ready to forward
     * (this function forwards synchronously right after pulling).
     */
    suspend fun pullPendingForActiveServer() {
        val server = activeGotifyServer(prefs, serverDao) ?: return
        val api = apiClientFor(server) ?: return
        when (val result = UnifiedPushRepository(api).getPending()) {
            is ApiResult.Success -> result.data.messages.forEach { msg ->
                runCatching { forwardForEndpoint(msg.endpoint, msg.payload) }
                    .onFailure { Log.w(TAG, "UP forward failed for ${msg.endpoint}", it) }
            }
            is ApiResult.Error -> Log.w(TAG, "UP pending pull failed: ${result.code} ${result.message}")
            ApiResult.Loading -> Unit
        }
    }

    private fun decode(payloadB64: String): ByteArray? = runCatching {
        Base64.decode(payloadB64, Base64.DEFAULT)
    }.onFailure { Log.w(TAG, "UP payload is not valid base64") }.getOrNull()
}
