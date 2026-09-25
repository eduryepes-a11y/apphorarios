package com.eduardo.horarios

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

const val MASK_WEEKDAYS = 0b0011111
const val MASK_WEEKEND = 0b1100000
const val MASK_ALL = 0b1111111

/** 0 = lunes … 6 = domingo */
fun todayIndex(): Int = LocalDate.now().dayOfWeek.value - 1

fun nowMinuteOfDay(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

/** Fecha del día [day] (0 = lunes) de la semana actual. */
fun dateOfThisWeek(day: Int): LocalDate = LocalDate.now().with(DayOfWeek.MONDAY).plusDays(day.toLong())

fun Int.hasDay(day: Int): Boolean = ((this shr day) and 1) == 1

fun Int.toggleDay(day: Int): Int = this xor (1 shl day)

fun hm(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

fun dayShort(context: Context, day: Int): String = context.resources.getStringArray(R.array.days_short)[day]

fun dayName(context: Context, day: Int): String = context.resources.getStringArray(R.array.days_long)[day]

/** Nombre del día dentro de una frase: «el lunes» en español, «on Monday» en inglés (con mayúscula). */
fun dayNameInSentence(context: Context, day: Int): String {
    val name = dayName(context, day)
    return if (context.resources.configuration.locales[0].language == "es") name.lowercase() else name
}

fun durationLabel(context: Context, minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> context.getString(R.string.dur_min, m)
        m == 0 -> context.getString(R.string.dur_h, h)
        else -> context.getString(R.string.dur_h_min, h, m)
    }
}

val REMINDER_OPTIONS = listOf(-1, 0, 5, 10, 15, 30, 60)

fun reminderChipLabel(context: Context, r: Int): String = when {
    r < 0 -> context.getString(R.string.reminder_none)
    r == 0 -> context.getString(R.string.reminder_at_start)
    r >= 60 && r % 60 == 0 -> context.getString(R.string.reminder_hours_before, r / 60)
    else -> context.getString(R.string.reminder_min_before, r)
}

fun reminderShort(context: Context, r: Int): String = when {
    r < 0 -> context.getString(R.string.reminder_none)
    r == 0 -> context.getString(R.string.reminder_short_start)
    r >= 60 && r % 60 == 0 -> "-${r / 60} h"
    else -> "-$r min"
}

fun daysSummary(context: Context, mask: Int): String = when (mask) {
    0 -> context.getString(R.string.days_none)
    MASK_ALL -> context.getString(R.string.days_all)
    MASK_WEEKDAYS -> context.getString(R.string.days_weekdays)
    MASK_WEEKEND -> context.getString(R.string.days_weekend)
    else -> (0..6).filter { mask.hasDay(it) }.joinToString(" · ") { dayShort(context, it) }
}

/** «Jueves, 3 de octubre» / «Thursday, October 3», en el idioma de [context]. */
fun longDate(context: Context, date: LocalDate): String {
    val locale = context.resources.configuration.locales[0] ?: java.util.Locale.getDefault()
    return date.format(java.time.format.DateTimeFormatter.ofPattern(context.getString(R.string.date_header_pattern), locale))
        .replaceFirstChar { it.titlecase(locale) }
}
