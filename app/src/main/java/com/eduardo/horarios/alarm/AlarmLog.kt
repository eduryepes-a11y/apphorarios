package com.eduardo.horarios.alarm

import android.content.Context
import com.eduardo.horarios.data.localized
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Pequeño historial (últimas 30 líneas) de lo que ha pasado con los avisos, para diagnosticar. */
object AlarmLog {
    private const val PREFS = "alarm_log"
    private const val KEY = "lines"
    private const val MAX = 30

    @Synchronized
    fun add(context: Context, text: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val locale = context.localized().resources.configuration.locales[0] ?: Locale.getDefault()
        val fmt = DateTimeFormatter.ofPattern("EEE d HH:mm:ss", locale)
        val line = "${LocalDateTime.now().format(fmt)} · $text"
        val lines = (listOf(line) + read(context)).take(MAX)
        prefs.edit().putString(KEY, lines.joinToString("\n")).apply()
    }

    fun read(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "")
            .orEmpty()
            .split("\n")
            .filter { it.isNotBlank() }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
