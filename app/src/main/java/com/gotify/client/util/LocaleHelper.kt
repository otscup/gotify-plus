package com.gotify.client.util

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import com.gotify.client.data.datastore.AppLanguage
import java.util.Locale

/**
 * Synchronous mirror of the language preference (SharedPreferences), so that
 * attachBaseContext and background services can read it without coroutines.
 * The DataStore preference in PreferencesRepository remains the source of
 * truth for the UI; this mirror is written alongside it in setAppLanguage().
 */
object LocaleStore {
    private const val PREFS = "gotify_locale"
    private const val KEY = "app_language"

    fun read(context: Context): AppLanguage {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
        return raw?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
            ?: AppLanguage.SYSTEM
    }

    fun write(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, language.name).apply()
    }
}

fun AppLanguage.toLocale(): Locale? = when (this) {
    AppLanguage.SYSTEM -> null
    AppLanguage.EN -> Locale.ENGLISH
    AppLanguage.ZH -> Locale.SIMPLIFIED_CHINESE
}

/** A context whose resources resolve in [language]; SYSTEM returns this context unchanged. */
fun Context.localizedFor(language: AppLanguage): Context {
    val locale = language.toLocale() ?: return this
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    config.setLocales(LocaleList(locale))
    return createConfigurationContext(config)
}

/** This context re-wrapped in the user's stored language choice. */
fun Context.currentLocalized(): Context = localizedFor(LocaleStore.read(this))
