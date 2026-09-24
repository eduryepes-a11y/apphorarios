package com.eduardo.horarios.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.eduardo.horarios.MainActivity
import com.eduardo.horarios.R
import com.eduardo.horarios.data.localized
import com.eduardo.horarios.data.ActivityEntity
import com.eduardo.horarios.data.AppDatabase
import com.eduardo.horarios.hasDay
import com.eduardo.horarios.widget.WidgetRefresher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/** Próximo aviso programado (para la pantalla de diagnóstico). */
data class NextReminder(val activity: ActivityEntity, val triggerAt: Long, val kind: Int)

/**
 * Programa una alarma por cada (actividad, día) del horario activo.
 * Cuando salta, [ReminderReceiver] muestra la notificación y reprograma la de la semana siguiente.
 */
class AlarmScheduler(
    private val context: Context,
    private val db: AppDatabase,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("alarms", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Modo alarma: usa setAlarmClock, que los fabricantes (Xiaomi, Huawei…) casi nunca bloquean. */
    var alarmClockMode: Boolean
        get() = prefs.getBoolean(KEY_ALARM_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_ALARM_MODE, value).apply()

    /** Aviso "empieza ahora" en todas las actividades con aviso (además del aviso previo). */
    var startAlerts: Boolean
        get() = prefs.getBoolean(KEY_START_ALERTS, true)
        set(value) = prefs.edit().putBoolean(KEY_START_ALERTS, value).apply()

    /** Qué avisos lleva una actividad: [KIND_BEFORE] y/o [KIND_START]. */
    fun kindsFor(activity: ActivityEntity): List<Int> = buildList {
        if (activity.reminderMinutes < 0) return@buildList
        if (activity.reminderMinutes > 0) add(KIND_BEFORE)
        if (activity.reminderMinutes == 0 || startAlerts) add(KIND_START)
    }

    /** Cuántas alarmas se programaron la última vez (para la pantalla de diagnóstico). */
    var lastScheduledCount: Int = 0
        private set

    /**
     * Si una actividad empieza justo ahora (en el último minuto y medio), muestra ya el aviso de inicio.
     * Útil al crear/editar una actividad que empieza en este momento: su alarma ya habría pasado.
     */
    suspend fun fireIfStartingNow(activity: ActivityEntity) = withContext(Dispatchers.IO) {
        if (KIND_START !in kindsFor(activity)) return@withContext
        val now = System.currentTimeMillis()
        val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
        val day = today.dayOfWeek.value - 1
        if (!activity.daysMask.hasDay(day)) return@withContext
        val startMillis = today.toLocalDate().atStartOfDay(ZoneId.systemDefault())
            .plusMinutes(activity.startMinute.toLong()).toInstant().toEpochMilli()
        if (now - startMillis in 0..90_000) {
            val shown = Notifications.showStart(context, activity)
            val r = context.localized()
            AlarmLog.add(
                context,
                r.getString(
                    if (shown) R.string.log_shown else R.string.log_not_shown,
                    r.getString(R.string.log_kind_start),
                    "${activity.emoji} ${activity.title} " + r.getString(R.string.log_on_save),
                ),
            )
        }
    }

    suspend fun rescheduleAll() {
        // NonCancellable: aunque se cierre la pantalla que lo pidió, termina de programar todo.
        withContext(Dispatchers.IO + NonCancellable) {
            mutex.withLock {
                // 1. Cancelar todo lo programado antes
                prefs.getStringSet(KEY_CODES, emptySet())?.forEach { code ->
                    code.toIntOrNull()?.let { cancel(it) }
                }
                // 2. Programar el horario activo
                val codes = mutableSetOf<String>()
                val active = db.scheduleDao().getActive()
                if (active != null) {
                    for (activity in db.activityDao().getForSchedule(active.id)) {
                        for (kind in kindsFor(activity)) {
                            for (day in 0..6) {
                                if (activity.daysMask.hasDay(day)) {
                                    codes += schedule(activity, day, kind).toString()
                                }
                            }
                        }
                    }
                }
                prefs.edit().putStringSet(KEY_CODES, codes).apply()
                lastScheduledCount = codes.size
            }
            // El widget también depende del horario activo
            WidgetRefresher.refresh(context)
        }
    }

    /** Programa la próxima ocurrencia posterior a [after]. Devuelve el requestCode usado. */
    fun schedule(
        activity: ActivityEntity,
        day: Int,
        kind: Int,
        after: Long = System.currentTimeMillis(),
    ): Int {
        val code = requestCode(activity.id, day, kind)
        val trigger = nextTrigger(day, triggerMinute(activity, kind), after)
        val pi = PendingIntent.getBroadcast(
            context,
            code,
            Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_REMINDER
                putExtra(ReminderReceiver.EXTRA_ACTIVITY_ID, activity.id)
                putExtra(ReminderReceiver.EXTRA_DAY, day)
                putExtra(ReminderReceiver.EXTRA_KIND, kind)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        setAlarm(trigger, pi)
        return code
    }

    /** Programa una notificación de prueba dentro de [delayMillis]. */
    fun scheduleTest(delayMillis: Long): Long {
        val trigger = System.currentTimeMillis() + delayMillis
        val pi = PendingIntent.getBroadcast(
            context,
            TEST_CODE,
            Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_TEST
                putExtra(ReminderReceiver.EXTRA_TEST, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        setAlarm(trigger, pi)
        return trigger
    }

    /** Calcula cuál es el próximo aviso que va a saltar. */
    suspend fun nextReminder(): NextReminder? = withContext(Dispatchers.IO) {
        val active = db.scheduleDao().getActive() ?: return@withContext null
        val now = System.currentTimeMillis()
        db.activityDao().getForSchedule(active.id)
            .flatMap { a ->
                kindsFor(a).flatMap { kind ->
                    (0..6).filter { a.daysMask.hasDay(it) }.map { day ->
                        NextReminder(a, nextTrigger(day, triggerMinute(a, kind), now), kind)
                    }
                }
            }
            .minByOrNull { it.triggerAt }
    }

    private fun setAlarm(trigger: Long, pi: PendingIntent) {
        try {
            when {
                alarmClockMode && canScheduleExact() -> {
                    val show = PendingIntent.getActivity(
                        context,
                        0,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, show), pi)
                }
                canScheduleExact() ->
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
                else ->
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    private fun cancel(code: Int) {
        val pi = PendingIntent.getBroadcast(
            context,
            code,
            Intent(context, ReminderReceiver::class.java).apply { action = ACTION_REMINDER },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pi)
        pi.cancel()
    }

    companion object {
        private const val KEY_CODES = "scheduled_codes"
        private const val KEY_ALARM_MODE = "alarm_clock_mode"
        private const val KEY_START_ALERTS = "start_alerts"

        /** Aviso X minutos antes. */
        const val KIND_BEFORE = 0
        /** Aviso justo al empezar. */
        const val KIND_START = 1
        private const val TEST_CODE = 2_000_000_000
        const val ACTION_REMINDER = "com.eduardo.horarios.REMINDER"
        const val ACTION_TEST = "com.eduardo.horarios.TEST"

        fun requestCode(activityId: Long, day: Int, kind: Int): Int = ((activityId * 7 + day) * 2 + kind).toInt()

        fun triggerMinute(activity: ActivityEntity, kind: Int): Int =
            if (kind == KIND_START) activity.startMinute else activity.startMinute - activity.reminderMinutes

        /** Próximo instante (epoch ms) del día [day] (0 = lunes) a [minuteOfDay] que sea > [after]. */
        fun nextTrigger(day: Int, minuteOfDay: Int, after: Long): Long {
            val zone = ZoneId.systemDefault()
            val today = Instant.ofEpochMilli(after).atZone(zone).toLocalDate()
            for (offset in -1L..14L) {
                val date = today.plusDays(offset)
                if (date.dayOfWeek.value - 1 != day) continue
                val millis = date.atStartOfDay(zone)
                    .plusMinutes(minuteOfDay.toLong())
                    .toInstant()
                    .toEpochMilli()
                if (millis > after) return millis
            }
            return after + 7L * 24 * 60 * 60 * 1000
        }
    }
}
