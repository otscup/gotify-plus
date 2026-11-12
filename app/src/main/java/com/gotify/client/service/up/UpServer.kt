package com.gotify.client.service.up

import com.gotify.client.BuildConfig
import com.gotify.client.data.api.GotifyApiClient
import com.gotify.client.data.api.NetworkClientFactory
import com.gotify.client.data.datastore.PreferencesRepository
import com.gotify.client.data.db.ServerDao
import com.gotify.client.data.db.toDomain
import com.gotify.client.data.model.GotifyServer
import kotlinx.coroutines.flow.first

/** Active server with a usable client token, or null when logged out. */
internal suspend fun activeGotifyServer(
    prefs: PreferencesRepository,
    serverDao: ServerDao
): GotifyServer? {
    val preferences = prefs.userPreferences.first()
    return serverDao.getServerById(preferences.activeServerId)?.toDomain()
        ?.takeIf { it.clientToken.isNotBlank() }
}

/** API client for a server, or null when the URL is invalid. */
internal fun apiClientFor(server: GotifyServer): GotifyApiClient? =
    runCatching { NetworkClientFactory.create(server, BuildConfig.DEBUG) }.getOrNull()

/** Endpoint token from a full endpoint URL (https://…/up/<token>). */
internal fun tokenOfEndpoint(endpoint: String): String =
    endpoint.substringAfterLast("/up/").substringBefore('?').substringBefore('#')
