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
import com.eduardo.horarios.data.localized
import com.eduardo.horarios.durationLabel
import com.eduardo.horarios.hm
import com.eduardo.horarios.ui.theme.paletteColor

object Notifications {
    const val CHANNEL_ID = "reminders"
    private const val TEST_NOTIFICATION_ID = 999_999
    private const val UPDATED_NOTIFICATION_ID = 999_998

    fun createChannel(context: Context) {
        val r = context.localized()
        val channel = NotificationChannel(
            CHANNEL_ID,
            r.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = r.getString(R.string.channel_description)
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

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    @SuppressLint("MissingPermission")
    fun showTest(context: Context, fromAlarm: Boolean): Boolean {
        if (!canPost(context)) return false
        val r = context.localized()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(r.getString(R.string.notif_test_title))
            .setContentText(r.getString(if (fromAlarm) R.string.notif_test_alarm else R.string.notif_test_now))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(TEST_NOTIFICATION_ID, notification)
        return true
    }

    /** Tras actualizarse la app: toca para volver a abrirla. */
    @SuppressLint("MissingPermission")
    fun showUpdated(context: Context, version: String) {
        if (!canPost(context)) return
        val r = context.localized()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(r.getString(R.string.notif_updated_title, version))
            .setContentText(r.getString(R.string.notif_updated_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(UPDATED_NOTIFICATION_ID, notification)
    }

    /** Aviso previo: "Empieza en 10 min". */
    @SuppressLint("MissingPermission")
    fun showReminder(
        context: Context,
        activity: ActivityEntity,
        start: Int = activity.startMinute,
        end: Int = activity.endMinute,
    ): Boolean {
        if (!canPost(context)) return false
        val r = context.localized()
        val range = "${hm(start)} – ${hm(end)}"
        val text = if (activity.reminderMinutes == 0) {
            r.getString(R.string.notif_starts_now, range)
        } else {
            r.getString(R.string.notif_starts_in, durationLabel(r, activity.reminderMinutes), range)
        }
        val bigText = if (activity.notes.isBlank()) text else "$text\n${activity.notes}"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${activity.emoji} ${activity.title}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setColor(paletteColor(activity.colorIndex).toArgb())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()

        NotificationManagerCompat.from(context).notify(beforeId(activity.id), notification)
        return true
    }

    /** Notificación "empieza ahora", con botón «Hecho». Sustituye al aviso previo. */
    @SuppressLint("MissingPermission")
    fun showStart(
        context: Context,
        activity: ActivityEntity,
        start: Int = activity.startMinute,
        end: Int = activity.endMinute,
    ): Boolean {
        if (!canPost(context)) return false
        val r = context.localized()
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(beforeId(activity.id))

        val duration = durationLabel(r, end - start)
        val text = r.getString(
            R.string.notif_start_text,
            "${hm(start)} – ${hm(end)}",
            duration,
        )
        val bigText = if (activity.notes.isBlank()) text else "$text\n${activity.notes}"

        val donePi = PendingIntent.getBroadcast(
            context,
            startId(activity.id),
            Intent(context, DoneReceiver::class.java).apply {
                action = DoneReceiver.ACTION_DONE
                putExtra(DoneReceiver.EXTRA_ACTIVITY_ID, activity.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

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
            .addAction(0, r.getString(R.string.notif_action_done), donePi)
            .setTimeoutAfter((end - start).coerceAtLeast(1) * 60_000L)
            .build()

        manager.notify(startId(activity.id), notification)
        return true
    }

    fun beforeId(activityId: Long): Int = (activityId * 2).toInt()
    fun startId(activityId: Long): Int = (activityId * 2 + 1).toInt()
}
