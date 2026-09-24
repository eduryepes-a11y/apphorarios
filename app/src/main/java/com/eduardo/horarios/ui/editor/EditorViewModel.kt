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
import com.eduardo.horarios.data.ActivityEntity
import com.eduardo.horarios.data.HorariosRepository
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
)

class EditorViewModel(
    private val repo: HorariosRepository,
    handle: SavedStateHandle,
) : ViewModel() {

    private val activityId: Long = handle.get<Long>("activityId") ?: -1L
    private val initialDay: Int = (handle.get<Int>("day") ?: -1).let { if (it in 0..6) it else todayIndex() }
    private var original: ActivityEntity? = null

    val isEditing: Boolean = activityId > 0

    var form by mutableStateOf(EditorForm(daysMask = 1 shl initialDay))
        private set
    var error by mutableStateOf<String?>(null)
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
                    )
                }
            }
        }
    }

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
            f.title.isBlank() -> "Ponle un nombre a la actividad"
            f.daysMask == 0 -> "Elige al menos un día"
            f.endMinute <= f.startMinute -> "La hora de fin debe ser posterior a la de inicio"
            else -> null
        }
        if (error != null) return

        viewModelScope.launch {
            val scheduleId = original?.scheduleId ?: repo.getActiveSchedule()?.id
            if (scheduleId == null) {
                error = "No hay ningún horario activo"
                return@launch
            }
            repo.saveActivity(
                ActivityEntity(
                    id = original?.id ?: 0L,
                    scheduleId = scheduleId,
                    title = f.title.trim(),
                    emoji = f.emoji,
                    notes = f.notes.trim(),
                    daysMask = f.daysMask,
                    startMinute = f.startMinute,
                    endMinute = f.endMinute,
                    colorIndex = f.colorIndex,
                    reminderMinutes = f.reminderMinutes,
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
