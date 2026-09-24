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

data class HomeState(
    val loading: Boolean = true,
    val schedule: ScheduleEntity? = null,
    val activities: List<ActivityEntity> = emptyList(),
    val hasAnySchedule: Boolean = false,
)

class HomeViewModel(private val repo: HorariosRepository) : ViewModel() {

    val state: StateFlow<HomeState> = combine(
        repo.activeSchedule,
        repo.activeActivities,
        repo.schedules,
    ) { schedule, activities, all ->
        HomeState(
            loading = false,
            schedule = schedule,
            activities = activities,
            hasAnySchedule = all.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

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
