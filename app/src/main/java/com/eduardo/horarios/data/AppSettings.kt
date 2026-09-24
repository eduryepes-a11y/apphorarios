package com.eduardo.horarios.data

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Preferencias de apariencia e idioma. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        ThemeMode.entries.getOrElse(prefs.getInt(KEY_THEME, 0)) { ThemeMode.SYSTEM }
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _accent = MutableStateFlow(prefs.getInt(KEY_ACCENT, 0))
    val accent: StateFlow<Int> = _accent.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putInt(KEY_THEME, mode.ordinal).apply()
        _themeMode.value = mode
    }

    fun setAccent(index: Int) {
        prefs.edit().putInt(KEY_ACCENT, index).apply()
        _accent.value = index
    }

    /** true cuando ya se ha completado la bienvenida. */
    val onboarded: Boolean get() = prefs.getBoolean(KEY_ONBOARDED, false)

    fun setOnboarded() {
        prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
    }

    /** "" = idioma del sistema, "es", "en". */
    val language: String get() = prefs.getString(KEY_LANGUAGE, "") ?: ""

    fun setLanguage(tag: String) {
        prefs.edit().putString(KEY_LANGUAGE, tag).apply()
        AppCompatDelegate.setApplicationLocales(
            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
        )
    }

    companion object {
        const val PREFS = "settings"
        const val KEY_THEME = "theme_mode"
        const val KEY_ACCENT = "accent"
        const val KEY_LANGUAGE = "language"
        const val KEY_ONBOARDED = "onboarded"
        val LANGUAGES = listOf("", "es", "en")
    }
}

/**
 * Contexto con el idioma elegido en la app. Hace falta fuera de las pantallas
 * (notificaciones, widget), porque ahí Android usa el idioma del sistema.
 */
fun Context.localized(): Context {
    val tag = getSharedPreferences(SettingsStore.PREFS, Context.MODE_PRIVATE)
        .getString(SettingsStore.KEY_LANGUAGE, "").orEmpty()
    if (tag.isEmpty()) return this
    val config = Configuration(resources.configuration)
    config.setLocale(Locale.forLanguageTag(tag))
    return createConfigurationContext(config)
}
