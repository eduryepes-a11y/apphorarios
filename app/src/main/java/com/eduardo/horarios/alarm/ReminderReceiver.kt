package com.eduardo.horarios.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.eduardo.horarios.HorariosApp
import com.eduardo.horarios.hasDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_TEST, false)) {
            val shown = Notifications.showTest(context, fromAlarm = true)
            AlarmLog.add(context, "🧪 Alarma de prueba recibida" + if (shown) "" else " (sin permiso para notificar)")
            return
        }
        val activityId = intent.getLongExtra(EXTRA_ACTIVITY_ID, -1L)
        val day = intent.getIntExtra(EXTRA_DAY, -1)
        val kind = intent.getIntExtra(EXTRA_KIND, AlarmScheduler.KIND_BEFORE)
        if (activityId < 0 || day < 0) return

        val app = context.applicationContext as HorariosApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val activity = app.database.activityDao().getById(activityId)
                val active = app.database.scheduleDao().getActive()
                val label = if (kind == AlarmScheduler.KIND_START) "▶️ inicio" else "⏰ aviso previo"
                when {
                    activity == null ->
                        AlarmLog.add(context, "$label recibido de una actividad que ya no existe")
                    active?.id != activity.scheduleId ->
                        AlarmLog.add(context, "$label de ${activity.title}: su horario no está activo")
                    kind !in app.scheduler.kindsFor(activity) || !activity.daysMask.hasDay(day) ->
                        AlarmLog.add(context, "$label de ${activity.title}: ya no toca avisar")
                    else -> {
                        val shown = if (kind == AlarmScheduler.KIND_START) {
                            Notifications.showStart(context, activity)
                        } else {
                            Notifications.showReminder(context, activity)
                        }
                        AlarmLog.add(
                            context,
                            "$label de ${activity.emoji} ${activity.title}: " +
                                if (shown) "notificación mostrada" else "NO mostrada (notificaciones desactivadas)",
                        )
                        // Reprogramar para la semana que viene
                        app.scheduler.schedule(activity, day, kind, System.currentTimeMillis() + 60_000L)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_ACTIVITY_ID = "activity_id"
        const val EXTRA_DAY = "day"
        const val EXTRA_TEST = "test"
        const val EXTRA_KIND = "kind"
    }
}
