package com.gotify.client.service.up

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.gotify.client.data.datastore.PreferencesRepository
import com.gotify.client.data.db.ServerDao
import com.gotify.client.data.model.ApiResult
import com.gotify.client.data.model.UpRegisterRequest
import com.gotify.client.data.repository.UnifiedPushRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.unifiedpush.android.distributor.ChannelCreationStatus
import org.unifiedpush.android.distributor.Database
import org.unifiedpush.android.distributor.FailedReason
import org.unifiedpush.android.distributor.UnifiedPushDistributor
import org.unifiedpush.android.distributor.WakeLock
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "UpDistributor"

/**
 * UnifiedPush distributor backed by gotify-cf (/up/… endpoints).
 *
 * - Register: POST /up/register with the requesting app's package name as
 *   `target`; the returned endpoint URL is handed to the app by the
 *   library ([finishRegistration]).
 * - Unregister: DELETE /up/<token>, then the local record is removed.
 * - Payloads are WebPush-encrypted end to end. This class never decrypts
 *   them; [UnifiedPushForwarder] only routes the opaque bytes.
 *
 * The server generates a fresh token on every register, so the previous
 * endpoint is DELETEed first on update/unregister — otherwise
 * re-registrations accumulate towards the server's per-user endpoint cap.
 * The endpoint URL is also mirrored in SharedPreferences keyed by channel
 * id, because the library's registerApp() wipes the endpoint column before
 * [backendUpdateChannelId] runs.
 */
@Singleton
class GotifyDistributor @Inject constructor(
    private val prefs: PreferencesRepository,
    private val serverDao: ServerDao,
) : UnifiedPushDistributor() {

    override val receiverComponent = UpDistributorReceiver::class.java

    override fun getDb(context: Context): Database = UpDatabaseFactory.getDb(context)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun endpointPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences("unifiedpush_endpoints", Context.MODE_PRIVATE)

    override fun backendRegisterNewChannelId(
        context: Context,
        packageName: String,
        channelId: String,
        title: String?,
        vapid: String?,
        description: String?
    ) {
        // The library's duplicate-registration guard only covers the
        // synchronous part of onReceive; a second REGISTER for the same
        // channel id can still reach us while the first call is in flight,
        // so a previously stored endpoint is dropped here as well.
        scope.launch {
            runCatching {
                registerChannel(
                    context,
                    packageName,
                    channelId,
                    oldEndpoint = endpointPrefs(context).getString(channelId, null)
                )
            }.onFailure { Log.e(TAG, "UP register failed", it) }
        }
    }

    override fun backendUpdateChannelId(
        context: Context,
        packageName: String,
        channelId: String,
        title: String?,
        vapid: String?,
        description: String?
    ) {
        scope.launch {
            runCatching {
                registerChannel(
                    context,
                    packageName,
                    channelId,
                    oldEndpoint = endpointPrefs(context).getString(channelId, null)
                )
            }.onFailure { Log.e(TAG, "UP register failed", it) }
        }
    }

    override fun backendUnregisterChannelId(context: Context, channelId: String) {
        scope.launch {
            runCatching {
                val ctx = context.applicationContext
                val wakeLock = WakeLock(ctx).also { it.acquire() }
                try {
                    endpointPrefs(ctx).getString(channelId, null)?.let { endpoint ->
                        deleteEndpoint(ctx, tokenOfEndpoint(endpoint))
                    }
                    endpointPrefs(ctx).edit().remove(channelId).apply()
                } finally {
                    wakeLock.release()
                    // Always drop the local record, even if the server DELETE failed;
                    // a leftover server-side endpoint just stops delivering.
                    deleteChannelFromServer(ctx, channelId)
                }
            }.onFailure { Log.e(TAG, "UP unregister failed", it) }
        }
    }

    private suspend fun registerChannel(
        context: Context,
        packageName: String,
        channelId: String,
        oldEndpoint: String?
    ) {
        val ctx = context.applicationContext
        val wakeLock = WakeLock(ctx).also { it.acquire() }
        try {
            val server = activeGotifyServer(prefs, serverDao) ?: run {
                finishRegistration(ctx, ChannelCreationStatus.Error(channelId, FailedReason.ACTION_REQUIRED))
                return
            }
            val api = apiClientFor(server) ?: run {
                finishRegistration(ctx, ChannelCreationStatus.Error(channelId, FailedReason.INTERNAL_ERROR))
                return
            }
            val repo = UnifiedPushRepository(api)
            oldEndpoint?.let { runCatching { repo.unregisterEndpoint(tokenOfEndpoint(it)) } }
            when (val result = repo.registerEndpoint(UpRegisterRequest(target = packageName))) {
                is ApiResult.Success -> {
                    endpointPrefs(ctx).edit().putString(channelId, result.data.endpoint).apply()
                    finishRegistration(ctx, ChannelCreationStatus.Ok(channelId, result.data.endpoint))
                }
                is ApiResult.Error -> {
                    Log.w(TAG, "UP register failed for $packageName: ${result.code} ${result.message}")
                    finishRegistration(ctx, ChannelCreationStatus.Error(channelId, FailedReason.NETWORK))
                }
                ApiResult.Loading -> Unit
            }
        } finally {
            wakeLock.release()
        }
    }

    private suspend fun deleteEndpoint(context: Context, token: String) {
        val server = activeGotifyServer(prefs, serverDao) ?: return
        val api = apiClientFor(server) ?: return
        runCatching { UnifiedPushRepository(api).unregisterEndpoint(token) }
            .onFailure { Log.w(TAG, "UP unregister $token failed", it) }
    }
}
