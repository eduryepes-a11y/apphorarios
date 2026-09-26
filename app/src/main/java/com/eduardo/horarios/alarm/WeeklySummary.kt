package com.eduardo.horarios.alarm

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.eduardo.horarios.HorariosApp
import com.eduardo.horarios.MainActivity
import com.eduardo.horarios.R
import com.eduardo.horarios.data.StatsCalculator
import com.eduardo.horarios.data.localized
import com.eduardo.horarios.durationLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Resumen semanal opcional (desactivado por defecto): el domingo a las 19:00, un aviso con
 * las horas de la semana, los hábitos cumplidos y los días con retraso.
 */
object WeeklySummary {
    private const val PREFS = "weekly_summary"
    private const val KEY_ENABLED = "enabled"
    const val CHANNEL_ID = "weekly_summary"
    private const val NOTIFICATION_ID = 999_990
    private const val REQUEST_CODE = 1_999_999_990
    private const val HOUR = 19

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) schedule(context) else cancel(context)
    }

    /** Próximo domingo a las 19:00 (hoy, si aún no han dado). */
    fun nextTrigger(now: LocalDateTime): LocalDateTime {
        val sunday = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(HOUR, 0)
        return if (sunday.isAfter(now)) sunday else sunday.plusWeeks(1)
    }

    fun schedule(context: Context) {
        if (!isEnabled(context)) return
        val at = nextTrigger(LocalDateTime.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Inexacta a propósito: no necesita el minuto exacto y así ahorra batería
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(context))
    }

    private fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, WeeklySummaryReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun createChannel(context: Context) {
        val r = context.localized()
        val channel = NotificationChannel(CHANNEL_ID, r.getString(R.string.summary_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Calcula y muestra el resumen de la semana (lunes–domingo) que termina hoy. */
    @SuppressLint("MissingPermission")
    suspend fun show(context: Context) {
        if (!Notifications.canPost(context)) return
        val app = context.applicationContext as HorariosApp
        val today = LocalDate.now()
        val monday = today.with(DayOfWeek.MONDAY)
        val (acts, completions, overrides) =
            app.repository.statsSnapshot(monday.minusWeeks(1).toEpochDay(), today.toEpochDay()) ?: return
        val week = StatsCalculator.compute(today, 24 * 60, acts, completions, overrides, periodDays = 7)
        val r = context.localized()
        val delays = if (week.happened.delayDays == 0) {
            r.getString(R.string.summary_no_delays)
        } else {
            r.resources.getQuantityString(R.plurals.summary_delays, week.happened.delayDays, week.happened.delayDays)
        }
        val text = r.getString(
            R.string.summary_text,
            durationLabel(r, week.weekHours.totalMinutes),
            week.done,
            week.planned,
            delays,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(r.getString(R.string.summary_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    REQUEST_CODE,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}

class WeeklySummaryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WeeklySummary.show(context)
            } finally {
                WeeklySummary.schedule(context) // la del domingo siguiente
                pending.finish()
            }
        }
    }
}
