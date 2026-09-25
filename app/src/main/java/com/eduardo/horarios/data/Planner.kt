package com.eduardo.horarios.data

import com.eduardo.horarios.hasDay
import java.time.LocalDate

/** Actividad de un solo día (no se repite cada semana). */
val ActivityEntity.isOneOff: Boolean get() = onDate != null

/** ¿Toca esta actividad en [date]? */
fun ActivityEntity.occursOn(date: LocalDate): Boolean =
    onDate?.let { it == date.toEpochDay() } ?: daysMask.hasDay(date.dayOfWeek.value - 1)

/** Días de la semana (0 = lunes) en los que puede tocar: el de su fecha si es de un solo día. */
fun ActivityEntity.weekDays(): List<Int> =
    onDate?.let { listOf(LocalDate.ofEpochDay(it).dayOfWeek.value - 1) } ?: (0..6).filter { daysMask.hasDay(it) }

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
