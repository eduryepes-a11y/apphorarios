package com.eduardo.horarios.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.eduardo.horarios.HorariosApp
import com.eduardo.horarios.R
import com.eduardo.horarios.data.localized
import com.eduardo.horarios.data.Planner
import java.time.LocalDate
import com.eduardo.horarios.data.weekDays
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_TEST, false)) {
            val shown = Notifications.showTest(context, fromAlarm = true)
            val r = context.localized()
            AlarmLog.add(
                context,
                r.getString(R.string.log_test_received) + if (shown) "" else r.getString(R.string.log_no_permission),
            )
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
                val r = context.localized()
                val label = r.getString(if (kind == AlarmScheduler.KIND_START) R.string.log_kind_start else R.string.log_kind_before)
                when {
                    activity == null ->
                        AlarmLog.add(context, r.getString(R.string.log_missing, label))
                    active?.id != activity.scheduleId ->
                        AlarmLog.add(context, r.getString(R.string.log_inactive, label, activity.title))
                    kind !in app.scheduler.kindsFor(activity) || day !in activity.weekDays() ->
                        AlarmLog.add(context, r.getString(R.string.log_not_due, label, activity.title))
                    else -> {
                        val overrides = app.scheduler.loadOverrides(activity.scheduleId)
                        val today = Planner.overridesFor(LocalDate.now().toEpochDay(), overrides)
                        if (Planner.isSkipped(activity.id, today)) {
                            AlarmLog.add(context, r.getString(R.string.log_skipped, label, activity.title))
                            app.scheduler.schedule(activity, day, kind, System.currentTimeMillis() + 60_000L, overrides)
                            return@launch
                        }
                        val start = Planner.shiftedStart(activity.startMinute, today)
                        val end = activity.endMinute + (start - activity.startMinute)
                        val shown = if (kind == AlarmScheduler.KIND_START) {
                            Notifications.showStart(context, activity, start, end)
                        } else {
                            Notifications.showReminder(context, activity, start, end)
                        }
                        AlarmLog.add(
                            context,
                            r.getString(
                                if (shown) R.string.log_shown else R.string.log_not_shown,
                                label,
                                "${activity.emoji} ${activity.title}",
                            ),
                        )
                        // Reprogramar para la semana que viene
                        app.scheduler.schedule(activity, day, kind, System.currentTimeMillis() + 60_000L, overrides)
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
