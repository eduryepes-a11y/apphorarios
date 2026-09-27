package com.eduardo.horarios.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.eduardo.horarios.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Tras reiniciar el móvil, actualizar la app o cambiar la hora, vuelve a programar los avisos. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                    Notifications.showUpdated(context, UpdateManager.currentVersion(context))
                }
                DailyWork.run(context) // cambio automático de horario, copia y avisos
                WeeklySummary.schedule(context)
            } finally {
                pending.finish()
            }
        }
    }
}
