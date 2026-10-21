package com.gotify.client.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.gotify.client.util.LocaleStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton


private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gotify_prefs")


data class UserPreferences(
    val notificationsEnabled: Boolean = true,
    val vibrationEnabled: Boolean     = true,
    val themeMode: ThemeMode          = ThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean  = false,
    val markdownEnabled: Boolean      = true,
    val keepAliveEnabled: Boolean     = true,
    val activeServerId: Long          = -1L,
    val lastSyncTimestamp: Long       = 0L,
    val quietHoursEnabled: Boolean    = false,
    val quietStartMinutes: Int        = 22 * 60,
    val quietEndMinutes: Int          = 7 * 60,
    val appLockEnabled: Boolean       = false,
    val serverIntentsEnabled: Boolean = false,
    val appLanguage: AppLanguage      = AppLanguage.SYSTEM,
    // "serverId:appId" -> muted-until epoch millis (Long.MAX_VALUE = muted indefinitely)
    val appMutes: Map<String, Long>   = emptyMap()
) {
    fun isAppMuted(serverId: Long, appId: Int, now: Long = System.currentTimeMillis()): Boolean =
        (appMutes[muteKey(serverId, appId)] ?: 0L) > now
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AppLanguage { SYSTEM, EN, ZH }

fun muteKey(serverId: Long, appId: Int) = "$serverId:$appId"

/** Quiet window may wrap past midnight (e.g. 22:00 → 07:00). start == end means disabled. */
fun isInQuietHours(nowMinutes: Int, startMinutes: Int, endMinutes: Int): Boolean = when {
    startMinutes == endMinutes -> false
    startMinutes < endMinutes  -> nowMinutes in startMinutes until endMinutes
    else                       -> nowMinutes >= startMinutes || nowMinutes < endMinutes
}

@Singleton
class PreferencesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {


    private object Keys {
        val NOTIFICATIONS_ENABLED  = booleanPreferencesKey("notifications_enabled")
        val VIBRATION_ENABLED      = booleanPreferencesKey("vibration_enabled")
        // Legacy on/off switch; only read as a fallback when THEME_MODE was never written.
        val DARK_THEME_ENABLED     = booleanPreferencesKey("dark_theme_enabled")
        val THEME_MODE             = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR_ENABLED  = booleanPreferencesKey("dynamic_color_enabled")
        val MARKDOWN_ENABLED       = booleanPreferencesKey("markdown_enabled")
        val KEEP_ALIVE_ENABLED     = booleanPreferencesKey("keep_alive_enabled")
        val ACTIVE_SERVER_ID       = longPreferencesKey("active_server_id")
        val LAST_SYNC_TIMESTAMP    = longPreferencesKey("last_sync_timestamp")
        val QUIET_HOURS_ENABLED    = booleanPreferencesKey("quiet_hours_enabled")
        val QUIET_START            = intPreferencesKey("quiet_start_minutes")
        val QUIET_END              = intPreferencesKey("quiet_end_minutes")
        val APP_LOCK_ENABLED       = booleanPreferencesKey("app_lock_enabled")
        val APP_LANGUAGE           = stringPreferencesKey("app_language")
        val SERVER_INTENTS_ENABLED = booleanPreferencesKey("server_intents_enabled")
        val APP_MUTES              = stringSetPreferencesKey("app_mutes")
    }



    val userPreferences: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { prefs ->
            UserPreferences(
                notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
                vibrationEnabled     = prefs[Keys.VIBRATION_ENABLED]     ?: true,
                themeMode            = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                    ?: prefs[Keys.DARK_THEME_ENABLED]?.let { if (it) ThemeMode.DARK else ThemeMode.LIGHT }
                    ?: ThemeMode.SYSTEM,
                dynamicColorEnabled  = prefs[Keys.DYNAMIC_COLOR_ENABLED] ?: false,
                markdownEnabled      = prefs[Keys.MARKDOWN_ENABLED]      ?: true,
                keepAliveEnabled     = prefs[Keys.KEEP_ALIVE_ENABLED]    ?: true,
                activeServerId       = prefs[Keys.ACTIVE_SERVER_ID]      ?: -1L,
                lastSyncTimestamp    = prefs[Keys.LAST_SYNC_TIMESTAMP]   ?: 0L,
                quietHoursEnabled    = prefs[Keys.QUIET_HOURS_ENABLED]   ?: false,
                quietStartMinutes    = prefs[Keys.QUIET_START]           ?: (22 * 60),
                quietEndMinutes      = prefs[Keys.QUIET_END]             ?: (7 * 60),
                appLockEnabled       = prefs[Keys.APP_LOCK_ENABLED]      ?: false,
                appLanguage          = prefs[Keys.APP_LANGUAGE]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                    ?: AppLanguage.SYSTEM,
                serverIntentsEnabled = prefs[Keys.SERVER_INTENTS_ENABLED] ?: false,
                appMutes             = parseMutes(prefs[Keys.APP_MUTES])
            )
        }

    private fun parseMutes(raw: Set<String>?): Map<String, Long> =
        raw.orEmpty().mapNotNull { entry ->
            val key = entry.substringBeforeLast(':', "")
            val until = entry.substringAfterLast(':').toLongOrNull()
            if (key.isEmpty() || until == null) null else key to until
        }.toMap()

    /** untilMillis = null unmutes; Long.MAX_VALUE mutes until turned off. Expired entries are dropped. */
    suspend fun setAppMute(serverId: Long, appId: Int, untilMillis: Long?) =
        context.dataStore.edit { prefs ->
            val now = System.currentTimeMillis()
            val mutes = parseMutes(prefs[Keys.APP_MUTES]).filterValues { it > now }.toMutableMap()
            if (untilMillis == null) mutes.remove(muteKey(serverId, appId))
            else mutes[muteKey(serverId, appId)] = untilMillis
            prefs[Keys.APP_MUTES] = mutes.map { (k, v) -> "$k:$v" }.toSet()
        }

    suspend fun setQuietHoursEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.QUIET_HOURS_ENABLED] = enabled }

    suspend fun setQuietHours(startMinutes: Int, endMinutes: Int) =
        context.dataStore.edit {
            it[Keys.QUIET_START] = startMinutes
            it[Keys.QUIET_END] = endMinutes
        }

    suspend fun setAppLockEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.APP_LOCK_ENABLED] = enabled }

    suspend fun setAppLanguage(language: AppLanguage) {
        // Mirror synchronously so attachBaseContext / services can read it without a coroutine.
        LocaleStore.write(context, language)
        context.dataStore.edit { it[Keys.APP_LANGUAGE] = language.name }
    }

    suspend fun setServerIntentsEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.SERVER_INTENTS_ENABLED] = enabled }



    suspend fun setNotificationsEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }

    suspend fun setVibrationEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.VIBRATION_ENABLED] = enabled }

    suspend fun setThemeMode(mode: ThemeMode) =
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }

    suspend fun setDynamicColorEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR_ENABLED] = enabled }

    suspend fun setMarkdownEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.MARKDOWN_ENABLED] = enabled }

    suspend fun setKeepAliveEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.KEEP_ALIVE_ENABLED] = enabled }

    suspend fun setActiveServerId(id: Long) =
        context.dataStore.edit { it[Keys.ACTIVE_SERVER_ID] = id }

    suspend fun setLastSyncTimestamp(ts: Long) =
        context.dataStore.edit { it[Keys.LAST_SYNC_TIMESTAMP] = ts }

    
    suspend fun clearAll() = context.dataStore.edit { it.clear() }
}
