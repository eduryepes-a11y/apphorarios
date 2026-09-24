package com.eduardo.horarios.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.eduardo.horarios.HorariosApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Botón «Hecho» de la notificación de inicio: marca la actividad como hecha hoy. */
class DoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val activityId = intent.getLongExtra(EXTRA_ACTIVITY_ID, -1L)
        if (activityId < 0) return
        NotificationManagerCompat.from(context).cancel(Notifications.startId(activityId))
        val app = context.applicationContext as HorariosApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.repository.setDone(activityId, LocalDate.now().toEpochDay(), true)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.eduardo.horarios.DONE"
        const val EXTRA_ACTIVITY_ID = "activity_id"
    }
}
