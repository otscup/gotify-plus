package com.gotify.client

import android.os.Bundle
import android.content.Intent
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import com.gotify.client.data.datastore.ThemeMode
import com.gotify.client.service.GotifyListenerService
import com.gotify.client.service.SyncJobService
import com.gotify.client.ui.login.LoginScreen
import com.gotify.client.ui.navigation.MainNavHost
import com.gotify.client.ui.navigation.Routes
import com.gotify.client.ui.theme.GotifyTheme
import com.gotify.client.ui.viewmodel.AppStartupViewModel
import com.gotify.client.ui.viewmodel.LoginUiState
import com.gotify.client.ui.viewmodel.LoginViewModel
import com.gotify.client.ui.viewmodel.SettingsViewModel
import com.gotify.client.util.currentLocalized
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(newBase.currentLocalized())
    }

    private val startupViewModel:  AppStartupViewModel  by viewModels()
    private val loginViewModel:    LoginViewModel        by viewModels()
    private val settingsViewModel: SettingsViewModel     by viewModels()
    private var pendingMessageId by mutableStateOf<Long?>(null)
    private var pendingServerId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingMessageId = messageIdFrom(intent)
        pendingServerId = serverIdFrom(intent)

        setContent {
            val appReady      by startupViewModel.ready.collectAsStateWithLifecycle()
            val settingsState by settingsViewModel.settingsState.collectAsStateWithLifecycle()
            val loginState    by loginViewModel.uiState.collectAsStateWithLifecycle()
            val notificationPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }

            LaunchedEffect(appReady, settingsState.serverUrl, settingsState.keepAliveEnabled) {
                if (!appReady) return@LaunchedEffect
                val loggedIn = settingsState.serverUrl.isNotBlank()
                if (loggedIn && settingsState.keepAliveEnabled) {
                    GotifyListenerService.start(this@MainActivity)
                    SyncJobService.cancel(this@MainActivity)
                } else {
                    GotifyListenerService.stop(this@MainActivity)
                    if (loggedIn) SyncJobService.schedule(this@MainActivity)
                    else SyncJobService.cancel(this@MainActivity)
                }
            }
            LaunchedEffect(settingsState.appLockEnabled) {
                // Keep message content out of the recents thumbnail while locked.
                if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(!settingsState.appLockEnabled)
            }
            LaunchedEffect(appReady, settingsState.notificationsEnabled, settingsState.serverUrl) {
                if (appReady && settingsState.serverUrl.isNotBlank() && settingsState.notificationsEnabled && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }

            GotifyTheme(
                darkTheme    = when (settingsState.themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT  -> false
                    ThemeMode.DARK   -> true
                },
                dynamicColor = settingsState.dynamicColorEnabled
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background
                ) {
                    if (!appReady) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        return@Surface
                    }

                    if (settingsState.appLockEnabled && !AppLock.unlocked) {
                        LockScreen(onUnlock = ::promptUnlock)
                        return@Surface
                    }

                    AppContent(
                        isLoggedIn        = settingsState.serverUrl.isNotBlank(),
                        deepLinkMessageId = pendingMessageId,
                        deepLinkServerId  = pendingServerId,
                        loginState        = loginState,
                        onLoginWithPassword = { url, user, pass, name ->
                            loginViewModel.loginWithPassword(url, user, pass, serverName = name)
                        },
                        onLoginWithToken = { url, token, name ->
                            loginViewModel.loginWithToken(url, token, serverName = name)
                        },
                        onDeepLinkConsumed = { pendingMessageId = null; pendingServerId = null },
                        onLoginSuccessConsumed = loginViewModel::clearLoginSuccess
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) AppLock.unlocked = false
    }

    private fun promptUnlock() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        // ponytail: no secure lock screen (or API < 30) means nothing to verify against; don't trap the user.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
            getSystemService(BiometricManager::class.java).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS
        ) {
            AppLock.unlocked = true
            return
        }
        BiometricPrompt.Builder(this)
            .setTitle(getString(R.string.lock_prompt_title))
            .setAllowedAuthenticators(authenticators)
            .build()
            .authenticate(CancellationSignal(), mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    AppLock.unlocked = true
                }
            })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingMessageId = messageIdFrom(intent)
        pendingServerId = serverIdFrom(intent)
    }

    private fun messageIdFrom(intent: Intent?): Long? =
        intent?.getLongExtra("message_id", -1L)?.takeIf { it >= 0 }
            ?: intent?.data?.takeIf { it.scheme == "gotifyclient" && it.host == "message" }
                ?.pathSegments?.firstOrNull()?.toLongOrNull()

    private fun serverIdFrom(intent: Intent?): Long? =
        intent?.getLongExtra("server_id", -1L)?.takeIf { it >= 0 }
}

/** Process-wide so rotation doesn't re-lock; cleared when the activity leaves the foreground. */
object AppLock {
    var unlocked by mutableStateOf(false)
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                Icons.Outlined.Lock, contentDescription = null,
                modifier = Modifier.padding(20.dp).size(36.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.lock_heading), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.lock_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onUnlock, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_unlock)) }
    }
}

@Composable
private fun AppContent(
    isLoggedIn:          Boolean,
    deepLinkMessageId:   Long?,
    deepLinkServerId:    Long?,
    loginState:          LoginUiState,
    onLoginWithPassword: (url: String, username: String, password: String, serverName: String) -> Unit,
    onLoginWithToken:    (url: String, token: String, serverName: String) -> Unit,
    onDeepLinkConsumed:  () -> Unit,
    onLoginSuccessConsumed: () -> Unit
) {
    val navController = rememberNavController()

    LaunchedEffect(deepLinkMessageId, deepLinkServerId, isLoggedIn, loginState.loginSuccess) {
        if (!isLoggedIn) return@LaunchedEffect

        if (deepLinkMessageId != null) {
            navController.navigate(Routes.detail(deepLinkMessageId, deepLinkServerId))
            onDeepLinkConsumed()
        } else if (loginState.loginSuccess) {
            navController.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } }
        }

        if (loginState.loginSuccess) onLoginSuccessConsumed()
    }

    if (!isLoggedIn) {
        LoginScreen(
            isLoading            = loginState.isLoading,
            errorMessage         = loginState.errorMessage,
            onLoginWithPassword  = onLoginWithPassword,
            onLoginWithToken     = onLoginWithToken
        )
    } else {
        MainNavHost(navController = navController)
    }
}
