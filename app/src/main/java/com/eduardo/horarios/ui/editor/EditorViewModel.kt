package com.eduardo.horarios.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eduardo.horarios.HorariosApp
import com.eduardo.horarios.R
import com.eduardo.horarios.data.ActivityEntity
import com.eduardo.horarios.data.HorariosRepository
import com.eduardo.horarios.data.Tracking
import java.time.LocalDate
import com.eduardo.horarios.todayIndex
import kotlinx.coroutines.launch

data class EditorForm(
    val title: String = "",
    val emoji: String = "📚",
    val notes: String = "",
    val daysMask: Int = 0,
    val startMinute: Int = 9 * 60,
    val endMinute: Int = 10 * 60,
    val colorIndex: Int = 0,
    val reminderMinutes: Int = 10,
    /** true = un solo día ([date]); false = cada semana ([daysMask]). */
    val oneOff: Boolean = false,
    val date: LocalDate = LocalDate.now(),
    val tracked: Boolean = true,
    /** El usuario ha tocado el interruptor: ya no lo cambiamos al escribir el nombre. */
    val trackedTouched: Boolean = false,
)

class EditorViewModel(
    private val repo: HorariosRepository,
    handle: SavedStateHandle,
) : ViewModel() {

    private val activityId: Long = handle.get<Long>("activityId") ?: -1L
    private val initialDate: LocalDate? = (handle.get<Long>("date") ?: -1L).takeIf { it >= 0 }?.let { LocalDate.ofEpochDay(it) }
    private val initialDay: Int = (handle.get<Int>("day") ?: -1).let {
        when {
            it in 0..6 -> it
            initialDate != null -> initialDate.dayOfWeek.value - 1
            else -> todayIndex()
        }
    }
    private var original: ActivityEntity? = null

    val isEditing: Boolean = activityId > 0

    var form by mutableStateOf(EditorForm(daysMask = 1 shl initialDay, date = initialDate ?: LocalDate.now()))
        private set
    var error by mutableStateOf<Int?>(null)
        private set

    init {
        if (isEditing) {
            viewModelScope.launch {
                repo.getActivity(activityId)?.let { a ->
                    original = a
                    form = EditorForm(
                        title = a.title,
                        emoji = a.emoji,
                        notes = a.notes,
                        daysMask = a.daysMask,
                        startMinute = a.startMinute,
                        endMinute = a.endMinute,
                        colorIndex = a.colorIndex,
                        reminderMinutes = a.reminderMinutes,
                        oneOff = a.onDate != null,
                        date = a.onDate?.let { LocalDate.ofEpochDay(it) } ?: form.date,
                        tracked = a.tracked,
                        trackedTouched = true,
                    )
                }
            }
        }
    }

    /** Al escribir el nombre propone si cuenta en Progreso (comer o dormir no; gimnasio sí). */
    fun setTitle(value: String) = update {
        val t = value.take(50)
        copy(title = t, tracked = if (trackedTouched) tracked else Tracking.defaultTracked(t))
    }

    fun setTracked(value: Boolean) = update { copy(tracked = value, trackedTouched = true) }

    fun update(transform: EditorForm.() -> EditorForm) {
        form = form.transform()
        error = null
    }

    /** Al cambiar la hora de inicio, mantiene la duración si la de fin queda antes. */
    fun setStart(minute: Int) = update {
        val duration = (endMinute - startMinute).coerceAtLeast(30)
        val newEnd = if (endMinute <= minute) (minute + duration).coerceAtMost(23 * 60 + 59) else endMinute
        copy(startMinute = minute, endMinute = newEnd)
    }

    fun setEnd(minute: Int) = update { copy(endMinute = minute) }

    fun save(onDone: () -> Unit) {
        val f = form
        error = when {
            f.title.isBlank() -> R.string.error_no_title
            !f.oneOff && f.daysMask == 0 -> R.string.error_no_days
            f.endMinute <= f.startMinute -> R.string.error_end_before_start
            else -> null
        }
        if (error != null) return

        viewModelScope.launch {
            val scheduleId = original?.scheduleId ?: repo.getActiveSchedule()?.id
            if (scheduleId == null) {
                error = R.string.error_no_active_schedule
                return@launch
            }
            repo.saveActivity(
                ActivityEntity(
                    id = original?.id ?: 0L,
                    scheduleId = scheduleId,
                    title = f.title.trim(),
                    emoji = f.emoji,
                    notes = f.notes.trim(),
                    daysMask = if (f.oneOff) 1 shl (f.date.dayOfWeek.value - 1) else f.daysMask,
                    startMinute = f.startMinute,
                    endMinute = f.endMinute,
                    colorIndex = f.colorIndex,
                    reminderMinutes = f.reminderMinutes,
                    onDate = if (f.oneOff) f.date.toEpochDay() else null,
                    tracked = f.tracked,
                )
            )
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val a = original ?: return
        viewModelScope.launch {
            repo.deleteActivity(a)
            onDone()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                EditorViewModel(
                    (this[APPLICATION_KEY] as HorariosApp).repository,
                    createSavedStateHandle(),
                )
            }
        }
    }
}
