package com.gotify.client.data.repository

import com.gotify.client.data.api.GotifyApiClient
import com.gotify.client.data.api.buildBasicAuth
import com.gotify.client.data.model.*
import com.gotify.client.util.safeApiCall
import com.gotify.client.util.safeApiCallUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject




class AuthRepository @Inject constructor(
    private val api: GotifyApiClient
) {

    
    suspend fun loginWithPassword(
        username: String,
        password: String,
        clientName: String = "GotifyPlus Android"
    ): ApiResult<ClientResponse> {
        val basicAuth = buildBasicAuth(username, password)
        return safeApiCall {
            api.auth.createClient(LoginRequest(clientName), basicAuth)
        }
    }

    
    suspend fun verifyToken(): ApiResult<GotifyUser> =
        safeApiCall { api.users.getCurrentUser() }

    
    suspend fun logout(clientId: Int): ApiResult<Unit> =
        safeApiCallUnit { api.auth.deleteClient(clientId) }

    
    suspend fun getClients(): ApiResult<List<ClientResponse>> =
        safeApiCall { api.auth.getClients() }

    
    suspend fun getServerVersion(): ApiResult<VersionInfo> =
        safeApiCall { api.server.getVersion() }

    
    suspend fun getServerHealth(): ApiResult<HealthResponse> =
        safeApiCall { api.server.getHealth() }
}




class MessageRepository @Inject constructor(
    private val api: GotifyApiClient
) {

    data class MessageSyncResult(
        val messages: List<GotifyMessage>,
        val complete: Boolean
    )

    
    suspend fun getMessages(
        limit: Int = 200,
        since: Long? = null
    ): ApiResult<PagedMessages> =
        safeApiCall { api.messages.getMessages(limit, since) }

    
    suspend fun getMessagesByApp(
        appId: Int,
        limit: Int = 200,
        since: Long? = null
    ): ApiResult<PagedMessages> =
        safeApiCall { api.messages.getMessagesByApp(appId, limit, since) }

    suspend fun getAllMessages(
        pageSize: Int = 200,
        maxPages: Int = 50
    ): ApiResult<MessageSyncResult> {
        val messages = mutableListOf<GotifyMessage>()
        var since: Long? = null

        repeat(maxPages) {
            when (val result = getMessages(pageSize, since)) {
                is ApiResult.Success -> {
                    val page = result.data
                    messages += page.messages
                    val nextSince = page.paging.since
                    val hasMore = page.paging.next != null &&
                        nextSince != null &&
                        nextSince != since &&
                        page.messages.isNotEmpty()
                    if (!hasMore) return ApiResult.Success(MessageSyncResult(messages, complete = true))
                    since = nextSince
                }
                is ApiResult.Error -> return result
                ApiResult.Loading -> Unit
            }
        }

        return ApiResult.Success(MessageSyncResult(messages, complete = false))
    }

    suspend fun getAllMessagesByApp(
        appId: Int,
        pageSize: Int = 200,
        maxPages: Int = 50
    ): ApiResult<MessageSyncResult> {
        val messages = mutableListOf<GotifyMessage>()
        var since: Long? = null

        repeat(maxPages) {
            when (val result = getMessagesByApp(appId, pageSize, since)) {
                is ApiResult.Success -> {
                    val page = result.data
                    messages += page.messages
                    val nextSince = page.paging.since
                    val hasMore = page.paging.next != null &&
                        nextSince != null &&
                        nextSince != since &&
                        page.messages.isNotEmpty()
                    if (!hasMore) return ApiResult.Success(MessageSyncResult(messages, complete = true))
                    since = nextSince
                }
                is ApiResult.Error -> return result
                ApiResult.Loading -> Unit
            }
        }

        return ApiResult.Success(MessageSyncResult(messages, complete = false))
    }

    
    fun getAllMessagesFlow(pageSize: Int = 200, maxPages: Int = 50): Flow<ApiResult<List<GotifyMessage>>> = flow {
        var since: Long? = null
        var hasMore = true
        var pages = 0

        while (hasMore && pages < maxPages) {
            val result = getMessages(pageSize, since)
            when (result) {
                is ApiResult.Success -> {
                    emit(ApiResult.Success(result.data.messages))
                    val nextSince = result.data.paging.since
                    hasMore = result.data.paging.next != null && nextSince != null && nextSince != since && result.data.messages.isNotEmpty()
                    since = nextSince
                    pages++
                }
                is ApiResult.Error -> {
                    emit(result)
                    hasMore = false
                }
                ApiResult.Loading -> {  }
            }
        }
    }

    
    fun getAppMessagesFlow(appId: Int, pageSize: Int = 200, maxPages: Int = 50): Flow<ApiResult<List<GotifyMessage>>> = flow {
        var since: Long? = null
        var hasMore = true
        var pages = 0

        while (hasMore && pages < maxPages) {
            val result = getMessagesByApp(appId, pageSize, since)
            when (result) {
                is ApiResult.Success -> {
                    emit(ApiResult.Success(result.data.messages))
                    val nextSince = result.data.paging.since
                    hasMore = result.data.paging.next != null && nextSince != null && nextSince != since && result.data.messages.isNotEmpty()
                    since = nextSince
                    pages++
                }
                is ApiResult.Error -> {
                    emit(result)
                    hasMore = false
                }
                ApiResult.Loading -> {}
            }
        }
    }

    
    suspend fun deleteMessage(messageId: Long): ApiResult<Unit> =
        safeApiCallUnit { api.messages.deleteMessage(messageId) }

    
    suspend fun deleteAllMessages(): ApiResult<Unit> =
        safeApiCallUnit { api.messages.deleteAllMessages() }

    
    suspend fun deleteMessagesByApp(appId: Int): ApiResult<Unit> =
        safeApiCallUnit { api.messages.deleteMessagesByApp(appId) }
}




class ApplicationRepository @Inject constructor(
    private val api: GotifyApiClient
) {

    
    suspend fun getApplications(): ApiResult<List<GotifyApplication>> =
        safeApiCall { api.applications.getApplications() }

    
    suspend fun createApplication(
        name: String,
        description: String = ""
    ): ApiResult<GotifyApplication> =
        safeApiCall {
            api.applications.createApplication(CreateApplicationRequest(name, description))
        }

    
    suspend fun updateApplication(
        appId: Int,
        name: String,
        description: String = ""
    ): ApiResult<GotifyApplication> =
        safeApiCall {
            api.applications.updateApplication(appId, CreateApplicationRequest(name, description))
        }

    
    suspend fun deleteApplication(appId: Int): ApiResult<Unit> =
        safeApiCallUnit { api.applications.deleteApplication(appId) }

    
    fun resolveImageUrl(baseUrl: String, imagePath: String): String =
        "${baseUrl.trimEnd('/')}/${ imagePath.trimStart('/') }"
}




class UserRepository @Inject constructor(
    private val api: GotifyApiClient
) {
    suspend fun getCurrentUser(): ApiResult<GotifyUser> =
        safeApiCall { api.users.getCurrentUser() }

    suspend fun getUsers(): ApiResult<List<GotifyUser>> =
        safeApiCall { api.users.getUsers() }

    suspend fun createUser(name: String, password: String, admin: Boolean = false): ApiResult<GotifyUser> =
        safeApiCall { api.users.createUser(CreateUserRequest(name, password, admin)) }

    suspend fun updateUser(userId: Int, request: UpdateUserRequest): ApiResult<GotifyUser> =
        safeApiCall { api.users.updateUser(userId, request) }

    suspend fun deleteUser(userId: Int): ApiResult<Unit> =
        safeApiCallUnit { api.users.deleteUser(userId) }
}



/** FCM device registration against the Gotify server (gotify-cf). */
class FcmRepository @Inject constructor(
    private val api: GotifyApiClient
) {

    suspend fun registerDevice(request: FcmDeviceRegisterRequest): ApiResult<Unit> =
        safeApiCallUnit { api.fcm.registerDevice(request) }

    suspend fun unregisterDevice(token: String): ApiResult<Unit> =
        safeApiCallUnit { api.fcm.unregisterDevice(token) }
}

/** UnifiedPush endpoint registration / pending pull against gotify-cf.
 *  Payloads are opaque WebPush ciphertext; this repository never decrypts. */
class UnifiedPushRepository @Inject constructor(
    private val api: GotifyApiClient
) {

    suspend fun registerEndpoint(request: UpRegisterRequest): ApiResult<UpRegisterResponse> =
        safeApiCall { api.up.registerEndpoint(request) }

    suspend fun unregisterEndpoint(token: String): ApiResult<Unit> =
        safeApiCallUnit { api.up.unregisterEndpoint(token) }

    suspend fun getPending(): ApiResult<UpPendingResponse> =
        safeApiCall { api.up.getPending() }
}
