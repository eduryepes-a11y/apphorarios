package com.eduardo.horarios.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.eduardo.horarios.HorariosApp
import com.eduardo.horarios.data.AutoBackup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Tareas de cada día: cambiar de horario si empieza o termina un periodo con fechas y hacer
 * la copia automática. Se hace al abrir la app, al encender el móvil y cada noche a las 00:01.
 */
object DailyWork {
    private const val REQUEST_CODE = 1_999_999_980

    /** Próximo 00:01 después de [now]. */
    fun nextTrigger(now: LocalDateTime): LocalDateTime = now.toLocalDate().plusDays(1).atTime(0, 1)

    suspend fun run(context: Context) {
        val app = context.applicationContext as HorariosApp
        val switched = app.repository.runAutoSwitch(LocalDate.now().toEpochDay())
        if (!switched) app.scheduler.rescheduleAll() // al cambiar ya se reprograma
        runCatching { AutoBackup.runIfDue(context, app.repository) }
        schedule(context)
    }

    fun schedule(context: Context) {
        val at = nextTrigger(LocalDateTime.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Inexacta: no hace falta el minuto exacto
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, DailyReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

class DailyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DailyWork.run(context)
            } finally {
                pending.finish()
            }
        }
    }
}
