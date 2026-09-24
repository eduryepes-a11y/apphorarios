package com.eduardo.horarios.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eduardo.horarios.HorariosApp
import com.eduardo.horarios.data.ActivityEntity
import com.eduardo.horarios.data.HorariosRepository
import com.eduardo.horarios.data.ScheduleEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

data class HomeState(
    val loading: Boolean = true,
    val schedule: ScheduleEntity? = null,
    val activities: List<ActivityEntity> = emptyList(),
    val hasAnySchedule: Boolean = false,
    /** (activityId, epochDay) de las actividades hechas esta semana. */
    val done: Set<Pair<Long, Long>> = emptySet(),
)

class HomeViewModel(private val repo: HorariosRepository) : ViewModel() {

    val state: StateFlow<HomeState> = combine(
        repo.activeSchedule,
        repo.activeActivities,
        repo.schedules,
        repo.completionsSince(LocalDate.now().with(DayOfWeek.MONDAY).toEpochDay()),
    ) { schedule, activities, all, completions ->
        HomeState(
            loading = false,
            schedule = schedule,
            activities = activities,
            hasAnySchedule = all.isNotEmpty(),
            done = completions.map { it.activityId to it.epochDay }.toSet(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun setDone(activityId: Long, epochDay: Long, done: Boolean) {
        viewModelScope.launch { repo.setDone(activityId, epochDay, done) }
    }

    /** Por si se acaba de conceder el permiso de alarmas exactas. */
    fun refreshAlarms(app: HorariosApp) {
        viewModelScope.launch { app.scheduler.rescheduleAll() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as HorariosApp
                HomeViewModel(app.repository)
            }
        }
    }
}
