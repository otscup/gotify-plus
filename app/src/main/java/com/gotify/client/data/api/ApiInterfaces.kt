package com.gotify.client.data.api

import com.gotify.client.data.model.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*



interface AuthApi {

    
    @POST("client")
    suspend fun createClient(
        @Body request: LoginRequest,
        @Header("Authorization") basicAuth: String
    ): Response<ClientResponse>

    
    @DELETE("client/{id}")
    suspend fun deleteClient(
        @Path("id") clientId: Int
    ): Response<Unit>

    
    @GET("client")
    suspend fun getClients(): Response<List<ClientResponse>>
}



interface MessageApi {

    
    @GET("message")
    suspend fun getMessages(
        @Query("limit") limit: Int = 200,
        @Query("since") since: Long? = null
    ): Response<PagedMessages>

    
    @GET("application/{appId}/message")
    suspend fun getMessagesByApp(
        @Path("appId")  appId: Int,
        @Query("limit") limit: Int = 200,
        @Query("since") since: Long? = null
    ): Response<PagedMessages>

    
    @DELETE("message/{id}")
    suspend fun deleteMessage(
        @Path("id") messageId: Long
    ): Response<Unit>

    
    @DELETE("message")
    suspend fun deleteAllMessages(): Response<Unit>

    
    @DELETE("application/{appId}/message")
    suspend fun deleteMessagesByApp(
        @Path("appId") appId: Int
    ): Response<Unit>
}



interface ApplicationApi {

    
    @GET("application")
    suspend fun getApplications(): Response<List<GotifyApplication>>

    
    @POST("application")
    suspend fun createApplication(
        @Body request: CreateApplicationRequest
    ): Response<GotifyApplication>

    
    @PUT("application/{id}")
    suspend fun updateApplication(
        @Path("id")   appId: Int,
        @Body request: CreateApplicationRequest
    ): Response<GotifyApplication>

    
    @DELETE("application/{id}")
    suspend fun deleteApplication(
        @Path("id") appId: Int
    ): Response<Unit>

    
    @Multipart
    @POST("application/{id}/image")
    suspend fun uploadApplicationImage(
        @Path("id")   appId: Int,
        @Part         image: MultipartBody.Part
    ): Response<GotifyApplication>
}



interface UserApi {

    
    @GET("user")
    suspend fun getUsers(): Response<List<GotifyUser>>

    
    @GET("current/user")
    suspend fun getCurrentUser(): Response<GotifyUser>

    
    @POST("user")
    suspend fun createUser(
        @Body request: CreateUserRequest
    ): Response<GotifyUser>

    
    @GET("user/{id}")
    suspend fun getUser(
        @Path("id") userId: Int
    ): Response<GotifyUser>

    
    @POST("user/{id}")
    suspend fun updateUser(
        @Path("id")   userId: Int,
        @Body request: UpdateUserRequest
    ): Response<GotifyUser>

    
    @DELETE("user/{id}")
    suspend fun deleteUser(
        @Path("id") userId: Int
    ): Response<Unit>
}



interface ServerApi {

    
    @GET("version")
    suspend fun getVersion(): Response<VersionInfo>

    
    @GET("health")
    suspend fun getHealth(): Response<HealthResponse>
}



/** FCM device registration on the Gotify server (gotify-cf). Auth is the
 *  same client-token (X-Gotify-Key) scheme as the other APIs. The server
 *  only ever sends a signal via FCM ({type:"gotify", messageId, appid});
 *  message content is always pulled through MessageApi afterwards. */
interface FcmApi {

    @POST("fcm/device")
    suspend fun registerDevice(
        @Body request: FcmDeviceRegisterRequest
    ): Response<Unit>

    @DELETE("fcm/device")
    suspend fun unregisterDevice(
        @Query("token") token: String
    ): Response<Unit>
}

/** UnifiedPush distributor endpoints on the Gotify server (gotify-cf).
 *  Auth is the same client-token (X-Gotify-Key) scheme as the other APIs.
 *  Payloads are WebPush-encrypted end to end; the server only ever sees
 *  ciphertext, and this client only forwards it to the registered app. */
interface UnifiedPushApi {

    @POST("up/register")
    suspend fun registerEndpoint(
        @Body request: UpRegisterRequest
    ): Response<UpRegisterResponse>

    @DELETE("up/{token}")
    suspend fun unregisterEndpoint(
        @Path("token") token: String
    ): Response<Unit>

    @GET("up/pending")
    suspend fun getPending(): Response<UpPendingResponse>
}
