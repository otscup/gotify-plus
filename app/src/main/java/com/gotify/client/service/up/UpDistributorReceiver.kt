package com.gotify.client.service.up

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.gotify.client.data.datastore.PreferencesRepository
import com.gotify.client.data.db.ServerDao
import com.gotify.client.data.db.toDomain
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.unifiedpush.android.distributor.UnifiedPushDistributor
import org.unifiedpush.android.distributor.receiver.DistributorReceiver
import javax.inject.Inject

private const val TAG = "UpReceiver"

/**
 * Entry point for UnifiedPush REGISTER/UNREGISTER broadcasts from other apps
 * (e.g. Nagram). Declared exported in AndroidManifest with the distributor
 * actions; the library's [DistributorReceiver] does the registration flow.
 *
 * [isConnected] is intentionally strict: without a logged-in Gotify server
 * there is no push server to register an endpoint on, so registrations fail
 * with ACTION_REQUIRED instead of silently doing nothing.
 */
@AndroidEntryPoint
class UpDistributorReceiver : DistributorReceiver() {

    @Inject lateinit var gotifyDistributor: GotifyDistributor
    @Inject lateinit var prefs: PreferencesRepository
    @Inject lateinit var serverDao: ServerDao

    override val distributor: UnifiedPushDistributor get() = gotifyDistributor

    override fun isConnected(context: Context): Boolean = runCatching {
        runBlocking {
            val preferences = prefs.userPreferences.first()
            val server = serverDao.getServerById(preferences.activeServerId)?.toDomain()
            server != null && server.clientToken.isNotBlank()
        }
    }.getOrDefault(false)

    override fun hasInternet(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // Toast the user when an app registers or a registration fails; the
    // library only toasts on new registrations and failures, not re-registers.
    override fun showToasts(context: Context): Boolean = true

    /**
     * Endpoints are bound to the server that was active at registration time.
     * If the user switched servers since, the saved endpoint points at the
     * old server and would never deliver: re-register instead of re-sending
     * the stale endpoint. When the endpoint matches the active server (or
     * there is none saved), keep the library's default behaviour.
     */
    override fun onRegisterKnownToken(
        context: Context,
        connectorToken: String,
        application: String,
        vapid: String?,
        description: String?
    ) {
        val savedEndpoint = distributor.getDb(context).getEndpoint(connectorToken)
        val activeBase = runCatching {
            runBlocking {
                val preferences = prefs.userPreferences.first()
                serverDao.getServerById(preferences.activeServerId)?.toDomain()
                    ?.baseUrl?.trimEnd('/')
            }
        }.getOrNull()
        val matchesServer = savedEndpoint != null && activeBase != null &&
            savedEndpoint.startsWith("$activeBase/up/")
        if (matchesServer) {
            super.onRegisterKnownToken(context, connectorToken, application, vapid, description)
        } else {
            Log.i(TAG, "Saved UP endpoint does not match the active server, re-registering $application")
            onRegisterUpdatedToken(context, connectorToken, application, vapid, description)
        }
    }
}
