package com.eduardo.horarios.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import com.eduardo.horarios.HorariosApp
import com.eduardo.horarios.hasDay
import com.eduardo.horarios.nowMinuteOfDay
import com.eduardo.horarios.todayIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Mantiene el widget al día: se refresca cuando cambian los datos y además
 * programa una alarma (no despierta el móvil) para el próximo inicio/fin de actividad
 * o para la medianoche.
 */
object WidgetRefresher {

    /** Cambia cada vez que refrescamos, para que el widget recalcule "ahora". */
    val tick = MutableStateFlow(0L)

    suspend fun refresh(context: Context) {
        tick.value = System.currentTimeMillis()
        try {
            HorariosWidget().updateAll(context)
        } catch (e: Exception) {
        }
        scheduleNext(context)
    }

    suspend fun scheduleNext(context: Context) {
        val ids = try {
            GlanceAppWidgetManager(context).getGlanceIds(HorariosWidget::class.java)
        } catch (e: Exception) {
            emptyList()
        }
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, WidgetRefreshReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (ids.isEmpty()) {
            am.cancel(pi)
            return
        }

        val repo = (context.applicationContext as HorariosApp).repository
        val today = todayIndex()
        val now = nowMinuteOfDay()
        val nextBoundary = repo.getActiveActivities()
            .filter { it.daysMask.hasDay(today) }
            .flatMap { listOf(it.startMinute, it.endMinute) }
            .filter { it > now }
            .minOrNull()

        val zone = ZoneId.systemDefault()
        val trigger = if (nextBoundary != null) {
            LocalDate.now().atStartOfDay(zone).plusMinutes(nextBoundary.toLong())
        } else {
            LocalDate.now().plusDays(1).atStartOfDay(zone).plusMinutes(1)
        }.toInstant().toEpochMilli()

        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExact(AlarmManager.RTC, trigger, pi) else am.set(AlarmManager.RTC, trigger, pi)
        } catch (e: SecurityException) {
            am.set(AlarmManager.RTC, trigger, pi)
        }
    }

    private const val REQUEST_CODE = 424_242
}

class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetRefresher.refresh(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}

class HorariosWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HorariosWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val app = context.applicationContext as HorariosApp
        app.appScope.launch { WidgetRefresher.scheduleNext(app) }
    }
}
