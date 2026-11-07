package com.gotify.client.service

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.gotify.client.BuildConfig
import com.gotify.client.data.api.NetworkClientFactory
import com.gotify.client.data.datastore.PreferencesRepository
import com.gotify.client.data.db.ServerDao
import com.gotify.client.data.db.toDomain
import com.gotify.client.data.model.ApiResult
import com.gotify.client.data.model.FcmDeviceRegisterRequest
import com.gotify.client.data.repository.FcmRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FcmRegistration"

/**
 * Keeps the server-side FCM device registration in sync with the keep-alive
 * switch: FCM is the push path only while keep-alive is off (no foreground
 * WebSocket). With keep-alive on, the stored token is unregistered so the
 * server does not send redundant FCM signals.
 *
 * Registration is best-effort and never throws: login and the settings
 * toggle must keep working when Firebase is unavailable (e.g. before
 * google-services.json is added) or the network is down.
 */
@Singleton
class FcmDeviceRegistration @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val prefs: PreferencesRepository,
    private val serverDao: ServerDao
) {

    /** Register when keep-alive is off, unregister when it is on. No-op when no server is active. */
    suspend fun refresh() {
        runCatching { refreshInternal() }
            .onFailure { Log.w(TAG, "FCM registration refresh failed", it) }
    }

    private suspend fun refreshInternal() {
        val preferences = prefs.userPreferences.first()
        val server = serverDao.getServerById(preferences.activeServerId)?.toDomain()
            ?: return
        if (server.clientToken.isBlank()) return
        val api = runCatching {
            NetworkClientFactory.create(server, isDebug = BuildConfig.DEBUG)
        }.getOrNull() ?: return
        val repo = FcmRepository(api)

        if (preferences.keepAliveEnabled) {
            val stored = prefs.fcmToken.first()
            if (!stored.isNullOrBlank() &&
                repo.unregisterDevice(stored) is ApiResult.Success
            ) {
                prefs.setFcmToken(null)
            }
            return
        }

        val token = FirebaseMessaging.getInstance().token.await()
        if (token.isBlank()) return
        val request = FcmDeviceRegisterRequest(
            token = token,
            deviceId = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ANDROID_ID
            ),
            appVersion = BuildConfig.VERSION_NAME
        )
        if (repo.registerDevice(request) is ApiResult.Success) {
            prefs.setFcmToken(token)
        }
    }
}
