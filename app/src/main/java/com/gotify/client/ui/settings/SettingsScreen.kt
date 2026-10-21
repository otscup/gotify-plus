package com.gotify.client.ui.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.net.toUri
import androidx.compose.ui.res.stringResource
import com.gotify.client.R
import com.gotify.client.data.datastore.AppLanguage
import com.gotify.client.data.datastore.ThemeMode
import com.gotify.client.ui.components.gotifyTopAppBarColors
import com.gotify.client.util.LocaleStore
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class SettingsState(
    val notificationsEnabled: Boolean   = true,
    val vibrationEnabled:     Boolean   = true,
    val dynamicColorEnabled:  Boolean   = false,
    val themeMode:            ThemeMode = ThemeMode.SYSTEM,
    val appLanguage:          AppLanguage = AppLanguage.SYSTEM,
    val markdownEnabled:      Boolean   = true,
    val keepAliveEnabled:     Boolean   = true,
    val quietHoursEnabled:    Boolean   = false,
    val quietStartMinutes:    Int       = 22 * 60,
    val quietEndMinutes:      Int       = 7 * 60,
    val appLockEnabled:       Boolean   = false,
    val serverIntentsEnabled: Boolean   = false,
    val serverName:           String    = "",
    val serverUrl:            String    = "",
    val appVersion:           String    = "1.0.0"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state:                  SettingsState,
    onToggleNotifications:  (Boolean) -> Unit,
    onToggleVibration:      (Boolean) -> Unit,
    onToggleDynamicColor:   (Boolean) -> Unit,
    onSetThemeMode:         (ThemeMode) -> Unit,
    onSetAppLanguage:       (AppLanguage) -> Unit,
    onToggleMarkdown:       (Boolean) -> Unit,
    onToggleKeepAlive:      (Boolean) -> Unit,
    onToggleQuietHours:     (Boolean) -> Unit,
    onSetQuietHours:        (start: Int, end: Int) -> Unit,
    onToggleAppLock:        (Boolean) -> Unit,
    onToggleServerIntents:  (Boolean) -> Unit,
    onLogout:               () -> Unit,
    modifier:               Modifier = Modifier
) {
    val context = LocalContext.current
    val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var editingQuietStart by remember { mutableStateOf<Boolean?>(null) } // true = start, false = end
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier       = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar         = {
            TopAppBar(
                title          = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                colors         = gotifyTopAppBarColors(),
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->

        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                SettingsSection(title = stringResource(R.string.section_notifications)) {
                    SwitchSettingsRow(
                        icon      = Icons.Outlined.Notifications,
                        title     = stringResource(R.string.section_notifications),
                        subtitle  = stringResource(R.string.set_notifications_sub),
                        checked   = state.notificationsEnabled,
                        onChecked = onToggleNotifications
                    )
                    SwitchSettingsRow(
                        icon      = Icons.Outlined.Vibration,
                        title     = stringResource(R.string.set_vibration),
                        subtitle  = stringResource(R.string.set_vibration_sub),
                        checked   = state.vibrationEnabled,
                        onChecked = onToggleVibration,
                        enabled   = state.notificationsEnabled
                    )
                    SwitchSettingsRow(
                        icon      = Icons.Outlined.Bedtime,
                        title     = stringResource(R.string.set_quiet_hours),
                        subtitle  = if (state.quietHoursEnabled && state.notificationsEnabled)
                            stringResource(R.string.quiet_sub_on, formatMinutes(state.quietStartMinutes, is24Hour), formatMinutes(state.quietEndMinutes, is24Hour))
                        else stringResource(R.string.quiet_sub_off),
                        checked   = state.quietHoursEnabled,
                        onChecked = onToggleQuietHours,
                        enabled   = state.notificationsEnabled
                    )
                    AnimatedVisibility(visible = state.quietHoursEnabled && state.notificationsEnabled) {
                        Column {
                            ClickableSettingsRow(
                                icon     = Icons.Outlined.Schedule,
                                title    = stringResource(R.string.quiet_starts),
                                value    = formatMinutes(state.quietStartMinutes, is24Hour),
                                onClick  = { editingQuietStart = true }
                            )
                            ClickableSettingsRow(
                                icon     = Icons.Outlined.WbSunny,
                                title    = stringResource(R.string.quiet_ends),
                                value    = formatMinutes(state.quietEndMinutes, is24Hour),
                                onClick  = { editingQuietStart = false }
                            )
                        }
                    }
                    ClickableSettingsRow(
                        icon     = Icons.Outlined.Tune,
                        title    = stringResource(R.string.set_channels),
                        subtitle = stringResource(R.string.set_channels_sub),
                        external = true,
                        onClick  = {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            )
                        }
                    )
                    ClickableSettingsRow(
                        icon     = Icons.Outlined.BatteryAlert,
                        title    = stringResource(R.string.set_battery),
                        subtitle = stringResource(R.string.set_battery_sub),
                        external = true,
                        onClick  = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }
                        }
                    )
                }

                SettingsSection(title = stringResource(R.string.section_appearance)) {
                    ThemeModeRow(selected = state.themeMode, onSelect = onSetThemeMode)
                    LanguageRow(selected = state.appLanguage, onSelect = onSetAppLanguage)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SwitchSettingsRow(
                            icon      = Icons.Outlined.Palette,
                            title     = stringResource(R.string.set_dynamic_color),
                            subtitle  = stringResource(R.string.set_dynamic_sub),
                            checked   = state.dynamicColorEnabled,
                            onChecked = onToggleDynamicColor
                        )
                    }
                    SwitchSettingsRow(
                        icon      = Icons.Outlined.TextFormat,
                        title     = stringResource(R.string.set_markdown),
                        subtitle  = stringResource(R.string.set_markdown_sub),
                        checked   = state.markdownEnabled,
                        onChecked = onToggleMarkdown
                    )
                }

                SettingsSection(title = stringResource(R.string.section_connection)) {
                    SwitchSettingsRow(
                        icon      = Icons.Outlined.Sync,
                        title     = stringResource(R.string.set_keep_alive),
                        subtitle  = if (state.keepAliveEnabled) stringResource(R.string.keep_alive_on)
                                    else stringResource(R.string.keep_alive_off),
                        checked   = state.keepAliveEnabled,
                        onChecked = onToggleKeepAlive
                    )
                }

                SettingsSection(title = stringResource(R.string.section_privacy)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        SwitchSettingsRow(
                            icon      = Icons.Outlined.Fingerprint,
                            title     = stringResource(R.string.set_app_lock),
                            subtitle  = stringResource(R.string.set_app_lock_sub),
                            checked   = state.appLockEnabled,
                            onChecked = onToggleAppLock
                        )
                    }
                    SwitchSettingsRow(
                        icon      = Icons.Outlined.Bolt,
                        title     = stringResource(R.string.set_server_actions),
                        subtitle  = stringResource(R.string.set_server_actions_sub),
                        checked   = state.serverIntentsEnabled,
                        onChecked = onToggleServerIntents
                    )
                }

                SettingsSection(title = stringResource(R.string.section_active_server)) {
                    InfoSettingsRow(
                        icon  = Icons.Outlined.Dns,
                        title = stringResource(R.string.info_nickname),
                        value = state.serverName.ifBlank { "—" }
                    )
                    InfoSettingsRow(
                        icon  = Icons.Outlined.Language,
                        title = "URL",
                        value = state.serverUrl.ifBlank { "—" }
                    )
                }

                SettingsSection(title = stringResource(R.string.section_about)) {
                    InfoSettingsRow(
                        icon  = Icons.Outlined.Info,
                        title = stringResource(R.string.info_version),
                        value = state.appVersion
                    )
                    ClickableSettingsRow(
                        icon     = Icons.Outlined.Code,
                        title    = stringResource(R.string.set_source_code),
                        subtitle = stringResource(R.string.set_source_sub),
                        external = true,
                        onClick  = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, "https://github.com/anand34577/gotify-plus".toUri())
                                )
                            }
                        }
                    )
                }

                // Destructive action last, visually separated from regular settings.
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick  = { showLogoutDialog = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape    = MaterialTheme.shapes.large,
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border   = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    )
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.sign_out))
                }
                Text(
                    stringResource(R.string.sign_out_note),
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }

    editingQuietStart?.let { isStart ->
        val initial = if (isStart) state.quietStartMinutes else state.quietEndMinutes
        val pickerState = rememberTimePickerState(
            initialHour   = initial / 60,
            initialMinute = initial % 60,
            is24Hour      = is24Hour
        )
        AlertDialog(
            onDismissRequest = { editingQuietStart = null },
            title = { Text(stringResource(if (isStart) R.string.quiet_start_title else R.string.quiet_end_title)) },
            text  = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val picked = pickerState.hour * 60 + pickerState.minute
                    if (isStart) onSetQuietHours(picked, state.quietEndMinutes)
                    else onSetQuietHours(state.quietStartMinutes, picked)
                    editingQuietStart = null
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { editingQuietStart = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon    = { Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error) },
            title   = { Text(stringResource(R.string.dialog_sign_out_title)) },
            text    = { Text(stringResource(R.string.dialog_sign_out_body)) },
            confirmButton = {
                Button(
                    onClick = { onLogout(); showLogoutDialog = false },
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor   = MaterialTheme.colorScheme.onError
                    )
                ) { Text(stringResource(R.string.sign_out)) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

private fun formatMinutes(minutes: Int, is24Hour: Boolean): String =
    LocalTime.of(minutes / 60, minutes % 60)
        .format(DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a"))

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text     = title,
            style    = MaterialTheme.typography.labelLarge,
            color    = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 8.dp)
                .semantics { heading() }
        )
        Surface(
            shape    = MaterialTheme.shapes.extraLarge,
            color    = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun ThemeModeRow(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val options = listOf(
        Triple(ThemeMode.SYSTEM, stringResource(R.string.theme_system), Icons.Outlined.BrightnessAuto),
        Triple(ThemeMode.LIGHT,  stringResource(R.string.theme_light),  Icons.Outlined.LightMode),
        Triple(ThemeMode.DARK,   stringResource(R.string.theme_dark),   Icons.Outlined.DarkMode)
    )
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsIcon(Icons.Outlined.Contrast, enabled = true)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.theme_title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    stringResource(R.string.theme_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (mode, label, icon) ->
                SegmentedButton(
                    selected = selected == mode,
                    onClick  = { onSelect(mode) },
                    shape    = SegmentedButtonDefaults.itemShape(index, options.size),
                    icon     = {
                        SegmentedButtonDefaults.Icon(active = selected == mode) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(SegmentedButtonDefaults.IconSize))
                        }
                    },
                    label    = { Text(label, maxLines = 1) }
                )
            }
        }
    }
}

@Composable
private fun LanguageRow(selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    val context = LocalContext.current
    val options = listOf(
        Triple(AppLanguage.SYSTEM, stringResource(R.string.theme_system), Icons.Outlined.BrightnessAuto),
        Triple(AppLanguage.EN,     "English", Icons.Outlined.Language),
        Triple(AppLanguage.ZH,     "中文",    Icons.Outlined.Translate)
    )
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsIcon(Icons.Outlined.Language, enabled = true)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.language_title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    stringResource(R.string.language_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (mode, label, icon) ->
                SegmentedButton(
                    selected = selected == mode,
                    onClick  = {
                        if (mode != selected) {
                            // Write the sync mirror first so the recreated activity picks the new locale up.
                            LocaleStore.write(context, mode)
                            onSelect(mode)
                            (context as? android.app.Activity)?.recreate()
                        }
                    },
                    shape    = SegmentedButtonDefaults.itemShape(index, options.size),
                    icon     = {
                        SegmentedButtonDefaults.Icon(active = selected == mode) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(SegmentedButtonDefaults.IconSize))
                        }
                    },
                    label    = { Text(label, maxLines = 1) }
                )
            }
        }
    }
}

@Composable
private fun SettingsIcon(icon: ImageVector, enabled: Boolean, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Icon(
        icon, null,
        tint     = if (enabled) tint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        modifier = Modifier.size(24.dp)
    )
}

@Composable
private fun SwitchSettingsRow(
    icon:      ImageVector,
    title:     String,
    subtitle:  String,
    checked:   Boolean,
    onChecked: (Boolean) -> Unit,
    enabled:   Boolean = true
) {
    val disabled = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    // One toggleable target for the whole row; the Switch is decorative so TalkBack announces a single control.
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChecked)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsIcon(icon, enabled)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color      = if (enabled) MaterialTheme.colorScheme.onSurface else disabled
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else disabled
            )
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun ClickableSettingsRow(
    icon:     ImageVector,
    title:    String,
    onClick:  () -> Unit,
    subtitle: String? = null,
    value:    String? = null,
    external: Boolean = false
) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsIcon(icon, enabled = true)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (value != null) {
                Text(
                    value,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Icon(
                if (external) Icons.AutoMirrored.Outlined.OpenInNew else Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun InfoSettingsRow(icon: ImageVector, title: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsIcon(icon, enabled = true)
        Text(
            title,
            style      = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            value,
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines  = 1,
            overflow  = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier  = Modifier.weight(1f)
        )
    }
}
