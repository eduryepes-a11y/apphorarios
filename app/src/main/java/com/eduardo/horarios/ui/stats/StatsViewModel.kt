package com.eduardo.horarios.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eduardo.horarios.HorariosApp
import com.eduardo.horarios.data.ActivityEntity
import com.eduardo.horarios.data.CompletionEntity
import com.eduardo.horarios.data.HorariosRepository
import com.eduardo.horarios.data.OverrideEntity
import com.eduardo.horarios.data.Planner
import com.eduardo.horarios.data.ScheduleEntity
import com.eduardo.horarios.hasDay
import com.eduardo.horarios.nowMinuteOfDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate

data class ActivityStat(
    val activity: ActivityEntity,
    val planned: Int,
    val done: Int,
    val weeklyMinutes: Int,
)

data class DayStat(
    val day: Int,
    val planned: Int,
    val done: Int,
    val isToday: Boolean,
    val isFuture: Boolean,
)

data class StatsState(
    val loading: Boolean = true,
    val schedule: ScheduleEntity? = null,
    val periodDays: Int = 7,
    val planned: Int = 0,
    val done: Int = 0,
    val streak: Int = 0,
    val weeklyMinutes: Int = 0,
    val week: List<DayStat> = emptyList(),
    val perActivity: List<ActivityStat> = emptyList(),
    val anyCompletion: Boolean = false,
) {
    val percent: Int? get() = if (planned == 0) null else (done * 100 / planned)
}

class StatsViewModel(repo: HorariosRepository) : ViewModel() {

    val period = MutableStateFlow(7)

    val state: StateFlow<StatsState> = combine(
        repo.activeSchedule,
        repo.activeActivities,
        repo.completionsSince(LocalDate.now().minusDays(400).toEpochDay()),
        repo.overridesBetween(LocalDate.now().minusDays(400).toEpochDay(), LocalDate.now().plusDays(7).toEpochDay()),
        period,
    ) { schedule, activities, completions, overrides, periodDays ->
        compute(schedule, activities, completions, overrides, periodDays)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsState())

    private fun compute(
        schedule: ScheduleEntity?,
        acts: List<ActivityEntity>,
        completions: List<CompletionEntity>,
        overrides: List<OverrideEntity>,
        periodDays: Int,
    ): StatsState {
        val today = LocalDate.now()
        val nowMin = nowMinuteOfDay()
        val doneSet = completions.map { it.activityId to it.epochDay }.toHashSet()

        /** Actividades que ya tocaban ese día (sin las canceladas; hoy, solo las que ya han empezado). */
        fun plannedOn(date: LocalDate): List<ActivityEntity> =
            Planner.plan(date, acts, overrides)
                .filter { !it.skipped && (date < today || it.start <= nowMin) }
                .map { it.activity }

        // Periodo elegido (7 o 28 días hasta hoy)
        var planned = 0
        var done = 0
        val perPlanned = HashMap<Long, Int>()
        val perDone = HashMap<Long, Int>()
        for (i in 0 until periodDays) {
            val date = today.minusDays(i.toLong())
            val epoch = date.toEpochDay()
            for (a in plannedOn(date)) {
                planned++
                perPlanned[a.id] = (perPlanned[a.id] ?: 0) + 1
                if ((a.id to epoch) in doneSet) {
                    done++
                    perDone[a.id] = (perDone[a.id] ?: 0) + 1
                }
            }
        }

        // Semana actual, de lunes a domingo
        val monday = today.with(DayOfWeek.MONDAY)
        val week = (0..6).map { d ->
            val date = monday.plusDays(d.toLong())
            if (date > today) {
                DayStat(d, Planner.plan(date, acts, overrides).count { !it.skipped }, 0, isToday = false, isFuture = true)
            } else {
                val pl = plannedOn(date)
                DayStat(
                    day = d,
                    planned = pl.size,
                    done = pl.count { (it.id to date.toEpochDay()) in doneSet },
                    isToday = date == today,
                    isFuture = false,
                )
            }
        }

        // Racha: días seguidos (hacia atrás) con todo hecho. Hoy no rompe la racha si aún no está completo.
        var streak = 0
        if (acts.isNotEmpty()) {
            var date = today
            for (i in 0 until 400) {
                val pl = plannedOn(date)
                if (pl.isNotEmpty()) {
                    val allDone = pl.all { (it.id to date.toEpochDay()) in doneSet }
                    if (allDone) streak++ else if (date != today) break
                }
                date = date.minusDays(1)
            }
        }

        val perActivity = acts.map { a ->
            ActivityStat(
                activity = a,
                planned = perPlanned[a.id] ?: 0,
                done = perDone[a.id] ?: 0,
                weeklyMinutes = (a.endMinute - a.startMinute) * Integer.bitCount(a.daysMask),
            )
        }.sortedByDescending { it.weeklyMinutes }

        return StatsState(
            loading = false,
            schedule = schedule,
            periodDays = periodDays,
            planned = planned,
            done = done,
            streak = streak,
            weeklyMinutes = perActivity.sumOf { it.weeklyMinutes },
            week = week,
            perActivity = perActivity,
            anyCompletion = completions.isNotEmpty(),
        )
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { StatsViewModel((this[APPLICATION_KEY] as HorariosApp).repository) }
        }
    }
}
