package com.eduardo.horarios.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eduardo.horarios.HorariosApp
import com.eduardo.horarios.data.HorariosRepository
import com.eduardo.horarios.data.ScheduleEntity
import com.eduardo.horarios.data.StatsCalculator
import com.eduardo.horarios.data.StatsResult
import com.eduardo.horarios.nowMinuteOfDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class StatsState(
    val loading: Boolean = true,
    val schedule: ScheduleEntity? = null,
    val result: StatsResult? = null,
    /** Hay alguna actividad que se sigue en Progreso. */
    val anyTracked: Boolean = false,
    val anyCompletion: Boolean = false,
)

class StatsViewModel(private val repo: HorariosRepository) : ViewModel() {

    val period = MutableStateFlow(7)

    val state: StateFlow<StatsState> = combine(
        repo.activeSchedule,
        repo.activeActivities,
        repo.completionsSince(LocalDate.now().minusDays(400).toEpochDay()),
        repo.overridesBetween(LocalDate.now().minusDays(400).toEpochDay(), LocalDate.now().plusDays(7).toEpochDay()),
        period,
    ) { schedule, activities, completions, overrides, periodDays ->
        StatsState(
            loading = false,
            schedule = schedule,
            result = if (schedule == null) null else StatsCalculator.compute(
                today = LocalDate.now(),
                nowMinute = nowMinuteOfDay(),
                allActs = activities,
                completions = completions,
                overrides = overrides,
                periodDays = periodDays,
            ),
            anyTracked = activities.any { it.tracked },
            anyCompletion = completions.isNotEmpty(),
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsState())

    /** CSV del último año del horario activo (para «Exportar»). */
    suspend fun exportCsv(): String? {
        val today = LocalDate.now()
        val from = today.minusDays(364)
        val (acts, completions, overrides) = repo.statsSnapshot(from.toEpochDay(), today.toEpochDay()) ?: return null
        return StatsCalculator.csv(from, today, acts, completions, overrides)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { StatsViewModel((this[APPLICATION_KEY] as HorariosApp).repository) }
        }
    }
}
