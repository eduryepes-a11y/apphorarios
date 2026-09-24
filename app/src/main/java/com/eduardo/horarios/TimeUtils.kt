package com.eduardo.horarios

import java.time.LocalDate
import java.time.LocalTime

val DAY_SHORT = listOf("L", "M", "X", "J", "V", "S", "D")
val DAY_NAMES = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

const val MASK_WEEKDAYS = 0b0011111
const val MASK_WEEKEND = 0b1100000
const val MASK_ALL = 0b1111111

/** 0 = lunes … 6 = domingo */
fun todayIndex(): Int = LocalDate.now().dayOfWeek.value - 1

fun nowMinuteOfDay(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

fun Int.hasDay(day: Int): Boolean = ((this shr day) and 1) == 1

fun Int.toggleDay(day: Int): Int = this xor (1 shl day)

fun hm(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

fun durationLabel(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h h"
        else -> "$h h $m min"
    }
}

val REMINDER_OPTIONS = listOf(-1, 0, 5, 10, 15, 30, 60)

fun reminderChipLabel(r: Int): String = when {
    r < 0 -> "Sin aviso"
    r == 0 -> "Al empezar"
    r >= 60 && r % 60 == 0 -> "${r / 60} h antes"
    else -> "$r min antes"
}

fun reminderShort(r: Int): String = when {
    r < 0 -> "Sin aviso"
    r == 0 -> "Al inicio"
    r >= 60 && r % 60 == 0 -> "-${r / 60} h"
    else -> "-$r min"
}

fun daysSummary(mask: Int): String = when (mask) {
    0 -> "Ningún día"
    MASK_ALL -> "Todos los días"
    MASK_WEEKDAYS -> "Entre semana"
    MASK_WEEKEND -> "Fines de semana"
    else -> (0..6).filter { mask.hasDay(it) }.joinToString(" · ") { DAY_SHORT[it] }
}
