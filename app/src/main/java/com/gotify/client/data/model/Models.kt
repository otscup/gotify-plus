package com.gotify.client.data.model

import com.google.gson.annotations.SerializedName




data class GotifyServer(
    val id: Long = 0,
    val name: String,
    val baseUrl: String,
    val clientToken: String,
    val isActive: Boolean = false
)



data class LoginRequest(
    @SerializedName("name") val name: String
)

data class ClientResponse(
    @SerializedName("id")    val id: Int,
    @SerializedName("name")  val name: String,
    @SerializedName("token") val token: String
)

data class VersionInfo(
    @SerializedName("version")   val version: String,
    @SerializedName("commit")    val commit: String,
    @SerializedName("buildDate") val buildDate: String
)

data class HealthResponse(
    @SerializedName("health")   val health: String,
    @SerializedName("database") val database: String
)



data class GotifyApplication(
    @SerializedName("id")          val id: Int,
    @SerializedName("token")       val token: String? = null,
    @SerializedName("name")        val name: String,
    @SerializedName("description") val description: String,
    @SerializedName("internal")    val internal: Boolean,
    @SerializedName("image")       val image: String
)

data class CreateApplicationRequest(
    @SerializedName("name")        val name: String,
    @SerializedName("description") val description: String = ""
)



data class GotifyMessage(
    @SerializedName("id")       val id: Long,
    @SerializedName("appid")    val appId: Int,
    @SerializedName("message")  val message: String,
    @SerializedName("title")    val title: String,
    @SerializedName("priority") val priority: Int,
    @SerializedName("date")     val date: String,
    @SerializedName("extras")   val extras: MessageExtras? = null,
    val isRead: Boolean = false
)


data class MessageExtras(
    @SerializedName("client::display")      val display: ClientDisplay? = null,
    @SerializedName("client::notification") val notification: ClientNotification? = null,
    @SerializedName("android::action")      val action: AndroidAction? = null,
    // Sender-defined action buttons (label + URL), rendered generically by the
    // client in the detail screen and as notification actions — the sender
    // changes its payload, never the app. Same key the web inbox uses.
    @SerializedName("notify::actions")      val notifyActions: List<NotifyAction>? = null
)

data class NotifyAction(
    @SerializedName("label") val label: String,
    @SerializedName("url")   val url: String
)

data class ClientDisplay(
    @SerializedName("contentType") val contentType: String?
)

data class ClientNotification(
    @SerializedName("bigImageUrl") val bigImageUrl: String? = null,
    @SerializedName("click")       val click: NotificationClick? = null
)

data class NotificationClick(
    @SerializedName("url") val url: String? = null
)

data class AndroidAction(
    @SerializedName("onReceive") val onReceive: ActionIntent? = null,
    @SerializedName("onClick")   val onClick: ActionIntent? = null
)

data class ActionIntent(
    @SerializedName("intentUrl") val intentUrl: String?
)


data class PagedMessages(
    @SerializedName("messages") val messages: List<GotifyMessage>,
    @SerializedName("paging")   val paging: Paging
)

data class Paging(
    @SerializedName("limit") val limit: Int,
    @SerializedName("since") val since: Long?,
    @SerializedName("size")  val size: Int,
    @SerializedName("next")  val next: String?
)



data class GotifyUser(
    @SerializedName("id")    val id: Int,
    @SerializedName("name")  val name: String,
    @SerializedName("admin") val admin: Boolean
)

data class CreateUserRequest(
    @SerializedName("name")     val name: String,
    @SerializedName("pass")     val pass: String,
    @SerializedName("admin")    val admin: Boolean = false
)

data class UpdateUserRequest(
    @SerializedName("name")  val name: String? = null,
    @SerializedName("pass")  val pass: String? = null,
    @SerializedName("admin") val admin: Boolean? = null
)




sealed class StreamState {
    object Connecting  : StreamState()
    object Connected   : StreamState()
    data class Message(val message: GotifyMessage) : StreamState()
    data class Error(val reason: String)            : StreamState()
    data class Closed(val code: Int, val reason: String) : StreamState()
}




sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val code: Int?, val message: String) : ApiResult<Nothing>()
    object Loading : ApiResult<Nothing>()
}
