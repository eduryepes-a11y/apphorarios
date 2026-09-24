package com.eduardo.horarios.data

import android.content.Context
import androidx.room.withTransaction
import com.eduardo.horarios.alarm.AlarmScheduler
import com.eduardo.horarios.widget.WidgetRefresher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class HorariosRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val scheduler: AlarmScheduler,
) {
    private val scheduleDao = db.scheduleDao()
    private val activityDao = db.activityDao()

    val schedules: Flow<List<ScheduleEntity>> = scheduleDao.observeAll()
    val activeSchedule: Flow<ScheduleEntity?> = scheduleDao.observeActive()

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeActivities: Flow<List<ActivityEntity>> = activeSchedule.flatMapLatest { s ->
        if (s == null) flowOf(emptyList()) else activityDao.observeForSchedule(s.id)
    }

    val activityCounts: Flow<Map<Long, Int>> =
        activityDao.observeCounts().map { list -> list.associate { it.scheduleId to it.count } }

    suspend fun getActiveSchedule(): ScheduleEntity? = scheduleDao.getActive()

    suspend fun getActiveActivities(): List<ActivityEntity> =
        scheduleDao.getActive()?.let { activityDao.getForSchedule(it.id) } ?: emptyList()

    suspend fun getActivity(id: Long): ActivityEntity? = activityDao.getById(id)

    // ---------- Horarios ----------

    suspend fun activate(id: Long) {
        scheduleDao.setActive(id)
        scheduler.rescheduleAll()
    }

    suspend fun createSchedule(name: String, emoji: String, colorIndex: Int): Long {
        val isFirst = scheduleDao.count() == 0
        val id = scheduleDao.insert(
            ScheduleEntity(name = name.trim(), emoji = emoji, colorIndex = colorIndex, isActive = isFirst)
        )
        if (isFirst) scheduler.rescheduleAll()
        return id
    }

    suspend fun updateSchedule(schedule: ScheduleEntity) {
        scheduleDao.update(schedule.copy(name = schedule.name.trim()))
        WidgetRefresher.refresh(context)
    }

    suspend fun duplicateSchedule(schedule: ScheduleEntity) {
        db.withTransaction {
            val newId = scheduleDao.insert(
                schedule.copy(
                    id = 0,
                    name = "${schedule.name} (copia)",
                    isActive = false,
                    createdAt = System.currentTimeMillis(),
                )
            )
            val copies = activityDao.getForSchedule(schedule.id).map { it.copy(id = 0, scheduleId = newId) }
            activityDao.insertAll(copies)
        }
    }

    suspend fun deleteSchedule(schedule: ScheduleEntity) {
        db.withTransaction {
            activityDao.deleteForSchedule(schedule.id)
            scheduleDao.delete(schedule)
            if (schedule.isActive) {
                scheduleDao.getAll().firstOrNull()?.let { scheduleDao.setActive(it.id) }
            }
        }
        scheduler.rescheduleAll()
    }

    // ---------- Actividades ----------

    suspend fun saveActivity(activity: ActivityEntity) {
        val id = if (activity.id == 0L) activityDao.insert(activity) else activity.id.also { activityDao.update(activity) }
        scheduler.rescheduleAll()
        scheduler.fireIfStartingNow(activity.copy(id = id))
    }

    suspend fun deleteActivity(activity: ActivityEntity) {
        activityDao.delete(activity)
        scheduler.rescheduleAll()
    }

    // ---------- Datos de ejemplo (solo la primera vez) ----------

    suspend fun seedIfFirstLaunch() {
        val prefs = context.getSharedPreferences("app", Context.MODE_PRIVATE)
        if (prefs.getBoolean("seeded", false)) return
        prefs.edit().putBoolean("seeded", true).apply()
        if (scheduleDao.count() > 0) return

        val weekdays = 0b0011111
        val weekend = 0b1100000
        val all = 0b1111111

        db.withTransaction {
            val work = scheduleDao.insert(
                ScheduleEntity(name = "Semana normal", emoji = "💼", colorIndex = 0, isActive = true)
            )
            activityDao.insertAll(
                listOf(
                    ActivityEntity(scheduleId = work, title = "Desayuno", emoji = "☕", daysMask = weekdays, startMinute = 8 * 60, endMinute = 8 * 60 + 30, colorIndex = 5, reminderMinutes = 0),
                    ActivityEntity(scheduleId = work, title = "Trabajo", emoji = "💻", daysMask = weekdays, startMinute = 9 * 60, endMinute = 14 * 60, colorIndex = 1, reminderMinutes = 10),
                    ActivityEntity(scheduleId = work, title = "Comida", emoji = "🍽️", daysMask = weekdays, startMinute = 14 * 60, endMinute = 15 * 60, colorIndex = 6, reminderMinutes = 0),
                    ActivityEntity(scheduleId = work, title = "Gimnasio", emoji = "💪", notes = "Pierna + cardio", daysMask = 0b0010101, startMinute = 18 * 60 + 30, endMinute = 19 * 60 + 30, colorIndex = 3, reminderMinutes = 15),
                    ActivityEntity(scheduleId = work, title = "Estudiar", emoji = "📚", daysMask = 0b0001010, startMinute = 20 * 60, endMinute = 21 * 60 + 30, colorIndex = 0, reminderMinutes = 10),
                    ActivityEntity(scheduleId = work, title = "Paseo", emoji = "🌳", daysMask = weekend, startMinute = 11 * 60, endMinute = 12 * 60 + 30, colorIndex = 2, reminderMinutes = 30),
                )
            )
            val holidays = scheduleDao.insert(
                ScheduleEntity(name = "Vacaciones", emoji = "🏖️", colorIndex = 2, isActive = false)
            )
            activityDao.insertAll(
                listOf(
                    ActivityEntity(scheduleId = holidays, title = "Piscina", emoji = "🏊", daysMask = all, startMinute = 12 * 60, endMinute = 14 * 60, colorIndex = 1, reminderMinutes = 15),
                    ActivityEntity(scheduleId = holidays, title = "Siesta", emoji = "😴", daysMask = all, startMinute = 16 * 60, endMinute = 17 * 60, colorIndex = 8, reminderMinutes = -1),
                )
            )
        }
        scheduler.rescheduleAll()
    }
}
