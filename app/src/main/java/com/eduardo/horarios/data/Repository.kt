package com.eduardo.horarios.data

import android.content.Context
import com.eduardo.horarios.R
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
    private val completionDao = db.completionDao()

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
                    name = context.localized().getString(R.string.schedule_copy_name, schedule.name),
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
            completionDao.deleteForSchedule(schedule.id)
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
        completionDao.deleteForActivity(activity.id)
        activityDao.delete(activity)
        scheduler.rescheduleAll()
    }

    // ---------- Hecho / estadísticas ----------

    fun completionsSince(fromDay: Long): Flow<List<CompletionEntity>> = completionDao.observeSince(fromDay)

    suspend fun setDone(activityId: Long, epochDay: Long, done: Boolean) {
        if (done) completionDao.insert(CompletionEntity(activityId, epochDay))
        else completionDao.delete(activityId, epochDay)
    }

    suspend fun isDone(activityId: Long, epochDay: Long): Boolean = completionDao.count(activityId, epochDay) > 0

    // ---------- Copias de seguridad y compartir ----------

    /** Todos los horarios, con sus actividades y días hechos. */
    suspend fun exportAll(): List<ScheduleExport> = scheduleDao.getAll().map { exportSchedule(it, withDone = true) }

    suspend fun exportSchedule(schedule: ScheduleEntity, withDone: Boolean): ScheduleExport =
        ScheduleExport(
            name = schedule.name,
            emoji = schedule.emoji,
            colorIndex = schedule.colorIndex,
            isActive = schedule.isActive,
            activities = activityDao.getForSchedule(schedule.id).map { a ->
                ActivityExport(
                    title = a.title,
                    emoji = a.emoji,
                    notes = a.notes,
                    daysMask = a.daysMask,
                    startMinute = a.startMinute,
                    endMinute = a.endMinute,
                    colorIndex = a.colorIndex,
                    reminderMinutes = a.reminderMinutes,
                    doneDays = if (withDone) completionDao.getForActivity(a.id).map { it.epochDay } else emptyList(),
                )
            },
        )

    /**
     * Importa horarios. Con [replace] borra antes todo lo que hay.
     * Devuelve cuántos horarios se han importado.
     */
    suspend fun import(file: HorariosFile, replace: Boolean): Int {
        db.withTransaction {
            if (replace) {
                completionDao.deleteAll()
                activityDao.deleteAll()
                scheduleDao.deleteAll()
            }
            val hadSchedules = scheduleDao.count() > 0
            var activateId: Long? = null
            file.schedules.forEachIndexed { index, s ->
                val name = if (!replace && scheduleDao.getAll().any { it.name == s.name }) "${s.name} (2)" else s.name
                val id = scheduleDao.insert(
                    ScheduleEntity(name = name, emoji = s.emoji, colorIndex = s.colorIndex, isActive = false)
                )
                if (activateId == null && ((replace && s.isActive) || (!hadSchedules && index == 0))) activateId = id
                for (a in s.activities) {
                    val activityId = activityDao.insert(
                        ActivityEntity(
                            scheduleId = id,
                            title = a.title,
                            emoji = a.emoji,
                            notes = a.notes,
                            daysMask = a.daysMask,
                            startMinute = a.startMinute,
                            endMinute = a.endMinute,
                            colorIndex = a.colorIndex,
                            reminderMinutes = a.reminderMinutes,
                        )
                    )
                    if (a.doneDays.isNotEmpty()) {
                        completionDao.insertAll(a.doneDays.map { CompletionEntity(activityId, it) })
                    }
                }
            }
            val toActivate = activateId ?: if (replace) scheduleDao.getAll().firstOrNull()?.id else null
            if (toActivate != null) scheduleDao.setActive(toActivate)
        }
        scheduler.rescheduleAll()
        return file.schedules.size
    }

    // ---------- Datos de ejemplo (solo la primera vez) ----------

    suspend fun seedIfFirstLaunch() {
        val prefs = context.getSharedPreferences("app", Context.MODE_PRIVATE)
        if (prefs.getBoolean("seeded", false)) return
        prefs.edit().putBoolean("seeded", true).apply()
        if (scheduleDao.count() > 0) return

        val r = context.localized()
        val weekdays = 0b0011111
        val weekend = 0b1100000
        val all = 0b1111111

        db.withTransaction {
            val work = scheduleDao.insert(
                ScheduleEntity(name = r.getString(R.string.seed_schedule_week), emoji = "💼", colorIndex = 0, isActive = true)
            )
            activityDao.insertAll(
                listOf(
                    ActivityEntity(scheduleId = work, title = r.getString(R.string.seed_breakfast), emoji = "☕", daysMask = weekdays, startMinute = 8 * 60, endMinute = 8 * 60 + 30, colorIndex = 5, reminderMinutes = 0),
                    ActivityEntity(scheduleId = work, title = r.getString(R.string.seed_work), emoji = "💻", daysMask = weekdays, startMinute = 9 * 60, endMinute = 14 * 60, colorIndex = 1, reminderMinutes = 10),
                    ActivityEntity(scheduleId = work, title = r.getString(R.string.seed_lunch), emoji = "🍽️", daysMask = weekdays, startMinute = 14 * 60, endMinute = 15 * 60, colorIndex = 6, reminderMinutes = 0),
                    ActivityEntity(scheduleId = work, title = r.getString(R.string.seed_gym), emoji = "💪", notes = r.getString(R.string.seed_gym_notes), daysMask = 0b0010101, startMinute = 18 * 60 + 30, endMinute = 19 * 60 + 30, colorIndex = 3, reminderMinutes = 15),
                    ActivityEntity(scheduleId = work, title = r.getString(R.string.seed_study), emoji = "📚", daysMask = 0b0001010, startMinute = 20 * 60, endMinute = 21 * 60 + 30, colorIndex = 0, reminderMinutes = 10),
                    ActivityEntity(scheduleId = work, title = r.getString(R.string.seed_walk), emoji = "🌳", daysMask = weekend, startMinute = 11 * 60, endMinute = 12 * 60 + 30, colorIndex = 2, reminderMinutes = 30),
                )
            )
            val holidays = scheduleDao.insert(
                ScheduleEntity(name = r.getString(R.string.seed_schedule_holidays), emoji = "🏖️", colorIndex = 2, isActive = false)
            )
            activityDao.insertAll(
                listOf(
                    ActivityEntity(scheduleId = holidays, title = r.getString(R.string.seed_pool), emoji = "🏊", daysMask = all, startMinute = 12 * 60, endMinute = 14 * 60, colorIndex = 1, reminderMinutes = 15),
                    ActivityEntity(scheduleId = holidays, title = r.getString(R.string.seed_nap), emoji = "😴", daysMask = all, startMinute = 16 * 60, endMinute = 17 * 60, colorIndex = 8, reminderMinutes = -1),
                )
            )
        }
        scheduler.rescheduleAll()
    }
}
