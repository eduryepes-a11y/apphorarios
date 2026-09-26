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
import java.time.LocalDate

private const val KEY_TRACKING_DEFAULTS = "tracking_defaults_v13"

class HorariosRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val scheduler: AlarmScheduler,
) {
    private val scheduleDao = db.scheduleDao()
    private val activityDao = db.activityDao()
    private val completionDao = db.completionDao()
    private val overrideDao = db.overrideDao()

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

    // ---------- Excepciones, días libres y retrasos ----------

    /** Cambios puntuales del horario activo entre dos días (epochDay, ambos incluidos). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun overridesBetween(fromDay: Long, toDay: Long): Flow<List<OverrideEntity>> = activeSchedule.flatMapLatest { s ->
        if (s == null) flowOf(emptyList()) else overrideDao.observeRange(s.id, fromDay, toDay)
    }

    suspend fun getOverrides(fromDay: Long, toDay: Long): List<OverrideEntity> =
        scheduleDao.getActive()?.let { overrideDao.getRange(it.id, fromDay, toDay) } ?: emptyList()

    /** Plan de hoy del horario activo (con excepciones y retrasos). */
    suspend fun todayPlan(): List<PlannedActivity> {
        val today = LocalDate.now()
        return Planner.plan(today, getActiveActivities(), getOverrides(today.toEpochDay(), today.toEpochDay()))
    }

    /** Saltar (o recuperar) una actividad solo el día [epochDay]. */
    suspend fun setSkipped(activity: ActivityEntity, epochDay: Long, skipped: Boolean) {
        overrideDao.deleteSkip(activity.scheduleId, epochDay, activity.id)
        if (skipped) {
            overrideDao.insert(
                OverrideEntity(
                    scheduleId = activity.scheduleId,
                    epochDay = epochDay,
                    type = OverrideEntity.TYPE_SKIP,
                    activityId = activity.id,
                )
            )
        }
        scheduler.rescheduleAll()
    }

    /** Marcar (o quitar) un día libre en el horario activo. */
    suspend fun setDayOff(epochDay: Long, off: Boolean) {
        val s = scheduleDao.getActive() ?: return
        overrideDao.deleteDayOff(s.id, epochDay)
        if (off) overrideDao.insert(OverrideEntity(scheduleId = s.id, epochDay = epochDay, type = OverrideEntity.TYPE_SKIP))
        scheduler.rescheduleAll()
    }

    /** Retrasar [minutes] las actividades de hoy que empiezan a partir de [fromMinute]. */
    suspend fun shiftDay(epochDay: Long, fromMinute: Int, minutes: Int) {
        val s = scheduleDao.getActive() ?: return
        overrideDao.insert(
            OverrideEntity(
                scheduleId = s.id,
                epochDay = epochDay,
                type = OverrideEntity.TYPE_SHIFT,
                fromMinute = fromMinute,
                minutes = minutes,
            )
        )
        scheduler.rescheduleAll()
    }

    suspend fun resetShifts(epochDay: Long) {
        val s = scheduleDao.getActive() ?: return
        overrideDao.deleteShifts(s.id, epochDay)
        scheduler.rescheduleAll()
    }

    /** Borra cambios puntuales de hace más de un mes. */
    suspend fun cleanOldOverrides() {
        // Se guarda un año de historia para las estadísticas (retrasos, saltos, días libres)
        overrideDao.deleteOlderThan(LocalDate.now().minusDays(400).toEpochDay())
    }

    /** Datos del horario activo para estadísticas fuera de las pantallas (resumen semanal, CSV). */
    suspend fun statsSnapshot(fromDay: Long, toDay: Long): Triple<List<ActivityEntity>, List<CompletionEntity>, List<OverrideEntity>>? {
        val s = scheduleDao.getActive() ?: return null
        return Triple(
            activityDao.getForSchedule(s.id),
            completionDao.getRange(fromDay, toDay),
            overrideDao.getRange(s.id, fromDay, toDay),
        )
    }

    // ---------- Plantillas y alta rápida ----------

    /** Crea un horario a partir de una plantilla. Devuelve su id. */
    suspend fun createFromTemplate(template: ScheduleTemplate, activate: Boolean): Long {
        val r = context.localized()
        val id = db.withTransaction {
            val isFirst = scheduleDao.count() == 0
            val newId = scheduleDao.insert(
                ScheduleEntity(
                    name = r.getString(template.nameRes),
                    emoji = template.emoji,
                    colorIndex = template.colorIndex,
                    isActive = false,
                )
            )
            activityDao.insertAll(template.activities.map { it.toEntity(r, newId) })
            if (activate || isFirst) scheduleDao.setActive(newId)
            newId
        }
        scheduler.rescheduleAll()
        return id
    }

    /** Añade varias actividades de golpe al horario activo. */
    suspend fun addActivities(activities: List<ActivityEntity>) {
        val s = scheduleDao.getActive() ?: return
        activityDao.insertAll(activities.map { it.copy(id = 0, scheduleId = s.id) })
        scheduler.rescheduleAll()
    }

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
            overrideDao.deleteForSchedule(schedule.id)
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

    /**
     * Una sola vez al actualizar a la v1.3: las actividades fijas que ya existían (comer, dormir,
     * clase…) dejan de contar en Progreso. Las demás siguen igual.
     */
    suspend fun applyTrackingDefaultsOnce() {
        val prefs = context.getSharedPreferences(SettingsStore.PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_TRACKING_DEFAULTS, false)) return
        db.withTransaction {
            for (a in activityDao.getAll()) {
                if (a.tracked && !Tracking.defaultTracked(a.title)) activityDao.setTracked(a.id, false)
            }
        }
        prefs.edit().putBoolean(KEY_TRACKING_DEFAULTS, true).apply()
    }

    suspend fun deleteActivity(activity: ActivityEntity) {
        completionDao.deleteForActivity(activity.id)
        overrideDao.deleteForActivity(activity.id)
        activityDao.delete(activity)
        scheduler.rescheduleAll()
    }

    // ---------- Hecho / estadísticas ----------

    fun completionsSince(fromDay: Long): Flow<List<CompletionEntity>> = completionDao.observeSince(fromDay)

    fun completionsBetween(fromDay: Long, toDay: Long): Flow<List<CompletionEntity>> =
        completionDao.observeRange(fromDay, toDay)

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
                    onDate = a.onDate,
                    tracked = a.tracked,
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
                overrideDao.deleteAll()
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
                            onDate = a.onDate,
                            tracked = a.tracked,
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
}
