package com.eduardo.horarios.alarm

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.eduardo.horarios.MainActivity
import com.eduardo.horarios.R
import com.eduardo.horarios.data.ActivityEntity
import com.eduardo.horarios.durationLabel
import com.eduardo.horarios.hm
import com.eduardo.horarios.ui.theme.paletteColor

object Notifications {
    const val CHANNEL_ID = "reminders"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Recordatorios",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Avisos de las actividades de tu horario"
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED)

    /** true si el canal de recordatorios está activo (el usuario puede apagarlo aparte). */
    fun channelEnabled(context: Context): Boolean {
        val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL_ID)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    @SuppressLint("MissingPermission")
    fun showTest(context: Context, fromAlarm: Boolean): Boolean {
        if (!canPost(context)) return false
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("✅ ¡Los avisos funcionan!")
            .setContentText(
                if (fromAlarm) "La alarma de prueba ha llegado a su hora." else "Esta es una notificación de prueba."
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(TEST_NOTIFICATION_ID, notification)
        return true
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private const val TEST_NOTIFICATION_ID = 999_999
    private const val UPDATED_NOTIFICATION_ID = 999_998

    /** Tras actualizarse la app: toca para volver a abrirla. */
    @SuppressLint("MissingPermission")
    fun showUpdated(context: Context, version: String) {
        if (!canPost(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("✅ Horarios actualizada a la versión $version")
            .setContentText("Toca para abrirla.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(UPDATED_NOTIFICATION_ID, notification)
    }

    @SuppressLint("MissingPermission")
    fun showReminder(context: Context, activity: ActivityEntity): Boolean {
        if (!canPost(context)) return false

        val range = "${hm(activity.startMinute)} – ${hm(activity.endMinute)}"
        val text = if (activity.reminderMinutes == 0) {
            "Empieza ahora · $range"
        } else {
            "Empieza en ${durationLabel(activity.reminderMinutes)} · $range"
        }
        val bigText = if (activity.notes.isBlank()) text else "$text\n${activity.notes}"

        val openApp = openAppIntent(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${activity.emoji} ${activity.title}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setColor(paletteColor(activity.colorIndex).toArgb())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()

        NotificationManagerCompat.from(context).notify(beforeId(activity), notification)
        return true
    }

    /** Notificación "empieza ahora". Sustituye al aviso previo si seguía en pantalla. */
    @SuppressLint("MissingPermission")
    fun showStart(context: Context, activity: ActivityEntity): Boolean {
        if (!canPost(context)) return false
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(beforeId(activity))

        val duration = durationLabel(activity.endMinute - activity.startMinute)
        val text = "Empieza ahora · ${hm(activity.startMinute)} – ${hm(activity.endMinute)} ($duration)"
        val bigText = if (activity.notes.isBlank()) text else "$text\n${activity.notes}"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("▶️ ${activity.emoji} ${activity.title}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setColor(paletteColor(activity.colorIndex).toArgb())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .setTimeoutAfter((activity.endMinute - activity.startMinute).coerceAtLeast(1) * 60_000L)
            .build()

        manager.notify(startId(activity), notification)
        return true
    }

    private fun beforeId(activity: ActivityEntity): Int = (activity.id * 2).toInt()
    private fun startId(activity: ActivityEntity): Int = (activity.id * 2 + 1).toInt()
}
