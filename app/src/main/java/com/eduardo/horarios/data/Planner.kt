package com.eduardo.horarios.data

import com.eduardo.horarios.hasDay
import java.time.LocalDate

/** Actividad de un solo día (no se repite cada semana). */
val ActivityEntity.isOneOff: Boolean get() = onDate != null

/**
 * Semanas alternas (A/B, A/B/C, A/B/C/D). Las semanas van de lunes a domingo y se numeran desde
 * el lunes 5 de enero de 1970 (epochDay 4), que es semana A en todos los ciclos. Así la letra de
 * cada semana nunca cambia.
 */
object WeekParity {
    const val EVERY = 0
    const val A = 1
    const val B = 2
    val CYCLES = 2..4

    fun weekIndex(date: LocalDate): Long = Math.floorDiv(date.toEpochDay() - 4, 7L)

    /** Semana del ciclo de [cycle] semanas en la que cae [date]: 1 = A, 2 = B… */
    fun of(date: LocalDate, cycle: Int = 2): Int = Math.floorMod(weekIndex(date), cycle.coerceIn(CYCLES).toLong()).toInt() + 1

    /** «A», «B», «C», «D». */
    fun letter(parity: Int): String = ('A' + (parity - 1).coerceIn(0, 25)).toString()
}

/** Turnos por días: ¿toca [date] en un patrón de [on] días sí y [off] días no que empieza en [start]? */
object Rotation {
    val DAYS = 1..14

    fun occurs(start: Long, on: Int, off: Int, date: LocalDate): Boolean {
        if (on <= 0) return false
        val length = on + off.coerceAtLeast(0)
        return Math.floorMod(date.toEpochDay() - start, length.toLong()) < on
    }
}

/** Actividad por turnos de días (no depende de los días de la semana). */
val ActivityEntity.isRotation: Boolean get() = onDate == null && rotStart != null && rotOn > 0

/** ¿Toca esta actividad en [date]? */
fun ActivityEntity.occursOn(date: LocalDate): Boolean {
    onDate?.let { return it == date.toEpochDay() }
    if (isRotation) return Rotation.occurs(rotStart!!, rotOn, rotOff, date)
    return daysMask.hasDay(date.dayOfWeek.value - 1) &&
        (weekParity == WeekParity.EVERY || weekParity == WeekParity.of(date, weekCycle))
}

/** Días de la semana (0 = lunes) en los que puede tocar: el de su fecha si es de un solo día. */
fun ActivityEntity.weekDays(): List<Int> = when {
    onDate != null -> listOf(LocalDate.ofEpochDay(onDate).dayOfWeek.value - 1)
    isRotation -> (0..6).toList() // por turnos puede caer cualquier día
    else -> (0..6).filter { daysMask.hasDay(it) }
}

/** Una actividad tal y como queda un día concreto, con excepciones y retrasos aplicados. */
data class PlannedActivity(
    val activity: ActivityEntity,
    val date: LocalDate,
    val start: Int,
    val end: Int,
    val skipped: Boolean,
    val shift: Int,
) {
    val epochDay: Long get() = date.toEpochDay()
}

/** Resumen de los cambios puntuales de un día. */
data class DayOverrides(
    val dayOff: Boolean = false,
    val skipped: Set<Long> = emptySet(),
    /** (a partir de qué minuto, cuántos minutos), en el orden en que se hicieron. */
    val shifts: List<Pair<Int, Int>> = emptyList(),
) {
    val totalShift: Int get() = shifts.sumOf { it.second }
}

object Planner {

    fun overridesFor(epochDay: Long, all: List<OverrideEntity>): DayOverrides {
        val day = all.filter { it.epochDay == epochDay }
        if (day.isEmpty()) return DayOverrides()
        return DayOverrides(
            dayOff = day.any { it.type == OverrideEntity.TYPE_SKIP && it.activityId == null },
            skipped = day.filter { it.type == OverrideEntity.TYPE_SKIP }.mapNotNull { it.activityId }.toSet(),
            shifts = day.filter { it.type == OverrideEntity.TYPE_SHIFT }.sortedBy { it.id }.map { it.fromMinute to it.minutes },
        )
    }

    /** Minuto de inicio tras aplicar los retrasos del día, en orden. */
    fun shiftedStart(start: Int, o: DayOverrides): Int {
        var s = start
        for ((from, minutes) in o.shifts) if (s >= from) s += minutes
        return s
    }

    fun isSkipped(activityId: Long, o: DayOverrides): Boolean = o.dayOff || activityId in o.skipped

    /** Actividades de [date], ordenadas por hora, con excepciones y retrasos aplicados. */
    fun plan(date: LocalDate, activities: List<ActivityEntity>, overrides: List<OverrideEntity>): List<PlannedActivity> {
        val o = overridesFor(date.toEpochDay(), overrides)
        return activities
            .filter { it.occursOn(date) }
            .map { a ->
                val start = shiftedStart(a.startMinute, o).coerceAtMost(24 * 60 - 1)
                val shift = start - a.startMinute
                val end = (a.endMinute + shift).coerceIn(start + 1, 24 * 60)
                PlannedActivity(
                    activity = a,
                    date = date,
                    start = start,
                    end = end,
                    skipped = isSkipped(a.id, o),
                    shift = shift,
                )
            }
            .sortedBy { it.start }
    }
}
