package com.eduardo.horarios.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eduardo.horarios.HorariosApp
import com.eduardo.horarios.data.ActivityEntity
import com.eduardo.horarios.data.CompletionEntity
import com.eduardo.horarios.data.DayOverrides
import com.eduardo.horarios.data.HorariosRepository
import com.eduardo.horarios.data.OverrideEntity
import com.eduardo.horarios.data.PlannedActivity
import com.eduardo.horarios.data.Planner
import com.eduardo.horarios.data.ScheduleEntity
import com.eduardo.horarios.nowMinuteOfDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

/** Día elegido, compartido entre las pestañas Hoy y Semana. */
object DaySelection {
    val date = MutableStateFlow(LocalDate.now())

    fun select(d: LocalDate) {
        date.value = d
    }

    fun moveWeeks(weeks: Long) {
        date.value = date.value.plusWeeks(weeks)
    }

    fun goToday() {
        date.value = LocalDate.now()
    }
}

private data class WeekData(
    val weekStart: LocalDate,
    val completions: List<CompletionEntity>,
    val overrides: List<OverrideEntity>,
)

data class PlanState(
    val loading: Boolean = true,
    val schedule: ScheduleEntity? = null,
    val activities: List<ActivityEntity> = emptyList(),
    val hasAnySchedule: Boolean = false,
    val date: LocalDate = LocalDate.now(),
    val weekStart: LocalDate = LocalDate.now().with(DayOfWeek.MONDAY),
    /** (activityId, epochDay) hechas en la semana visible. */
    val done: Set<Pair<Long, Long>> = emptySet(),
    val overrides: List<OverrideEntity> = emptyList(),
) {
    fun plan(d: LocalDate): List<PlannedActivity> = Planner.plan(d, activities, overrides)
    fun dayOverrides(d: LocalDate): DayOverrides = Planner.overridesFor(d.toEpochDay(), overrides)
    fun isDone(p: PlannedActivity): Boolean = (p.activity.id to p.epochDay) in done
    val isCurrentWeek: Boolean get() = weekStart == LocalDate.now().with(DayOfWeek.MONDAY)
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModel(private val repo: HorariosRepository) : ViewModel() {

    private val weekData = DaySelection.date
        .map { it.with(DayOfWeek.MONDAY) }
        .distinctUntilChanged()
        .flatMapLatest { ws ->
            val from = ws.toEpochDay()
            val to = from + 6
            combine(repo.completionsBetween(from, to), repo.overridesBetween(from, to)) { c, o ->
                WeekData(ws, c, o)
            }
        }

    val state: StateFlow<PlanState> = combine(
        repo.activeSchedule,
        repo.activeActivities,
        repo.schedules,
        weekData,
        DaySelection.date,
    ) { schedule, activities, all, week, date ->
        PlanState(
            loading = false,
            schedule = schedule,
            activities = activities,
            hasAnySchedule = all.isNotEmpty(),
            date = date,
            weekStart = week.weekStart,
            done = week.completions.map { it.activityId to it.epochDay }.toSet(),
            overrides = week.overrides,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanState())

    fun setDone(p: PlannedActivity, done: Boolean) {
        viewModelScope.launch { repo.setDone(p.activity.id, p.epochDay, done) }
    }

    fun setSkipped(p: PlannedActivity, skipped: Boolean) {
        viewModelScope.launch { repo.setSkipped(p.activity, p.epochDay, skipped) }
    }

    fun setDayOff(date: LocalDate, off: Boolean) {
        viewModelScope.launch { repo.setDayOff(date.toEpochDay(), off) }
    }

    /** «Voy con retraso»: retrasa lo que aún no ha empezado hoy. */
    fun delayToday(minutes: Int) {
        viewModelScope.launch { repo.shiftDay(LocalDate.now().toEpochDay(), nowMinuteOfDay(), minutes) }
    }

    fun resetDelays(date: LocalDate) {
        viewModelScope.launch { repo.resetShifts(date.toEpochDay()) }
    }

    fun refreshAlarms(app: HorariosApp) {
        viewModelScope.launch { app.scheduler.rescheduleAll() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { PlanViewModel((this[APPLICATION_KEY] as HorariosApp).repository) }
        }
    }
}
