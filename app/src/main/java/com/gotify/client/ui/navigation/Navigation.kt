package com.gotify.client.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.gotify.client.R
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.gotify.client.ui.components.EmptyState
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.*
import androidx.navigation.compose.*
import com.gotify.client.ui.appinbox.AppInboxScreen
import com.gotify.client.ui.apps.ApplicationsScreen
import com.gotify.client.ui.detail.MessageDetailScreen
import com.gotify.client.ui.home.HomeScreen
import com.gotify.client.ui.search.SearchScreen
import com.gotify.client.ui.servers.ServerSwitcherSheet
import com.gotify.client.ui.settings.SettingsScreen
import com.gotify.client.ui.login.LoginScreen
import com.gotify.client.ui.viewmodel.*

object Routes {
    const val HOME      = "home"
    const val APPS      = "apps"
    const val SETTINGS  = "settings"
    const val DETAIL    = "detail/{messageId}?serverId={serverId}"
    const val APP_INBOX = "app_inbox/{appId}?serverId={serverId}"
    const val SEARCH    = "search"
    const val ADD_SERVER= "add_server"

    fun detail(messageId: Long, serverId: Long? = null) = "detail/$messageId?serverId=${serverId ?: -1L}"
    fun appInbox(appId: Int, serverId: Long? = null) =
        "app_inbox/$appId?serverId=${serverId ?: -1L}"
}

data class BottomNavDestination(
    val route:        String,
    val icon:         ImageVector,
    val selectedIcon: ImageVector,
    @StringRes val labelRes: Int
)

private val topLevelRoutes = setOf(Routes.HOME, Routes.APPS, Routes.SETTINGS)

private fun NavBackStackEntry.isTopLevel() = destination.route in topLevelRoutes

val bottomNavDestinations = listOf(
    BottomNavDestination(Routes.HOME,     Icons.Outlined.Inbox,    Icons.Filled.Inbox,    R.string.nav_messages),
    BottomNavDestination(Routes.APPS,     Icons.Outlined.Apps,     Icons.Filled.Apps,     R.string.nav_apps),
    BottomNavDestination(Routes.SETTINGS, Icons.Outlined.Settings, Icons.Filled.Settings, R.string.nav_settings)
)

@Composable
fun MainNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route
    val showBottomBar = currentRoute in bottomNavDestinations.map { it.route }
    var showServerSheet by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Row(Modifier.fillMaxSize()) {
            if (useNavigationRail) {
                GotifyNavigationRail(
                    navController = navController,
                    currentRoute = currentRoute
                )
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                // Each screen owns its own insets (top bars, lists); this scaffold only reserves the bottom bar.
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (!useNavigationRail) {
                        AnimatedVisibility(
                            visible = showBottomBar,
                            enter = slideInVertically(initialOffsetY = { it }),
                            exit = slideOutVertically(targetOffsetY = { it })
                        ) {
                            GotifyBottomBar(
                                navController = navController,
                                currentRoute = currentRoute
                            )
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController      = navController,
                    startDestination   = Routes.HOME,
                    // consumeWindowInsets: the bottom bar already covers the navigation-bar inset,
                    // so screens below must not pad for it a second time.
                    modifier           = Modifier
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding),
                    // Tabs fade through (Material guidance for peer destinations); drill-downs slide.
                    enterTransition    = {
                        if (initialState.isTopLevel() && targetState.isTopLevel()) fadeIn(tween(220, delayMillis = 60))
                        else fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 8 }
                    },
                    exitTransition     = {
                        if (initialState.isTopLevel() && targetState.isTopLevel()) fadeOut(tween(90))
                        else fadeOut(tween(180)) + slideOutHorizontally(tween(260)) { -it / 8 }
                    },
                    popEnterTransition = {
                        if (initialState.isTopLevel() && targetState.isTopLevel()) fadeIn(tween(220, delayMillis = 60))
                        else fadeIn(tween(220)) + slideInHorizontally(tween(260)) { -it / 8 }
                    },
                    popExitTransition  = {
                        if (initialState.isTopLevel() && targetState.isTopLevel()) fadeOut(tween(90))
                        else fadeOut(tween(180)) + slideOutHorizontally(tween(260)) { it / 8 }
                    }
                ) {

            composable(Routes.HOME) {
                val vm: HomeViewModel = hiltViewModel()
                val uiState     by vm.uiState.collectAsStateWithLifecycle()
                val connStatus  by vm.connectionStatus.collectAsStateWithLifecycle()
                val serverName  by vm.activeServerName.collectAsStateWithLifecycle()
                val clientToken by vm.clientToken.collectAsStateWithLifecycle()
                val serverBaseUrl by vm.serverBaseUrl.collectAsStateWithLifecycle()
                val filter      by vm.filter.collectAsStateWithLifecycle()

                HomeScreen(
                    messages            = uiState.messages,
                    applications        = uiState.applications,
                    clientToken         = clientToken,
                    serverBaseUrl       = serverBaseUrl,
                    connectionStatus    = connStatus,
                    activeServerName    = serverName,
                    unreadCount         = uiState.unreadCount,
                    cachedMessageCount  = uiState.cachedMessageCount,
                    cacheSyncTruncated  = uiState.cacheSyncTruncated,
                    isLoading           = uiState.isLoading,
                    isRefreshing        = uiState.isRefreshing,
                    hasMorePages        = uiState.hasMorePages,
                    errorMessage        = uiState.errorMessage,
                    selectedAppId       = filter.appId,
                    unreadOnly          = filter.unreadOnly,
                    onSelectApp         = vm::selectApp,
                    onToggleUnread      = vm::toggleUnreadOnly,
                    onRefresh           = vm::refresh,
                    onLoadMore          = vm::loadMore,
                    onMessageClick      = { navController.navigate(Routes.detail(it.id)) },
                    onDeleteMessage     = vm::deleteMessage,
                    onUndoDelete        = vm::undoDelete,
                    onDeleteAllMessages = vm::deleteAllMessages,
                    onMarkAllAsRead     = vm::markAllAsRead,
                    onOpenServers       = { showServerSheet = true },
                    onOpenSearch        = { navController.navigate(Routes.SEARCH) }
                )
            }

            composable(Routes.APPS) {
                val vm: AppsViewModel = hiltViewModel()
                val uiState      by vm.uiState.collectAsStateWithLifecycle()
                val serverBaseUrl by vm.serverBaseUrl.collectAsStateWithLifecycle()
                val clientToken  by vm.clientToken.collectAsStateWithLifecycle()
                val mutedUntil   by vm.mutedUntil.collectAsStateWithLifecycle()

                ApplicationsScreen(
                    applications        = uiState.applications,
                    messageCounts       = uiState.messageCounts,
                    mutedUntil          = mutedUntil,
                    onMuteApp           = vm::setMute,
                    serverBaseUrl       = serverBaseUrl,
                    clientToken         = clientToken,
                    isLoading           = uiState.isLoading,
                    isRefreshing        = uiState.isRefreshing,
                    errorMessage        = uiState.errorMessage,
                    onRefresh           = vm::refresh,
                    onAppClick          = { navController.navigate(Routes.appInbox(it.id)) },
                    onDeleteApp         = vm::deleteApplication,
                    onDeleteAppMessages = vm::deleteAppMessages,
                    onCreateApp         = vm::createApplication,
                    onEditApp           = vm::updateApplication
                )
            }

            composable(Routes.SETTINGS) {
                val vm: SettingsViewModel = hiltViewModel()
                val state by vm.settingsState.collectAsStateWithLifecycle()

                SettingsScreen(
                    state                 = state,
                    onToggleNotifications = vm::setNotifications,
                    onToggleVibration     = vm::setVibration,
                    onToggleDynamicColor  = vm::setDynamicColor,
                    onSetThemeMode        = vm::setThemeMode,
                    onSetAppLanguage      = vm::setAppLanguage,
                    onToggleMarkdown      = vm::setMarkdown,
                    onToggleKeepAlive     = vm::setKeepAlive,
                    onToggleQuietHours    = vm::setQuietHoursEnabled,
                    onSetQuietHours       = vm::setQuietHours,
                    onToggleAppLock       = { enabled ->
                        com.gotify.client.AppLock.unlocked = true // don't lock the user out mid-setting
                        vm.setAppLock(enabled)
                    },
                    onToggleServerIntents = vm::setServerIntents,
                    onLogout              = {
                        vm.logout {
                            navController.navigate(Routes.HOME) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(
                route     = Routes.DETAIL,
                arguments = listOf(
                    navArgument("messageId") { type = NavType.LongType },
                    navArgument("serverId") { type = NavType.LongType; defaultValue = -1L }
                )
            ) {
                val vm: MessageDetailViewModel = hiltViewModel()
                val message     by vm.message.collectAsStateWithLifecycle()
                val application by vm.application.collectAsStateWithLifecycle()
                val clientToken by vm.clientToken.collectAsStateWithLifecycle()
                val serverBaseUrl by vm.serverBaseUrl.collectAsStateWithLifecycle()
                val detailServerId by vm.serverId.collectAsStateWithLifecycle()
                val detailError by vm.errorMessage.collectAsStateWithLifecycle()
                val loaded by vm.loaded.collectAsStateWithLifecycle()
                val settingsVm: SettingsViewModel = hiltViewModel()
                val settings by settingsVm.settingsState.collectAsStateWithLifecycle()

                message?.let { msg ->
                    MessageDetailScreen(
                        message        = msg,
                        application    = application,
                        clientToken    = clientToken,
                        serverBaseUrl  = serverBaseUrl,
                        markdownEnabled= settings.markdownEnabled,
                        errorMessage   = detailError,
                        onBack         = { navController.popBackStack() },
                        onDelete       = { vm.deleteMessage { navController.popBackStack() } },
                        onOpenAppInbox = {
                            navController.navigate(Routes.appInbox(msg.appId, detailServerId))
                        }
                    )
                } ?: if (loaded) {
                    Box(
                        Modifier.fillMaxSize().systemBarsPadding(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        EmptyState(
                            icon     = Icons.Outlined.SearchOff,
                            title    = stringResource(R.string.not_found_title),
                            subtitle = stringResource(R.string.not_found_sub),
                            action   = {
                                FilledTonalButton(onClick = { navController.popBackStack() }) { Text(stringResource(R.string.action_go_back)) }
                            }
                        )
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            composable(
                route     = Routes.APP_INBOX,
                arguments = listOf(
                    navArgument("appId") { type = NavType.IntType },
                    navArgument("serverId") { type = NavType.LongType; defaultValue = -1L }
                )
            ) {
                val vm: AppInboxViewModel = hiltViewModel()
                val uiState by vm.uiState.collectAsStateWithLifecycle()
                val serverBaseUrl by vm.serverBaseUrl.collectAsStateWithLifecycle()
                val clientToken by vm.clientToken.collectAsStateWithLifecycle()
                val inboxServerId by vm.serverId.collectAsStateWithLifecycle()
                val resolvedIconUrl = uiState.appImageUrl?.let { path ->
                    com.gotify.client.ui.apps.resolveAppImageUrl(serverBaseUrl, path)
                }

                AppInboxScreen(
                    appName         = uiState.appName,
                    appImageUrl     = resolvedIconUrl,
                    clientToken     = clientToken,
                    serverBaseUrl   = serverBaseUrl,
                    messages        = uiState.messages,
                    isLoading       = uiState.isLoading,
                    isRefreshing    = uiState.isRefreshing,
                    hasMorePages    = uiState.hasMorePages,
                    cacheSyncTruncated = uiState.cacheSyncTruncated,
                    errorMessage    = uiState.errorMessage,
                    onRefresh       = vm::refresh,
                    onLoadMore      = vm::loadMore,
                    onBack          = { navController.popBackStack() },
                    onMessageClick  = {
                        navController.navigate(Routes.detail(it.id, inboxServerId))
                    },
                    onDeleteMessage = vm::deleteMessage,
                    onUndoDelete    = vm::undoDelete,
                    onClearAll      = vm::clearAllMessages
                )
            }

            composable(Routes.SEARCH) {
                val vm: SearchViewModel = hiltViewModel()
                val query   by vm.query.collectAsStateWithLifecycle()
                val results by vm.searchResults.collectAsStateWithLifecycle()
                val apps    by vm.applications.collectAsStateWithLifecycle()
                val clientToken by vm.clientToken.collectAsStateWithLifecycle()
                val serverBaseUrl by vm.serverBaseUrl.collectAsStateWithLifecycle()
                val filters by vm.filters.collectAsStateWithLifecycle()

                SearchScreen(
                    query          = query,
                    results        = results,
                    applications   = apps,
                    clientToken    = clientToken,
                    serverBaseUrl  = serverBaseUrl,
                    filters        = filters,
                    onPriorityFilter = vm::setPriorityFilter,
                    onAppFilter    = vm::setAppFilter,
                    onDateRange    = vm::setDateRange,
                    onQueryChange  = vm::setQuery,
                    onClearQuery   = vm::clearQuery,
                    onBack         = { navController.popBackStack() },
                    onMessageClick = { navController.navigate(Routes.detail(it.id)) }
                )
            }

            composable(Routes.ADD_SERVER) {
                val vm: LoginViewModel = hiltViewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(state.loginSuccess) {
                    if (state.loginSuccess) navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ADD_SERVER) { inclusive = true }
                    }
                }
                LoginScreen(
                    isLoading = state.isLoading,
                    errorMessage = state.errorMessage,
                    onLoginWithPassword = vm::loginWithPassword,
                    onLoginWithToken = vm::loginWithToken,
                    onBack = { navController.popBackStack() }
                )
            }
                }
            }
        }
    }

    if (showServerSheet) {
        val vm: ServersViewModel = hiltViewModel()
        val servers    by vm.servers.collectAsStateWithLifecycle()
        val connStatus by vm.connectionStatus.collectAsStateWithLifecycle()
        val serverInfo by vm.serverInfo.collectAsStateWithLifecycle()

        ServerSwitcherSheet(
            servers          = servers,
            connectionStatus = connStatus,
            serverInfo       = serverInfo,
            onSwitchServer   = vm::switchServer,
            onRemoveServer   = vm::removeServer,
            onRefreshInfo    = vm::refreshServerInfo,
            onAddServer      = {
                showServerSheet = false
                navController.navigate(Routes.ADD_SERVER)
            },
            onDismiss        = { showServerSheet = false }
        )
    }
}

@Composable
private fun GotifyBottomBar(
    navController: NavController,
    currentRoute:  String?,
    modifier:      Modifier = Modifier
) {
    NavigationBar(
        modifier       = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        bottomNavDestinations.forEach { dest ->
            val selected = currentRoute == dest.route
            NavigationBarItem(
                selected = selected,
                onClick  = {
                    if (!selected) {
                        navController.navigate(dest.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    }
                },
                icon  = {
                    Icon(
                        imageVector        = if (selected) dest.selectedIcon else dest.icon,
                        contentDescription = stringResource(dest.labelRes)
                    )
                },
                label = { Text(stringResource(dest.labelRes), style = MaterialTheme.typography.labelMedium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor    = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

@Composable
private fun GotifyNavigationRail(
    navController: NavController,
    currentRoute: String?
) {
    NavigationRail(
        modifier = Modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        header = {
            Image(
                painter = painterResource(R.drawable.main_logo),
                contentDescription = "Gotify+",
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 24.dp)
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.medium)
            )
        }
    ) {
        bottomNavDestinations.forEach { dest ->
            val selected = currentRoute == dest.route
            NavigationRailItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        navController.navigate(dest.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (selected) dest.selectedIcon else dest.icon,
                        contentDescription = stringResource(dest.labelRes)
                    )
                },
                label = { Text(stringResource(dest.labelRes), style = MaterialTheme.typography.labelMedium) },
                alwaysShowLabel = true,
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}
