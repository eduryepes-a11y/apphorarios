@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)

package com.eduardo.horarios.ui.settings

import androidx.compose.ui.ExperimentalComposeUiApi
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.eduardo.horarios.HorariosApp
import com.eduardo.horarios.alarm.AlarmLog
import com.eduardo.horarios.alarm.AlarmScheduler
import com.eduardo.horarios.alarm.NextReminder
import com.eduardo.horarios.alarm.Notifications
import com.eduardo.horarios.ui.components.SectionLabel
import com.eduardo.horarios.ui.theme.DefaultStatusBarIcons
import com.eduardo.horarios.R
import com.eduardo.horarios.dayName
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Pantalla de avisos: comprueba todo lo que puede impedir que lleguen las notificaciones
 * y permite enviar pruebas.
 */
@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    DefaultStatusBarIcons()
    val context = LocalContext.current
    val app = context.applicationContext as HorariosApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // Se recalcula cada vez que vuelves a la pantalla (p. ej. desde Ajustes)
    var refreshKey by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshKey++ }

    val notificationsOn = remember(refreshKey) { Notifications.canPost(context) }
    val channelOn = remember(refreshKey) { Notifications.channelEnabled(context) }
    val exactOn = remember(refreshKey) { app.scheduler.canScheduleExact() }
    val batteryOk = remember(refreshKey) { isIgnoringBattery(context) }
    var alarmMode by remember { mutableStateOf(app.scheduler.alarmClockMode) }
    var startAlerts by remember { mutableStateOf(app.scheduler.startAlerts) }
    var next by remember { mutableStateOf<NextReminder?>(null) }
    var log by remember { mutableStateOf(AlarmLog.read(context)) }
    var scheduledCount by remember { mutableIntStateOf(0) }
    // Mientras se reprograma no se muestra «no hay avisos» (sería falso)
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey, alarmMode, startAlerts) {
        // Reprograma por si algún permiso acaba de cambiar
        app.scheduler.rescheduleAll()
        next = app.scheduler.nextReminder()
        scheduledCount = app.scheduler.lastScheduledCount
        log = AlarmLog.read(context)
        loaded = true
    }

    val allOk = notificationsOn && channelOn && exactOn && batteryOk

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notifications_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Resumen
            Surface(
                color = if (allOk) Color(0xFF4CC38A).copy(alpha = 0.15f) else MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        stringResource(if (allOk) R.string.diag_all_ok else R.string.diag_check),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(4.dp))
                    if (!loaded) {
                        Text(stringResource(R.string.diag_loading), style = MaterialTheme.typography.bodyMedium)
                    } else Text(
                        next?.let {
                            val what = stringResource(
                                if (it.kind == AlarmScheduler.KIND_START) R.string.diag_kind_start else R.string.diag_kind_before
                            )
                            stringResource(
                                R.string.diag_next,
                                "${it.activity.emoji} ${it.activity.title}",
                                what,
                                formatTrigger(context, it.triggerAt),
                            )
                        } ?: stringResource(R.string.diag_no_next),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (loaded) Text(
                        pluralStringResource(R.plurals.diag_scheduled, scheduledCount, scheduledCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionLabel(stringResource(R.string.diag_section_what), Modifier.padding(top = 8.dp))

            ToggleCard(
                title = stringResource(R.string.diag_start_alerts),
                text = stringResource(R.string.diag_start_alerts_text),
                checked = startAlerts,
                onChange = {
                    app.scheduler.startAlerts = it
                    startAlerts = it
                },
            )

            SectionLabel(stringResource(R.string.diag_section_permissions), Modifier.padding(top = 8.dp))

            StatusRow(
                ok = notificationsOn,
                title = stringResource(R.string.diag_notifications),
                okText = stringResource(R.string.diag_allowed),
                badText = stringResource(R.string.diag_notifications_bad),
                action = stringResource(R.string.enable),
            ) { openAppNotificationSettings(context) }

            StatusRow(
                ok = channelOn,
                title = stringResource(R.string.diag_channel),
                okText = stringResource(R.string.diag_active),
                badText = stringResource(R.string.diag_channel_bad),
                action = stringResource(R.string.open),
            ) { openChannelSettings(context) }

            StatusRow(
                ok = exactOn,
                title = stringResource(R.string.diag_exact),
                okText = stringResource(R.string.diag_exact_ok),
                badText = stringResource(R.string.diag_exact_bad),
                action = stringResource(R.string.allow),
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                    )
                }
            }

            StatusRow(
                ok = batteryOk,
                title = stringResource(R.string.diag_battery),
                okText = stringResource(R.string.diag_battery_ok),
                badText = stringResource(R.string.diag_battery_bad),
                action = stringResource(R.string.allow),
            ) { requestIgnoreBattery(context) }

            SectionLabel(stringResource(R.string.diag_section_reliability), Modifier.padding(top = 8.dp))

            ToggleCard(
                title = stringResource(R.string.diag_alarm_mode),
                text = stringResource(R.string.diag_alarm_mode_text),
                checked = alarmMode,
                onChange = {
                    app.scheduler.alarmClockMode = it
                    alarmMode = it
                },
            )

            SectionLabel(stringResource(R.string.diag_section_test), Modifier.padding(top = 8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = {
                        if (!notificationsOn) {
                            scope.launch { snackbar.showSnackbar(context.getString(R.string.diag_enable_first)) }
                        } else {
                            Notifications.showTest(context, fromAlarm = false)
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.diag_test_now)) }

                Button(
                    onClick = {
                        app.scheduler.scheduleTest(60_000L)
                        scope.launch {
                            snackbar.showSnackbar(context.getString(R.string.diag_test_scheduled))
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.diag_test_1min)) }
            }

            Text(
                stringResource(R.string.diag_test_explain),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                SectionLabel(stringResource(R.string.diag_section_log), Modifier.weight(1f))
                TextButton(onClick = { log = AlarmLog.read(context) }) { Text(stringResource(R.string.refresh)) }
                if (log.isNotEmpty()) {
                    TextButton(onClick = {
                        AlarmLog.clear(context)
                        log = emptyList()
                    }) { Text(stringResource(R.string.clear)) }
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (log.isEmpty()) {
                        Text(
                            stringResource(R.string.diag_log_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        log.take(15).forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.diag_log_explain),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            SectionLabel(stringResource(R.string.diag_section_device), Modifier.padding(top = 8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.diag_device, "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(manufacturerTip()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { openAppDetails(context) }) { Text(stringResource(R.string.diag_open_app_settings)) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun ToggleCard(title: String, text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun StatusRow(
    ok: Boolean,
    title: String,
    okText: String,
    badText: String,
    action: String,
    onAction: () -> Unit,
) {
    val green = Color(0xFF4CC38A)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (ok) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (ok) green.copy(alpha = 0.18f) else MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (ok) Icons.Rounded.Check else Icons.Rounded.PriorityHigh,
                    contentDescription = null,
                    tint = if (ok) green else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    if (ok) okText else badText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!ok) TextButton(onClick = onAction) { Text(action) }
        }
    }
}

// ------------------------------------------------------------------
// Utilidades
// ------------------------------------------------------------------

private fun formatTrigger(context: Context, millis: Long): String {
    val dt = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val day = dayName(context, dt.dayOfWeek.value - 1).lowercase()
    return context.getString(R.string.diag_trigger_at, day, "%02d:%02d".format(dt.hour, dt.minute))
}

private fun isIgnoringBattery(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

@SuppressLint("BatteryLife")
private fun requestIgnoreBattery(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        )
    } catch (e: Exception) {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

private fun openAppNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    )
}

private fun openChannelSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, Notifications.CHANNEL_ID)
    )
}

private fun openAppDetails(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    )
}

private fun manufacturerTip(): Int = when (Build.MANUFACTURER.lowercase()) {
    "xiaomi", "redmi", "poco" -> R.string.tip_xiaomi
    "samsung" -> R.string.tip_samsung
    "huawei", "honor" -> R.string.tip_huawei
    "oppo", "realme", "oneplus", "vivo" -> R.string.tip_oppo
    else -> R.string.tip_generic
}
