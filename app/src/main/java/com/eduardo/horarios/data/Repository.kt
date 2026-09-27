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
import kotlinx.coroutines.flow.combine
import com.eduardo.horarios.pro.FreeState
import com.eduardo.horarios.pro.Pro
import com.eduardo.horarios.pro.ProRules
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

private const val KEY_TRACKING_DEFAULTS = "tracking_defaults_v13"
private const val SWITCH_PREFS = "auto_switch"
private const val KEY_LAST_DAY = "last_day"
private const val KEY_BASE_ID = "base_id"

class HorariosRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val scheduler: AlarmScheduler,
) {
    private val scheduleDao = db.scheduleDao()
    private val activityDao = db.activityDao()
    private val completionDao = db.completionDao()
    private val overrideDao = db.overrideDao()
    private val switchPrefs = context.getSharedPreferences(SWITCH_PREFS, Context.MODE_PRIVATE)
    private val switchMutex = Mutex()

    val schedules: Flow<List<ScheduleEntity>> = scheduleDao.observeAll()
    val activeSchedule: Flow<ScheduleEntity?> = scheduleDao.observeActive()

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeActivities: Flow<List<ActivityEntity>> = activeSchedule.flatMapLatest { s ->
        if (s == null) flowOf(emptyList()) else activityDao.observeForSchedule(s.id)
    }

    /** Qué está bloqueado 🔒 sin Pro (se recalcula al cambiar horarios, actividades o la suscripción). */
    val freeState: Flow<FreeState> = combine(scheduleDao.observeAll(), activityDao.observeAll(), Pro.state) { s, a, p ->
        ProRules.evaluate(p.active, s, a).copy(known = p.known)
    }

    suspend fun currentFreeState(): FreeState =
        ProRules.evaluate(Pro.isActive, scheduleDao.getAll(), activityDao.getAll()).copy(known = Pro.state.value.known)

    private val proMutex = Mutex()

    /**
     * Aplica las reglas de Pro: con Pro, quita los bloqueos por límite; sin Pro, si el horario activo
     * está bloqueado activa uno libre (o ninguno). Solo cuando ya se sabe si hay suscripción.
     */
    suspend fun enforceFreeLimits() = proMutex.withLock {
        if (!Pro.state.value.known) return@withLock
        if (Pro.isActive) {
            if (scheduleDao.getAll().any { it.freeLocked }) scheduleDao.clearFreeLocked()
        } else {
            val all = scheduleDao.getAll()
            val state = ProRules.evaluate(false, all, activityDao.getAll())
            val active = all.firstOrNull { it.isActive }
            if (active != null && state.isLocked(active.id)) {
                val next = ProRules.fallbackActive(state, all)
                if (next != null) scheduleDao.setActive(next) else scheduleDao.clearActive()
            }
        }
        scheduler.rescheduleAll()
    }

    /**
     * Al terminar Pro con más de 2 horarios normales: se quedan libres los elegidos y el resto se
     * bloquea 🔒 (sin borrar nada). La elección no se puede cambiar sin volver a Pro.
     */
    suspend fun keepSchedules(keep: Set<Long>): Boolean {
        val state = currentFreeState()
        if (!ProRules.isValidChoice(state, keep)) return false
        scheduleDao.setFreeLocked(ProRules.toLockAfterChoice(state, keep), true)
        enforceFreeLimits()
        return true
    }

    /** ¿Se puede crear otro horario ahora? (sin Pro, como mucho 2 normales sin bloquear) */
    suspend fun canCreateSchedule(): Boolean = currentFreeState().canCreate

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
    suspend fun createFromTemplate(template: ScheduleTemplate, activate: Boolean): Long? {
        if (!canCreateSchedule()) return null
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

    /** Activa un horario. No deja activar uno bloqueado 🔒 sin Pro (devuelve false). */
    suspend fun activate(id: Long): Boolean {
        if (currentFreeState().isLocked(id)) return false
        scheduleDao.setActive(id)
        scheduler.rescheduleAll()
        return true
    }

    // ---------- Semanas alternas ----------

    /** «Esta semana es la [parity]» en el ciclo de [cycle] semanas: ajusta las letras y reprograma los avisos. */
    suspend fun setThisWeekLetter(cycle: Int, parity: Int) {
        WeekCalibration.setThisWeek(context, cycle, parity)
        scheduler.rescheduleAll()
    }

    // ---------- Fechas automáticas ----------

    /** Horario «de siempre» al que se vuelve cuando termina un periodo con fechas. */
    private fun savedBaseId(): Long? =
        if (switchPrefs.contains(KEY_BASE_ID)) switchPrefs.getLong(KEY_BASE_ID, 0) else null

    private suspend fun applySwitch(d: SwitchDecision, today: Long) {
        switchPrefs.edit().apply {
            putLong(KEY_LAST_DAY, today)
            if (d.baseId == null) remove(KEY_BASE_ID) else putLong(KEY_BASE_ID, d.baseId)
        }.apply()
        d.activateId?.let { activate(it) }
    }

    /**
     * Una vez al día (al abrir la app, al encender el móvil y a las 00:01): si empieza o termina
     * un periodo con fechas, cambia el horario activo. Devuelve true si ha cambiado.
     */
    suspend fun runAutoSwitch(today: Long = LocalDate.now().toEpochDay()): Boolean = switchMutex.withLock {
        val last = if (switchPrefs.contains(KEY_LAST_DAY)) switchPrefs.getLong(KEY_LAST_DAY, 0) else null
        if (last == today) return@withLock false
        // Sin Pro no hay fechas automáticas (esos horarios están bloqueados)
        if (!Pro.isActive) {
            switchPrefs.edit().putLong(KEY_LAST_DAY, today).apply()
            return@withLock false
        }
        val all = scheduleDao.getAll()
        val d = ScheduleSwitcher.onNewDay(last, today, all, all.firstOrNull { it.isActive }?.id, savedBaseId())
        applySwitch(d, today)
        d.activateId != null
    }

    /** Pone (o quita, con null) las fechas automáticas de un horario. Si ya cubren hoy, se activa ya. */
    suspend fun setAutoRange(scheduleId: Long, from: Long?, to: Long?): Unit = switchMutex.withLock {
        if (!Pro.isActive) return@withLock
        val today = LocalDate.now().toEpochDay()
        val before = scheduleDao.getAll()
        val s = before.firstOrNull { it.id == scheduleId } ?: return@withLock
        val (f, t) = if (from == null || to == null) null to null else minOf(from, to) to maxOf(from, to)
        scheduleDao.update(s.copy(autoFrom = f, autoTo = t))
        val after = scheduleDao.getAll()
        val d = ScheduleSwitcher.onRangesChanged(today, before, after, after.firstOrNull { it.isActive }?.id, savedBaseId())
        applySwitch(d, today)
        WidgetRefresher.refresh(context)
    }

    /** Crea un horario vacío. Devuelve null si sin Pro ya se ha llegado al límite. */
    suspend fun createSchedule(name: String, emoji: String, colorIndex: Int): Long? {
        if (!canCreateSchedule()) return null
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

    /** Duplica un horario. Devuelve false si sin Pro ya se ha llegado al límite (o es uno bloqueado). */
    suspend fun duplicateSchedule(schedule: ScheduleEntity): Boolean {
        val state = currentFreeState()
        if (!state.canCreate || state.isLocked(schedule.id)) return false
        db.withTransaction {
            val newId = scheduleDao.insert(
                schedule.copy(
                    id = 0,
                    name = context.localized().getString(R.string.schedule_copy_name, schedule.name),
                    isActive = false,
                    createdAt = System.currentTimeMillis(),
                    // Las fechas no se copian: dos horarios no pueden mandar a la vez
                    autoFrom = null,
                    autoTo = null,
                )
            )
            val copies = activityDao.getForSchedule(schedule.id).map { it.copy(id = 0, scheduleId = newId) }
            activityDao.insertAll(copies)
        }
        return true
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
        // Sin Pro, el que queda activo no puede ser uno bloqueado
        enforceFreeLimits()
        scheduler.rescheduleAll()
    }

    // ---------- Actividades ----------

    /** Guarda una actividad. Sin Pro no deja guardar semanas alternas ni turnos (devuelve false). */
    suspend fun saveActivity(activity: ActivityEntity): Boolean {
        if (!Pro.isActive && ProRules.usesPro(activity)) return false
        val id = if (activity.id == 0L) activityDao.insert(activity) else activity.id.also { activityDao.update(activity) }
        scheduler.rescheduleAll()
        scheduler.fireIfStartingNow(activity.copy(id = id))
        return true
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
            freeLocked = withDone && schedule.freeLocked,
            autoFrom = if (withDone) schedule.autoFrom else null,
            autoTo = if (withDone) schedule.autoTo else null,
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
                    weekParity = a.weekParity,
                    weekCycle = a.weekCycle,
                    rotStart = a.rotStart,
                    rotOn = a.rotOn,
                    rotOff = a.rotOff,
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
            // Sin Pro: los que pasan del límite de horarios llegan bloqueados 🔒 (los que usan Pro ya lo están)
            val isPro = Pro.isActive
            val unlockedNormal = if (isPro) 0 else ProRules.evaluate(false, scheduleDao.getAll(), activityDao.getAll())
                .let { st -> scheduleDao.getAll().count { !st.isLocked(it.id) } }
            val incomingPro = file.schedules.map { s -> s.autoFrom != null || s.activities.any { it.onDate == null && (it.weekParity != 0 || (it.rotStart != null && it.rotOn > 0)) } }
            // Los que ya venían bloqueados en la copia tampoco ocupan hueco
            val importLocked = ProRules.importLocks(isPro, unlockedNormal, incomingPro.mapIndexed { i, pro -> pro || file.schedules[i].freeLocked })
            file.schedules.forEachIndexed { index, s ->
                val name = if (!replace && scheduleDao.getAll().any { it.name == s.name }) "${s.name} (2)" else s.name
                val id = scheduleDao.insert(
                    ScheduleEntity(
                        name = name,
                        emoji = s.emoji,
                        colorIndex = s.colorIndex,
                        isActive = false,
                        autoFrom = s.autoFrom,
                        autoTo = s.autoTo,
                        freeLocked = !isPro && (s.freeLocked || importLocked[index]),
                    )
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
                            weekParity = a.weekParity,
                            weekCycle = a.weekCycle,
                            rotStart = a.rotStart,
                            rotOn = a.rotOn,
                            rotOff = a.rotOff,
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
        // Al restaurar una copia completa, también las letras de las semanas alternas
        if (replace) file.weekOffsets?.let { WeekCalibration.replaceAll(context, it) }
        enforceFreeLimits()
        scheduler.rescheduleAll()
        return file.schedules.size
    }
}
