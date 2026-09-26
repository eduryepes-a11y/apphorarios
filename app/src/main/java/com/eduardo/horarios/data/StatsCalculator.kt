package com.eduardo.horarios.data

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.floor
import kotlin.math.max

/** Horas de una actividad (o de varias con el mismo nombre) en una semana. */
data class HoursItem(val title: String, val emoji: String, val colorIndex: Int, val minutes: Int)

/** «Tu semana en horas»: total de esta semana, el de la anterior y el reparto. */
data class WeekHours(val totalMinutes: Int, val previousMinutes: Int, val items: List<HoursItem>) {
    val deltaMinutes: Int get() = totalMinutes - previousMinutes
}

/** «Lo que pasó» en el periodo: retrasos, actividades saltadas y días libres. */
data class WhatHappened(val delayDays: Int, val delayMinutes: Int, val skipped: Int, val daysOff: Int) {
    val averageDelay: Int get() = if (delayDays == 0) 0 else delayMinutes / delayDays
}

/** Un día del calendario de constancia: cuántos hábitos tocaban y cuántos se hicieron. */
data class DayCell(val date: LocalDate, val planned: Int, val done: Int, val future: Boolean) {
    val ratio: Float get() = if (planned == 0) 0f else done.toFloat() / planned
}

/** Un hábito (actividad que se sigue) con su progreso y su racha de semanas. */
data class HabitStat(
    val activity: ActivityEntity,
    val plannedPeriod: Int,
    val donePeriod: Int,
    val weekPlanned: Int,
    val weekDone: Int,
    val weeklyGoal: Int,
    val streakWeeks: Int,
)

data class StatsResult(
    val periodDays: Int,
    val planned: Int,
    val done: Int,
    val weekHours: WeekHours,
    val happened: WhatHappened,
    val heatmap: List<DayCell>,
    val habits: List<HabitStat>,
) {
    val percent: Int? get() = if (planned == 0) null else done * 100 / planned
}

/**
 * Cálculos de la pestaña Progreso y del resumen semanal. Sin Android: se prueba con JUnit.
 * - Horas: todas las actividades (no hace falta marcar nada).
 * - Constancia y hábitos: solo las actividades que se siguen ([ActivityEntity.tracked]).
 */
object StatsCalculator {

    const val HEATMAP_WEEKS = 12

    /** Veces que hay que hacerla en una semana para que cuente: el 80 %, con un mínimo de 1. */
    fun weeklyGoal(planned: Int): Int = when {
        planned <= 0 -> 0
        planned == 1 -> 1
        else -> max(1, floor(planned * 0.8).toInt())
    }

    private fun key(a: ActivityEntity) = a.title.trim().lowercase() to a.emoji

    /** Minutos del día sin las actividades saltadas (con los retrasos aplicados). */
    private fun dayMinutes(date: LocalDate, acts: List<ActivityEntity>, overrides: List<OverrideEntity>) =
        Planner.plan(date, acts, overrides).filter { !it.skipped }

    fun weekHours(monday: LocalDate, acts: List<ActivityEntity>, overrides: List<OverrideEntity>): WeekHours {
        fun week(start: LocalDate) = (0L..6L).flatMap { dayMinutes(start.plusDays(it), acts, overrides) }
        val current = week(monday)
        val previous = week(monday.minusWeeks(1))
        val items = current
            .groupBy { key(it.activity) }
            .values
            .map { group ->
                val a = group.first().activity
                HoursItem(a.title, a.emoji, a.colorIndex, group.sumOf { it.end - it.start })
            }
            .sortedByDescending { it.minutes }
        return WeekHours(
            totalMinutes = current.sumOf { it.end - it.start },
            previousMinutes = previous.sumOf { it.end - it.start },
            items = items,
        )
    }

    fun whatHappened(from: LocalDate, to: LocalDate, acts: List<ActivityEntity>, overrides: List<OverrideEntity>): WhatHappened {
        var delayDays = 0
        var delayMinutes = 0
        var skipped = 0
        var daysOff = 0
        var d = from
        while (!d.isAfter(to)) {
            val o = Planner.overridesFor(d.toEpochDay(), overrides)
            val plan = Planner.plan(d, acts, overrides)
            if (o.dayOff) {
                if (plan.isNotEmpty()) daysOff++
            } else {
                skipped += plan.count { it.skipped }
                if (o.totalShift > 0 && plan.isNotEmpty()) {
                    delayDays++
                    delayMinutes += o.totalShift
                }
            }
            d = d.plusDays(1)
        }
        return WhatHappened(delayDays, delayMinutes, skipped, daysOff)
    }

    fun compute(
        today: LocalDate,
        nowMinute: Int,
        allActs: List<ActivityEntity>,
        completions: List<CompletionEntity>,
        overrides: List<OverrideEntity>,
        periodDays: Int,
    ): StatsResult {
        val tracked = allActs.filter { it.tracked }
        val doneSet = completions.map { it.activityId to it.epochDay }.toHashSet()
        val monday = today.with(DayOfWeek.MONDAY)

        /** Hábitos que ya tocaban ese día (sin los saltados; hoy, solo los que ya han empezado). */
        fun startedOn(date: LocalDate): List<PlannedActivity> =
            Planner.plan(date, tracked, overrides).filter { !it.skipped && (date < today || it.start <= nowMinute) }

        /** Hábitos del día completo (para la semana en curso y las pasadas). */
        fun allOn(date: LocalDate): List<PlannedActivity> =
            Planner.plan(date, tracked, overrides).filter { !it.skipped }

        fun isDone(p: PlannedActivity) = (p.activity.id to p.epochDay) in doneSet

        // Periodo elegido (hasta hoy)
        var planned = 0
        var done = 0
        val perPlanned = HashMap<Pair<String, String>, Int>()
        val perDone = HashMap<Pair<String, String>, Int>()
        for (i in 0 until periodDays) {
            for (p in startedOn(today.minusDays(i.toLong()))) {
                val k = key(p.activity)
                planned++
                perPlanned[k] = (perPlanned[k] ?: 0) + 1
                if (isDone(p)) {
                    done++
                    perDone[k] = (perDone[k] ?: 0) + 1
                }
            }
        }

        // Calendario de constancia: las últimas semanas, de lunes a domingo
        val heatStart = monday.minusWeeks((HEATMAP_WEEKS - 1).toLong())
        val heatmap = (0 until HEATMAP_WEEKS * 7).map { i ->
            val date = heatStart.plusDays(i.toLong())
            if (date.isAfter(today)) {
                DayCell(date, 0, 0, future = true)
            } else {
                val pl = startedOn(date)
                DayCell(date, pl.size, pl.count { isDone(it) }, future = false)
            }
        }

        // Hábitos: progreso de esta semana y racha de semanas cumpliendo
        // Las actividades de un solo día (el dentista) no son hábitos: cuentan en la constancia, no aquí
        val habits = tracked
            .filter { it.onDate == null }
            .groupBy { key(it) }
            .map { (k, group) ->
                fun weekCounts(start: LocalDate): Pair<Int, Int> {
                    val occ = (0L..6L).flatMap { allOn(start.plusDays(it)) }.filter { key(it.activity) == k }
                    return occ.size to occ.count { isDone(it) }
                }
                val (wp, wd) = weekCounts(monday)
                var streak = 0
                var week = monday
                for (w in 0 until 52) {
                    val (p, d) = weekCounts(week)
                    if (p > 0) {
                        if (d >= weeklyGoal(p)) {
                            streak++
                        } else if (w > 0) {
                            break // la semana en curso aún no rompe la racha
                        }
                    }
                    week = week.minusWeeks(1)
                }
                HabitStat(
                    activity = group.first(),
                    plannedPeriod = perPlanned[k] ?: 0,
                    donePeriod = perDone[k] ?: 0,
                    weekPlanned = wp,
                    weekDone = wd,
                    weeklyGoal = weeklyGoal(wp),
                    streakWeeks = streak,
                )
            }
            .filter { it.weekPlanned > 0 || it.plannedPeriod > 0 }
            .sortedWith(compareByDescending<HabitStat> { it.streakWeeks }.thenByDescending { it.weekPlanned })

        return StatsResult(
            periodDays = periodDays,
            planned = planned,
            done = done,
            weekHours = weekHours(monday, allActs, overrides),
            happened = whatHappened(today.minusDays((periodDays - 1).toLong()), today, allActs, overrides),
            heatmap = heatmap,
            habits = habits,
        )
    }

    /**
     * CSV con lo que pasó cada día (una fila por actividad y día), del más antiguo al más reciente.
     * Columnas: fecha, actividad, inicio, fin, retraso_min, estado (hecha / saltada / pendiente), seguida.
     */
    fun csv(
        from: LocalDate,
        to: LocalDate,
        acts: List<ActivityEntity>,
        completions: List<CompletionEntity>,
        overrides: List<OverrideEntity>,
    ): String {
        val doneSet = completions.map { it.activityId to it.epochDay }.toHashSet()
        val sb = StringBuilder("date,activity,start,end,delay_min,status,tracked\n")
        var d = from
        while (!d.isAfter(to)) {
            for (p in Planner.plan(d, acts, overrides)) {
                val status = when {
                    p.skipped -> "skipped"
                    (p.activity.id to p.epochDay) in doneSet -> "done"
                    else -> "planned"
                }
                sb.append(d).append(',')
                    .append(csvField(p.activity.title)).append(',')
                    .append(hhmm(p.start)).append(',')
                    .append(hhmm(p.end)).append(',')
                    .append(p.shift).append(',')
                    .append(status).append(',')
                    .append(if (p.activity.tracked) "yes" else "no").append('\n')
            }
            d = d.plusDays(1)
        }
        return sb.toString()
    }

    private fun hhmm(m: Int) = "%02d:%02d".format(m / 60, m % 60)

    private fun csvField(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}
