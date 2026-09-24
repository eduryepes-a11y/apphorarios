package com.eduardo.horarios.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

/** Recibe el resultado de la instalación de PackageInstaller. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Android pide confirmación: mostramos su ventana "¿Actualizar esta app?"
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirm)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> UpdateManager.onInstallFinished(true, null)
            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateManager.onInstallCancelled()
            else -> UpdateManager.onInstallFinished(
                false,
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)?.let { "No se pudo instalar: $it" },
            )
        }
    }
}
