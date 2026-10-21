package com.gotify.client.ui.viewmodel

import androidx.lifecycle.*
import com.gotify.client.data.api.GotifyWebSocketManager
import com.gotify.client.data.api.NetworkClientFactory
import com.gotify.client.data.api.buildBasicAuth
import com.gotify.client.data.datastore.PreferencesRepository
import com.gotify.client.data.db.*
import com.gotify.client.data.model.*
import com.gotify.client.data.repository.*
import com.gotify.client.ui.components.ConnectionStatus
import com.gotify.client.ui.settings.SettingsState
import com.gotify.client.util.safeApiCall
import android.content.Context
import androidx.annotation.StringRes
import com.gotify.client.R
import com.gotify.client.util.currentLocalized
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext

/** Resolve a user-facing error string in the language chosen in settings. */
private fun localizedErr(context: Context, @StringRes id: Int, vararg args: Any): String =
    context.currentLocalized().getString(id, *args)
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.gotify.client.BuildConfig
import com.gotify.client.notification.GotifyNotificationManager



@HiltViewModel
class AppStartupViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val serverDao:     ServerDao,
    private val prefs:         PreferencesRepository
) : ViewModel() {

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        viewModelScope.launch {
            val savedId = prefs.userPreferences.first().activeServerId
            val servers = serverDao.getAllServers().first()
            val domains = servers.map { it to it.toDomain() }
            val preferredId = savedId.takeIf { id -> domains.any { it.first.id == id } }
                ?: domains.firstOrNull { it.first.isActive }?.first?.id
            val activeId = preferredId?.takeIf { id ->
                domains.firstOrNull { it.first.id == id }?.second?.clientToken?.isNotBlank() == true
            }

            if (activeId != null) serverDao.switchActiveServer(activeId)
            else serverDao.deactivateAll()
            serverManager.reset()
            prefs.setActiveServerId(activeId ?: -1L)

            domains.forEach { (entity, domain) ->
                val shouldActivate = domain.clientToken.isNotBlank() && entity.id == activeId
                val added = serverManager.addServer(domain.copy(isActive = shouldActivate))
                if (!added) {
                    // Do not leave startup suspended forever when an old row
                    // contains a URL that is no longer accepted.
                    serverDao.updateServer(entity.copy(isActive = false))
                    if (shouldActivate) prefs.setActiveServerId(-1L)
                    return@forEach
                }
                if (entity.isActive != shouldActivate) {
                    serverDao.updateServer(entity.copy(isActive = shouldActivate))
                }
                if (!CredentialCipher.isEncrypted(entity.clientToken)) {
                    serverDao.updateServer(
                        entity.copy(
                            clientToken = CredentialCipher.encrypt(entity.clientToken).orEmpty(),
                            isActive = shouldActivate
                        )
                    )
                }
            }
            _ready.value = true
        }
    }
}



@HiltViewModel
class LoginViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val serverDao:     ServerDao,
    private val prefs:         PreferencesRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun loginWithPassword(serverUrl: String, username: String, password: String, serverName: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, loginSuccess = false) }
            if (username.isBlank() || password.isBlank()) {
                _uiState.update { it.copy(isLoading = false, errorMessage = localizedErr(appContext, R.string.err_enter_credentials)) }
                return@launch
            }
            val trimmedUrl = validateServerUrl(serverUrl) ?: run {
                _uiState.update { it.copy(isLoading = false, errorMessage = localizedErr(appContext, R.string.err_enter_https_url)) }
                return@launch
            }
            val tempServer = GotifyServer(name = "", baseUrl = trimmedUrl, clientToken = "")
            val tempApi = try { NetworkClientFactory.create(tempServer) } catch (e: IllegalArgumentException) {
                _uiState.update { it.copy(isLoading = false, errorMessage = localizedErr(appContext, R.string.err_invalid_url)) }
                return@launch
            }

            val versionResult = safeApiCall { tempApi.server.getVersion() }
            if (versionResult is ApiResult.Error) {
                _uiState.update { it.copy(isLoading = false, errorMessage = localizedErr(appContext, R.string.err_cannot_reach, versionResult.message)) }
                return@launch
            }
            val authResult = safeApiCall {
                tempApi.auth.createClient(LoginRequest("GotifyPlus Android"), buildBasicAuth(username, password))
            }
            when (authResult) {
                is ApiResult.Success -> {
                    val client = authResult.data
                    try {
                        saveAndActivate(GotifyServer(name = serverName.ifBlank { trimmedUrl.substringAfter("://") }, baseUrl = trimmedUrl, clientToken = client.token, isActive = true), clientId = client.id)
                        _uiState.update { it.copy(isLoading = false, loginSuccess = true) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        _uiState.update { it.copy(isLoading = false, errorMessage = localizedErr(appContext, R.string.err_store_token)) }
                    }
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = if (authResult.code == 401) localizedErr(appContext, R.string.err_wrong_credentials) else localizedErr(appContext, R.string.err_login_failed, authResult.message))
                }
                else -> {}
            }
        }
    }

    fun loginWithToken(serverUrl: String, token: String, serverName: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, loginSuccess = false) }
            val trimmedToken = token.trim()
            if (trimmedToken.isBlank()) {
                _uiState.update { it.copy(isLoading = false, errorMessage = localizedErr(appContext, R.string.err_enter_token)) }
                return@launch
            }
            val trimmedUrl = validateServerUrl(serverUrl) ?: run {
                _uiState.update { it.copy(isLoading = false, errorMessage = localizedErr(appContext, R.string.err_enter_https_url)) }
                return@launch
            }
            val server = GotifyServer(name = serverName.ifBlank { trimmedUrl.substringAfter("://") }, baseUrl = trimmedUrl, clientToken = trimmedToken, isActive = true)
            when (val result = safeApiCall { NetworkClientFactory.create(server).messages.getMessages(limit = 1) }) {
                is ApiResult.Success -> try {
                    saveAndActivate(server, 0)
                    _uiState.update { it.copy(isLoading = false, loginSuccess = true) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = localizedErr(appContext, R.string.err_store_token)) }
                }
                is ApiResult.Error   -> _uiState.update { it.copy(isLoading = false, errorMessage = if (result.code == 401) "Invalid token" else "Could not connect: ${result.message}") }
                else -> {}
            }
        }
    }

    private suspend fun saveAndActivate(server: GotifyServer, clientId: Int) {
        serverDao.deactivateAll()
        val existing = serverDao.getServerByBaseUrl(server.baseUrl)
        val entity = server.copy(id = existing?.id ?: 0).toEntity(
            clientId.takeIf { it > 0 } ?: existing?.clientId ?: 0
        ).copy(
            isActive = true,
            addedAt = existing?.addedAt ?: System.currentTimeMillis()
        )
        val newId = serverDao.insertServer(entity)
        prefs.setActiveServerId(newId)
        check(serverManager.addServer(server.copy(id = newId, isActive = true))) {
            "Unable to activate the saved server"
        }
    }

    private fun validateServerUrl(raw: String): String? {
        val url = raw.trim().trimEnd('/').toHttpUrlOrNull() ?: return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty() ||
            url.query != null || url.fragment != null
        ) return null
        if (url.scheme != "https" && url.host !in setOf("localhost", "127.0.0.1", "10.0.2.2")) return null
        return url.toString().trimEnd('/')
    }

    fun clearLoginSuccess() {
        _uiState.update { it.copy(loginSuccess = false) }
    }
}

data class LoginUiState(
    val isLoading:    Boolean = false,
    val errorMessage: String? = null,
    val loginSuccess: Boolean = false
)



@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val serverManager:    ServerManager,
    private val messageDao:       MessageDao,
    private val applicationDao:   ApplicationDao,
    private val webSocketManager: GotifyWebSocketManager,
    private val prefs:            PreferencesRepository,
    private val notifications:    GotifyNotificationManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val activeServerName: StateFlow<String> = serverManager.activeServer
        .map { it?.name ?: "Gotify" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Gotify")

    val serverBaseUrl: StateFlow<String> = serverManager.activeServer
        .map { it?.baseUrl.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")


    val clientToken: StateFlow<String> = serverManager.activeServer
        .map { it?.clientToken ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val connectionStatus: StateFlow<ConnectionStatus> = webSocketManager.connectionState
        .map { state ->
            when (state) {
                is StreamState.Connected  -> ConnectionStatus.CONNECTED
                is StreamState.Connecting -> ConnectionStatus.CONNECTING
                is StreamState.Error      -> ConnectionStatus.ERROR
                else                      -> ConnectionStatus.DISCONNECTED
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionStatus.DISCONNECTED)

    private var observedServerId = -1L
    private var messageJob: Job? = null
    private var appJob:     Job? = null
    private var unreadJob:  Job? = null
    private var countJob:   Job? = null
    private var syncJob:    Job? = null
    private val pageLimit = MutableStateFlow(50)

    private val _filter = MutableStateFlow(HomeFilter())
    val filter: StateFlow<HomeFilter> = _filter.asStateFlow()

    private val pendingDeletes = PendingDeletes(viewModelScope) { id -> commitDelete(id) }

    init {
        // Live messages are persisted app-wide (GotifyApplication); Room flows below pick them up.
        viewModelScope.launch {
            serverManager.activeServer
                .filterNotNull()
                .distinctUntilChangedBy { it.id }
                .collect { server ->
                    if (server.id != observedServerId) {
                        observedServerId = server.id
                        startObservingData(server)
                        syncFromServer(server)
                    }
                }
        }
    }

    fun selectApp(appId: Int?) {
        _filter.update { it.copy(appId = if (it.appId == appId) null else appId) }
        pageLimit.value = 50
    }

    fun toggleUnreadOnly() {
        _filter.update { it.copy(unreadOnly = !it.unreadOnly) }
        pageLimit.value = 50
    }

    private fun startObservingData(server: GotifyServer) {
        messageJob?.cancel(); appJob?.cancel(); unreadJob?.cancel(); countJob?.cancel()
        pageLimit.value = 50
        _filter.value = HomeFilter()
        _uiState.update {
            it.copy(
                messages = emptyList(),
                applications = emptyList(),
                unreadCount = 0,
                cachedMessageCount = 0,
                isLoading = true,
                isRefreshing = false,
                hasMorePages = false,
                cacheSyncTruncated = false,
                errorMessage = null
            )
        }

        messageJob = viewModelScope.launch {
            combine(pageLimit, _filter) { limit, filter -> limit to filter }
                .flatMapLatest { (limit, filter) ->
                    messageDao.getMessagesFiltered(server.id, filter.appId, filter.unreadOnly, limit)
                        .map { entities -> entities to (entities.size >= limit) }
                }
                .combine(pendingDeletes.ids) { (entities, hasMore), hidden ->
                    entities.filterNot { it.id in hidden } to hasMore
                }
                .collect { (entities, hasMore) ->
                    _uiState.update {
                        it.copy(messages = entities.map { e -> e.toDomain() }, isLoading = false, hasMorePages = hasMore)
                    }
                }
        }
        appJob = viewModelScope.launch {
            applicationDao.getApplications(server.id).collect { entities ->
                _uiState.update { it.copy(applications = entities.map { e -> e.toDomain().resolvedFor(server) }) }
            }
        }
        unreadJob = viewModelScope.launch {
            messageDao.getUnreadCount(server.id).collect { count ->
                _uiState.update { it.copy(unreadCount = count) }
            }
        }
        countJob = viewModelScope.launch {
            messageDao.getMessageCount(server.id).collect { count ->
                _uiState.update { it.copy(cachedMessageCount = count) }
            }
        }
    }

    private fun syncFromServer(server: GotifyServer) {
        val api = serverManager.apiClient ?: run {
            _uiState.update { it.copy(isRefreshing = false, errorMessage = localizedErr(appContext, R.string.err_no_active_connection)) }
            return
        }
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            try {
                when (val result = ApplicationRepository(api).getApplications()) {
                    is ApiResult.Success -> {
                        val entities = result.data.map { app ->
                            app.toEntity(server.id, applicationDao.getApplicationById(server.id, app.id)?.token)
                        }
                        if (entities.isEmpty()) applicationDao.deleteAllForServer(server.id)
                        else {
                            applicationDao.insertApplications(entities)
                            applicationDao.deleteMissingSafely(server.id, entities.map { it.id })
                        }
                    }
                    is ApiResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                    else -> Unit
                }
                when (val result = MessageRepository(api).getAllMessages()) {
                    is ApiResult.Success -> {
                        val sync = result.data
                        val readIds = messageDao.getReadMessageIds(server.id).toHashSet()
                        _uiState.update { it.copy(cacheSyncTruncated = !sync.complete) }
                        messageDao.insertMessages(sync.messages.map { message ->
                            message.toEntity(server.id, readIds.contains(message.id))
                        })
                        if (sync.complete) {
                            val remoteIds = sync.messages.map { it.id }
                            if (remoteIds.isEmpty()) messageDao.deleteAllMessages(server.id)
                            else messageDao.deleteMissingSafely(server.id, remoteIds)
                        }
                        messageDao.evictOldMessages(server.id, System.currentTimeMillis() - CACHE_RETENTION_MS)
                        prefs.setLastSyncTimestamp(System.currentTimeMillis())
                    }
                    is ApiResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                    else -> Unit
                }
            } finally {
                if (currentCoroutineContext().isActive && serverManager.activeServer.value?.id == server.id) {
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            }
        }
    }

    fun refresh() { serverManager.activeServer.value?.let { syncFromServer(it) } }

    fun loadMore() {
        pageLimit.update { it + 50 }
    }

    fun deleteMessage(messageId: Long) = pendingDeletes.schedule(messageId)

    fun undoDelete(messageId: Long) = pendingDeletes.undo(messageId)

    private suspend fun commitDelete(messageId: Long) {
        val server = serverManager.activeServer.value ?: return
        val api = serverManager.apiClient ?: run {
            _uiState.update { it.copy(errorMessage = localizedErr(appContext, R.string.err_no_active_connection)) }
            return
        }
        when (val result = MessageRepository(api).deleteMessage(messageId)) {
            is ApiResult.Success -> {
                messageDao.deleteMessage(server.id, messageId)
                notifications.cancelNotification(server.id, messageId)
            }
            is ApiResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
            else -> Unit
        }
    }

    fun deleteAllMessages() {
        val server = serverManager.activeServer.value ?: return
        val api = serverManager.apiClient
        if (api == null) {
            _uiState.update { it.copy(errorMessage = localizedErr(appContext, R.string.err_no_active_connection)) }
            return
        }
        viewModelScope.launch {
            when (val result = MessageRepository(api).deleteAllMessages()) {
                is ApiResult.Success -> {
                    messageDao.deleteAllMessages(server.id)
                    notifications.cancelServerNotifications(server.id)
                }
                is ApiResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                else -> Unit
            }
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            if (observedServerId > 0) {
                messageDao.markAllAsRead(observedServerId)
                notifications.cancelServerNotifications(observedServerId)
            }
        }
    }
}

data class HomeUiState(
    val messages:     List<GotifyMessage>         = emptyList(),
    val applications: List<GotifyApplication>     = emptyList(),
    val cachedMessageCount: Int                   = 0,
    val isLoading:    Boolean                     = true,
    val isRefreshing: Boolean                     = false,
    val hasMorePages: Boolean                     = false,
    val unreadCount:  Int                         = 0,
    val cacheSyncTruncated: Boolean               = false,
    val errorMessage: String?                     = null
)

private const val CACHE_RETENTION_MS = 90L * 24 * 60 * 60 * 1000
// Slightly longer than SnackbarDuration.Short (4 s) so a last-moment Undo still wins.
const val UNDO_WINDOW_MS = 5_000L

/**
 * Hides a deleted message immediately and commits the server delete after the undo window.
 * ponytail: pending deletes are dropped (message reappears) if the ViewModel is cleared mid-window — the safe direction.
 */
class PendingDeletes(
    private val scope: CoroutineScope,
    private val commit: suspend (Long) -> Unit
) {
    private val _ids = MutableStateFlow<Set<Long>>(emptySet())
    val ids: StateFlow<Set<Long>> = _ids.asStateFlow()
    private val jobs = mutableMapOf<Long, Job>()

    fun schedule(id: Long) {
        jobs[id]?.cancel()
        _ids.update { it + id }
        jobs[id] = scope.launch {
            delay(UNDO_WINDOW_MS)
            try { commit(id) } finally {
                jobs.remove(id)
                _ids.update { it - id }
            }
        }
    }

    fun undo(id: Long) {
        jobs.remove(id)?.cancel()
        _ids.update { it - id }
    }
}

data class HomeFilter(val appId: Int? = null, val unreadOnly: Boolean = false)

private fun ServerEntity.safeApiClient() = runCatching {
    NetworkClientFactory.create(toDomain(), isDebug = BuildConfig.DEBUG)
}.getOrNull()

private suspend fun MessageDao.deleteMissingSafely(serverId: Long, remoteIds: List<Long>) {
    val remoteIdSet = remoteIds.toHashSet()
    getMessageIds(serverId)
        .filterNot(remoteIdSet::contains)
        .chunked(500)
        .forEach { ids -> if (ids.isNotEmpty()) deleteMessagesByIds(serverId, ids) }
}

private suspend fun MessageDao.deleteMissingForAppSafely(
    serverId: Long,
    appId: Int,
    remoteIds: List<Long>
) {
    val remoteIdSet = remoteIds.toHashSet()
    getMessageIdsForApp(serverId, appId)
        .filterNot(remoteIdSet::contains)
        .chunked(500)
        .forEach { ids -> if (ids.isNotEmpty()) deleteMessagesByIdsForApp(serverId, appId, ids) }
}

private suspend fun ApplicationDao.deleteMissingSafely(serverId: Long, remoteIds: List<Int>) {
    val remoteIdSet = remoteIds.toHashSet()
    getApplicationIds(serverId)
        .filterNot(remoteIdSet::contains)
        .chunked(500)
        .forEach { ids -> if (ids.isNotEmpty()) deleteApplicationsByIds(serverId, ids) }
}



@HiltViewModel
class MessageDetailViewModel @Inject constructor(
    private val messageDao:     MessageDao,
    private val applicationDao: ApplicationDao,
    private val serverManager:  ServerManager,
    private val serverDao:      ServerDao,
    private val notifications:  GotifyNotificationManager,
    savedStateHandle:           SavedStateHandle,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val messageId: Long = checkNotNull(savedStateHandle["messageId"])
    private val requestedServerId: Long = savedStateHandle.get<Long>("serverId") ?: -1L
    private var resolvedServerId: Long = -1L

    private val _message     = MutableStateFlow<GotifyMessage?>(null)
    val message: StateFlow<GotifyMessage?> = _message.asStateFlow()

    private val _application = MutableStateFlow<GotifyApplication?>(null)
    val application: StateFlow<GotifyApplication?> = _application.asStateFlow()
    private val _serverId = MutableStateFlow(-1L)
    val serverId: StateFlow<Long> = _serverId.asStateFlow()
    private val _clientToken = MutableStateFlow("")
    val clientToken: StateFlow<String> = _clientToken.asStateFlow()
    private val _serverBaseUrl = MutableStateFlow("")
    val serverBaseUrl: StateFlow<String> = _serverBaseUrl.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        viewModelScope.launch {
            val serverId = requestedServerId.takeIf { it > 0 }
                ?: serverManager.activeServer.value?.id
                ?: run {
                    _loaded.value = true
                    return@launch
                }
            resolvedServerId = serverId
            _serverId.value = serverId
            serverDao.getServerById(serverId)?.toDomain()?.let { server ->
                _clientToken.value = server.clientToken
                _serverBaseUrl.value = server.baseUrl
            }
            val entity = messageDao.getMessageById(serverId, messageId)
            _message.value = entity?.toDomain()
            entity?.let {
                messageDao.markAsRead(serverId, messageId)
                notifications.cancelNotification(serverId, messageId)
                _application.value = applicationDao.getApplicationById(serverId, it.appId)?.toDomain()
            }
            _loaded.value = true
        }
    }

    fun deleteMessage(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val serverId = resolvedServerId.takeIf { it > 0 } ?: return@launch
            val active = serverManager.activeServer.value
            val api = if (active?.id == serverId) serverManager.apiClient else {
                serverDao.getServerById(serverId)?.safeApiClient()
            }
            when (val result = api?.let { MessageRepository(it).deleteMessage(messageId) }) {
                is ApiResult.Success -> {
                    messageDao.deleteMessage(serverId, messageId)
                    onDeleted()
                }
                is ApiResult.Error -> _errorMessage.value = result.message
                else -> _errorMessage.value = localizedErr(appContext, R.string.err_delete_failed)
            }
            _loaded.value = true
        }
    }
}



@HiltViewModel
class AppsViewModel @Inject constructor(
    private val serverManager:  ServerManager,
    private val applicationDao: ApplicationDao,
    private val messageDao:     MessageDao,
    private val prefs:          PreferencesRepository,
    private val notifications:  GotifyNotificationManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppsUiState())
    val uiState: StateFlow<AppsUiState> = _uiState.asStateFlow()

    /** appId -> muted-until millis for the active server (expired entries excluded). */
    val mutedUntil: StateFlow<Map<Int, Long>> = combine(
        prefs.userPreferences,
        serverManager.activeServer
    ) { p, server ->
        val now = System.currentTimeMillis()
        val prefix = "${server?.id}:"
        p.appMutes.filter { (k, v) -> k.startsWith(prefix) && v > now }
            .mapNotNull { (k, v) -> k.removePrefix(prefix).toIntOrNull()?.let { it to v } }
            .toMap()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** untilMillis = null unmutes. */
    fun setMute(appId: Int, untilMillis: Long?) {
        val serverId = serverManager.activeServer.value?.id ?: return
        viewModelScope.launch {
            prefs.setAppMute(serverId, appId, untilMillis)
            if (untilMillis != null) notifications.cancelServerNotifications(serverId, appId)
        }
    }

    val serverBaseUrl: StateFlow<String> = serverManager.activeServer
        .map { it?.baseUrl ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")


    val clientToken: StateFlow<String> = serverManager.activeServer
        .map { it?.clientToken ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private var observedServerId = -1L
    private var appsJob:   Job? = null
    private var countsJob: Job? = null
    private var syncJob:   Job? = null

    init {
        viewModelScope.launch {
            serverManager.activeServer
                .filterNotNull()
                .distinctUntilChangedBy { it.id }
                .collect { server ->
                    observedServerId = server.id
                    startObservingApps(server)
                    syncAppsFromServer(server)
                }
        }
    }

    private fun startObservingApps(server: GotifyServer) {
        appsJob?.cancel(); countsJob?.cancel()
        _uiState.update {
            it.copy(
                applications = emptyList(),
                messageCounts = emptyMap(),
                isLoading = true,
                isRefreshing = false,
                errorMessage = null
            )
        }

        appsJob = viewModelScope.launch {
            applicationDao.getApplications(server.id).collect { entities ->
                val apps = entities.map { it.toDomain().resolvedFor(server) }
                _uiState.update { it.copy(applications = apps, isLoading = false) }

                countsJob?.cancel()
                countsJob = viewModelScope.launch {
                    val countFlows = apps.map { app ->
                        messageDao.getMessageCountForApp(server.id, app.id).map { app.id to it }
                    }
                    if (countFlows.isEmpty()) { _uiState.update { it.copy(messageCounts = emptyMap()) }; return@launch }
                    combine(countFlows) { pairs -> pairs.toMap() }
                        .collect { counts -> _uiState.update { it.copy(messageCounts = counts) } }
                }
            }
        }
    }

    fun refresh() { serverManager.activeServer.value?.let { syncAppsFromServer(it) } }

    private fun syncAppsFromServer(server: GotifyServer) {
        val api = serverManager.apiClient ?: run {
            _uiState.update { it.copy(isRefreshing = false, errorMessage = localizedErr(appContext, R.string.err_no_active_connection)) }
            return
        }
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            val result = ApplicationRepository(api).getApplications()
            if (result is ApiResult.Success) {
                val entities = result.data.map { app -> app.toEntity(server.id, applicationDao.getApplicationById(server.id, app.id)?.token) }
                if (entities.isEmpty()) applicationDao.deleteAllForServer(server.id) else {
                    applicationDao.insertApplications(entities)
                    applicationDao.deleteMissingSafely(server.id, entities.map { it.id })
                }
            } else if (result is ApiResult.Error) _uiState.update { it.copy(errorMessage = result.message) }
            if (serverManager.activeServer.value?.id == server.id) {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun createApplication(name: String, description: String) {
        val api      = serverManager.apiClient
        val serverId = serverManager.activeServer.value?.id
        if (api == null || serverId == null) {
            _uiState.update { it.copy(errorMessage = localizedErr(appContext, R.string.err_no_active_connection)) }
            return
        }
        viewModelScope.launch {
            val result = ApplicationRepository(api).createApplication(name, description)
            if (result is ApiResult.Success) applicationDao.insertApplication(result.data.toEntity(serverId))
            else if (result is ApiResult.Error) _uiState.update { it.copy(errorMessage = result.message) }
        }
    }

    fun updateApplication(appId: Int, name: String, description: String) {
        val api      = serverManager.apiClient
        val serverId = serverManager.activeServer.value?.id
        if (api == null || serverId == null) {
            _uiState.update { it.copy(errorMessage = localizedErr(appContext, R.string.err_no_active_connection)) }
            return
        }
        viewModelScope.launch {
            val existing = applicationDao.getApplicationById(serverId, appId)
            val result = ApplicationRepository(api).updateApplication(appId, name, description)
            when (result) {
                is ApiResult.Success -> applicationDao.insertApplication(
                    result.data.toEntity(serverId, existing?.token)
                )
                is ApiResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                ApiResult.Loading -> Unit
            }
        }
    }

    fun deleteApplication(appId: Int) {
        val api      = serverManager.apiClient
        val serverId = serverManager.activeServer.value?.id
        if (api == null || serverId == null) {
            _uiState.update { it.copy(errorMessage = localizedErr(appContext, R.string.err_no_active_connection)) }
            return
        }
        viewModelScope.launch {
            val app = applicationDao.getApplicationById(serverId, appId) ?: return@launch
            if (app.internal) return@launch
            val result = ApplicationRepository(api).deleteApplication(appId)
            if (result is ApiResult.Success) {
                applicationDao.deleteApplication(serverId, appId)
                messageDao.deleteMessagesByApp(serverId, appId)
                notifications.cancelServerNotifications(serverId, appId)
            } else if (result is ApiResult.Error) _uiState.update { it.copy(errorMessage = result.message) }
        }
    }

    fun deleteAppMessages(appId: Int) {
        val api      = serverManager.apiClient
        val serverId = serverManager.activeServer.value?.id
        if (api == null || serverId == null) {
            _uiState.update { it.copy(errorMessage = localizedErr(appContext, R.string.err_no_active_connection)) }
            return
        }
        viewModelScope.launch {
            val result = MessageRepository(api).deleteMessagesByApp(appId)
            if (result is ApiResult.Success) {
                messageDao.deleteMessagesByApp(serverId, appId)
                notifications.cancelServerNotifications(serverId, appId)
            } else if (result is ApiResult.Error) _uiState.update { it.copy(errorMessage = result.message) }
        }
    }
}

data class AppsUiState(
    val applications:  List<GotifyApplication> = emptyList(),
    val messageCounts: Map<Int, Int>            = emptyMap(),
    val isLoading:     Boolean                  = true,
    val isRefreshing:  Boolean                  = false,
    val errorMessage:  String?                  = null
)






@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs:         PreferencesRepository,
    private val serverManager: ServerManager,
    private val serverDao:     ServerDao,
    private val messageDao:    MessageDao,
    private val applicationDao:ApplicationDao,
    private val notifications: GotifyNotificationManager
) : ViewModel() {

    val settingsState: StateFlow<SettingsState> = combine(
        prefs.userPreferences,
        serverManager.activeServer
    ) { userPrefs, server ->
        SettingsState(
            notificationsEnabled = userPrefs.notificationsEnabled,
            vibrationEnabled     = userPrefs.vibrationEnabled,
            dynamicColorEnabled  = userPrefs.dynamicColorEnabled,
            themeMode            = userPrefs.themeMode,
            appLanguage          = userPrefs.appLanguage,
            markdownEnabled      = userPrefs.markdownEnabled,
            keepAliveEnabled     = userPrefs.keepAliveEnabled,
            quietHoursEnabled    = userPrefs.quietHoursEnabled,
            quietStartMinutes    = userPrefs.quietStartMinutes,
            quietEndMinutes      = userPrefs.quietEndMinutes,
            appLockEnabled       = userPrefs.appLockEnabled,
            serverIntentsEnabled = userPrefs.serverIntentsEnabled,
            serverName           = server?.name ?: "",
            serverUrl            = server?.baseUrl ?: "",
            appVersion           = BuildConfig.VERSION_NAME
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsState())




    fun setNotifications(v: Boolean) = viewModelScope.launch { prefs.setNotificationsEnabled(v) }
    fun setVibration(v: Boolean)     = viewModelScope.launch { prefs.setVibrationEnabled(v) }
    fun setDynamicColor(v: Boolean)  = viewModelScope.launch { prefs.setDynamicColorEnabled(v) }
    fun setThemeMode(mode: com.gotify.client.data.datastore.ThemeMode) = viewModelScope.launch { prefs.setThemeMode(mode) }
    fun setAppLanguage(lang: com.gotify.client.data.datastore.AppLanguage) = viewModelScope.launch { prefs.setAppLanguage(lang) }
    fun setMarkdown(v: Boolean)      = viewModelScope.launch { prefs.setMarkdownEnabled(v) }
    fun setKeepAlive(v: Boolean)     = viewModelScope.launch { prefs.setKeepAliveEnabled(v) }
    fun setQuietHoursEnabled(v: Boolean) = viewModelScope.launch { prefs.setQuietHoursEnabled(v) }
    fun setQuietHours(start: Int, end: Int) = viewModelScope.launch { prefs.setQuietHours(start, end) }
    fun setAppLock(v: Boolean)       = viewModelScope.launch { prefs.setAppLockEnabled(v) }
    fun setServerIntents(v: Boolean) = viewModelScope.launch { prefs.setServerIntentsEnabled(v) }

    
    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            val servers = serverDao.getAllServers().first()
            val active = serverManager.activeServer.value


            if (active != null) {
                serverManager.apiClient?.let { api ->
                    val clientId = serverDao.getServerById(active.id)?.clientId ?: 0
                    if (clientId > 0) {
                        withTimeoutOrNull(5_000) { AuthRepository(api).logout(clientId) }
                    }
                }

                serverDao.deleteServer(active.id)
                messageDao.deleteAllMessages(active.id)
                applicationDao.deleteAllForServer(active.id)
                notifications.cancelServerNotifications(active.id)
            }

            // Remove inactive servers as well so a future startup cannot
            // silently reactivate a credential the user intended to remove.
            servers.filterNot { it.id == active?.id }.forEach { server ->
                serverDao.deleteServer(server.id)
                messageDao.deleteAllMessages(server.id)
                applicationDao.deleteAllForServer(server.id)
                notifications.cancelServerNotifications(server.id)
            }



            serverManager.reset()


            // Keep appearance and notification preferences; only the active
            // server selection belongs to the account session.
            prefs.setActiveServerId(-1L)

            onLoggedOut()
        }
    }
}



@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppInboxViewModel @Inject constructor(
    private val messageDao:     MessageDao,
    private val applicationDao: ApplicationDao,
    private val serverManager:  ServerManager,
    private val serverDao:      ServerDao,
    private val notifications:  GotifyNotificationManager,
    savedStateHandle:           SavedStateHandle,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val appId: Int = checkNotNull(savedStateHandle["appId"])
    private val pendingDeletes = PendingDeletes(viewModelScope) { id -> commitDelete(id) }
    private val requestedServerId: Long = savedStateHandle.get<Long>("serverId") ?: -1L
    private var resolvedServerId: Long = -1L

    private val _uiState = MutableStateFlow(AppInboxUiState())
    val uiState: StateFlow<AppInboxUiState> = _uiState.asStateFlow()

    private val _serverId = MutableStateFlow(-1L)
    val serverId: StateFlow<Long> = _serverId.asStateFlow()
    private val _serverBaseUrl = MutableStateFlow("")
    val serverBaseUrl: StateFlow<String> = _serverBaseUrl.asStateFlow()
    private val _clientToken = MutableStateFlow("")
    val clientToken: StateFlow<String> = _clientToken.asStateFlow()

    private val pageLimit = MutableStateFlow(50)
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            val requested = requestedServerId.takeIf { it > 0 }
            val serverId = if (requested != null) {
                requested.takeIf { serverDao.getServerById(it) != null }
            } else {
                serverManager.activeServer.value?.id
            } ?: run {
                _uiState.update { it.copy(isLoading = false, errorMessage = localizedErr(appContext, R.string.err_server_not_found)) }
                return@launch
            }
            resolvedServerId = serverId
            _serverId.value = serverId
            val server = serverDao.getServerById(serverId)?.toDomain()
                ?: serverManager.activeServer.value?.takeIf { it.id == serverId }
                ?: run {
                    _uiState.update { it.copy(isLoading = false, errorMessage = localizedErr(appContext, R.string.err_server_not_found)) }
                    return@launch
                }
            _serverBaseUrl.value = server.baseUrl
            _clientToken.value = server.clientToken
            val app = applicationDao.getApplicationById(serverId, appId)
            _uiState.update { it.copy(appName = app?.name ?: "App $appId", appImageUrl = app?.image) }

            pageLimit.flatMapLatest { limit ->
                messageDao.getMessagesByAppPaged(serverId, appId, limit)
                    .map { entities -> entities to (entities.size >= limit) }
            }.combine(pendingDeletes.ids) { (entities, hasMore), hidden ->
                entities.filterNot { it.id in hidden } to hasMore
            }.collect { (entities, hasMore) ->
                _uiState.update {
                    it.copy(
                        messages = entities.map { e -> e.toDomain() },
                        isLoading = false,
                        hasMorePages = hasMore
                    )
                }
            }
        }
    }

    fun deleteMessage(messageId: Long) = pendingDeletes.schedule(messageId)

    fun undoDelete(messageId: Long) = pendingDeletes.undo(messageId)

    private suspend fun commitDelete(messageId: Long) {
        val serverId = resolvedServerId.takeIf { it > 0 } ?: return
        val api = if (serverManager.activeServer.value?.id == serverId) {
            serverManager.apiClient
        } else {
            serverDao.getServerById(serverId)?.safeApiClient()
        }
        when (val result = api?.let { MessageRepository(it).deleteMessage(messageId) }) {
            is ApiResult.Success -> {
                messageDao.deleteMessage(serverId, messageId)
                notifications.cancelNotification(serverId, messageId)
            }
            is ApiResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
            null -> _uiState.update { it.copy(errorMessage = localizedErr(appContext, R.string.err_no_active_connection)) }
            ApiResult.Loading -> Unit
        }
    }

    fun clearAllMessages() {
        val serverId = resolvedServerId.takeIf { it > 0 } ?: return
        viewModelScope.launch {
            val api = if (serverManager.activeServer.value?.id == serverId) {
                serverManager.apiClient
            } else {
                serverDao.getServerById(serverId)?.safeApiClient()
            }
            when (val result = api?.let { MessageRepository(it).deleteMessagesByApp(appId) }) {
                is ApiResult.Success -> {
                    messageDao.deleteMessagesByApp(serverId, appId)
                    notifications.cancelServerNotifications(serverId, appId)
                }
                is ApiResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                null -> _uiState.update { it.copy(errorMessage = localizedErr(appContext, R.string.err_no_active_connection)) }
                ApiResult.Loading -> Unit
            }
        }
    }

    fun refresh() {
        val serverId = resolvedServerId.takeIf { it > 0 } ?: return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val api = if (serverManager.activeServer.value?.id == serverId) {
                serverManager.apiClient
            } else {
                serverDao.getServerById(serverId)?.safeApiClient()
            }
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            if (api == null) {
                _uiState.update { it.copy(isRefreshing = false, errorMessage = localizedErr(appContext, R.string.err_no_active_server)) }
                return@launch
            }
            var error: String? = null
            when (val result = MessageRepository(api).getAllMessagesByApp(appId)) {
                is ApiResult.Success -> {
                    val sync = result.data
                    val readIds = messageDao.getReadMessageIdsForApp(serverId, appId).toHashSet()
                    _uiState.update { it.copy(cacheSyncTruncated = !sync.complete) }
                    messageDao.insertMessages(sync.messages.map { message ->
                        message.toEntity(serverId, readIds.contains(message.id))
                    })
                    if (sync.complete) {
                        val remoteIds = sync.messages.map { it.id }
                        if (remoteIds.isEmpty()) messageDao.deleteMessagesByApp(serverId, appId)
                        else messageDao.deleteMissingForAppSafely(serverId, appId, remoteIds)
                    }
                }
                is ApiResult.Error -> error = result.message
                else -> Unit
            }
            _uiState.update { it.copy(isRefreshing = false, errorMessage = error) }
        }
    }

    fun loadMore() {
        pageLimit.update { it + 50 }
    }

}

data class AppInboxUiState(
    val messages:    List<GotifyMessage> = emptyList(),
    val appName:     String              = "",
    val appImageUrl: String?             = null,
    val isLoading:   Boolean             = true,
    val isRefreshing:Boolean             = false,
    val hasMorePages:Boolean             = false,
    val cacheSyncTruncated:Boolean       = false,
    val errorMessage:String?             = null
)



@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val messageDao:     MessageDao,
    private val applicationDao: ApplicationDao,
    private val serverManager:  ServerManager
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val clientToken: StateFlow<String> = serverManager.activeServer
        .map { it?.clientToken.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val serverBaseUrl: StateFlow<String> = serverManager.activeServer
        .map { it?.baseUrl.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _filters = MutableStateFlow(SearchFilters())
    val filters: StateFlow<SearchFilters> = _filters.asStateFlow()

    val searchResults: StateFlow<List<GotifyMessage>> = combine(
        _query.debounce(300),
        _filters,
        serverManager.activeServer.map { it?.id }.distinctUntilChanged()
    ) { q, f, serverId -> Triple(q.trim(), f, serverId) }
        .flatMapLatest { (q, f, serverId) ->
            if (serverId == null || (q.isEmpty() && !f.isActive)) return@flatMapLatest flowOf(emptyList())
            val range = f.priority?.range ?: (Int.MIN_VALUE..Int.MAX_VALUE)
            val since = f.dateRange.windowSeconds?.let { System.currentTimeMillis() / 1000 - it }
            messageDao.searchMessages(serverId, q.escapeLike(), f.appId, range.first, range.last, since)
                .map { it.map { e -> e.toDomain() } }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setPriorityFilter(p: PriorityFilter?) = _filters.update { it.copy(priority = if (it.priority == p) null else p) }
    fun setAppFilter(appId: Int?)             = _filters.update { it.copy(appId = appId) }
    fun setDateRange(r: DateRange)            = _filters.update { it.copy(dateRange = if (it.dateRange == r) DateRange.ANY else r) }

    val applications: StateFlow<Map<Int, GotifyApplication>> = serverManager.activeServer
        .flatMapLatest { server ->
            if (server == null) {
                flowOf(emptyMap())
            } else {
                applicationDao.getApplications(server.id).map { entities ->
                    entities.associate { entity ->
                        entity.id to entity.toDomain().resolvedFor(server)
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun setQuery(q: String) { _query.value = q }
    fun clearQuery()        { _query.value = "" }

    private fun String.escapeLike(): String = replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}



@HiltViewModel
class ServersViewModel @Inject constructor(
    private val serverManager:    ServerManager,
    private val serverDao:        ServerDao,
    private val prefs:            PreferencesRepository,
    private val webSocketManager: GotifyWebSocketManager,
    private val messageDao:       MessageDao,
    private val applicationDao:   ApplicationDao,
    private val notifications:    GotifyNotificationManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _serverInfo = MutableStateFlow(ServerInfoUiState())
    val serverInfo: StateFlow<ServerInfoUiState> = _serverInfo.asStateFlow()
    private var serverInfoJob: Job? = null

    val servers: StateFlow<List<GotifyServer>> = serverDao.getAllServers()
        .map { it.map { e -> e.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val connectionStatus: StateFlow<ConnectionStatus> = webSocketManager.connectionState
        .map { state ->
            when (state) {
                is StreamState.Connected  -> ConnectionStatus.CONNECTED
                is StreamState.Connecting -> ConnectionStatus.CONNECTING
                is StreamState.Error      -> ConnectionStatus.ERROR
                else                      -> ConnectionStatus.DISCONNECTED
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionStatus.DISCONNECTED)

    init {
        viewModelScope.launch {
            serverManager.activeServer
                .map { it?.id }
                .distinctUntilChanged()
                .collect {
                    _serverInfo.value = ServerInfoUiState()
                    refreshServerInfo()
                }
        }
    }

    fun refreshServerInfo() {
        serverInfoJob?.cancel()
        val serverId = serverManager.activeServer.value?.id
        val api = serverManager.apiClient ?: run {
            _serverInfo.value = ServerInfoUiState(errorMessage = localizedErr(appContext, R.string.err_no_active_connection))
            return
        }
        serverInfoJob = viewModelScope.launch {
            _serverInfo.update { it.copy(isLoading = true, errorMessage = null) }
            val repository = AuthRepository(api)
            val version = repository.getServerVersion()
            val health = repository.getServerHealth()
            if (!isActive || serverManager.activeServer.value?.id != serverId) return@launch
            val error = listOfNotNull(
                (version as? ApiResult.Error)?.message,
                (health as? ApiResult.Error)?.message
            ).firstOrNull()
            _serverInfo.value = ServerInfoUiState(
                version = (version as? ApiResult.Success)?.data?.version,
                health = (health as? ApiResult.Success)?.data?.health,
                database = (health as? ApiResult.Success)?.data?.database,
                isLoading = false,
                errorMessage = error
            )
        }
    }

    fun switchServer(serverId: Long) {
        viewModelScope.launch {
            val target = serverDao.getServerById(serverId) ?: return@launch
            if (!serverManager.addServer(target.toDomain().copy(isActive = true))) {
                _serverInfo.update { it.copy(errorMessage = localizedErr(appContext, R.string.err_invalid_server_url)) }
                return@launch
            }
            serverDao.switchActiveServer(serverId)
            prefs.setActiveServerId(serverId)
        }
    }

    fun removeServer(serverId: Long) {
        viewModelScope.launch {
            val wasActive = serverManager.activeServer.value?.id == serverId
            serverDao.deleteServer(serverId)
            messageDao.deleteAllMessages(serverId)
            applicationDao.deleteAllForServer(serverId)
            notifications.cancelServerNotifications(serverId)
            serverManager.removeServer(serverId)
            if (wasActive) {
                val next = serverDao.getAllServers().first().firstOrNull()
                if (next != null) switchServer(next.id) else prefs.setActiveServerId(-1L)
            }
        }
    }
}

enum class PriorityFilter(val label: String, val range: IntRange) {
    HIGH("High", 8..Int.MAX_VALUE),
    NORMAL("Normal", 4..7),
    LOW("Low", Int.MIN_VALUE..3)
}

enum class DateRange(val label: String, val windowSeconds: Long?) {
    ANY("Anytime", null),
    DAY("24 hours", 24 * 3600L),
    WEEK("7 days", 7 * 24 * 3600L)
}

data class SearchFilters(
    val priority: PriorityFilter? = null,
    val appId: Int? = null,
    val dateRange: DateRange = DateRange.ANY
) {
    val isActive: Boolean get() = priority != null || appId != null || dateRange != DateRange.ANY
}

data class ServerInfoUiState(
    val version: String? = null,
    val health: String? = null,
    val database: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

private fun GotifyApplication.resolvedFor(server: GotifyServer): GotifyApplication = copy(
    image = com.gotify.client.ui.apps.resolveAppImageUrl(server.baseUrl, image).orEmpty()
)
