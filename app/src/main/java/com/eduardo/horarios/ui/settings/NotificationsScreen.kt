@file:OptIn(ExperimentalMaterial3Api::class)

package com.eduardo.horarios.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.eduardo.horarios.DAY_NAMES
import com.eduardo.horarios.HorariosApp
import com.eduardo.horarios.alarm.AlarmLog
import com.eduardo.horarios.alarm.AlarmScheduler
import com.eduardo.horarios.alarm.NextReminder
import com.eduardo.horarios.alarm.Notifications
import com.eduardo.horarios.ui.components.SectionLabel
import com.eduardo.horarios.ui.theme.StatusBarIcons
import com.eduardo.horarios.update.UpdateManager
import com.eduardo.horarios.update.UpdateState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Pantalla de avisos: comprueba todo lo que puede impedir que lleguen las notificaciones
 * y permite enviar pruebas.
 */
@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    StatusBarIcons(darkIcons = !isSystemInDarkTheme())
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

    LaunchedEffect(refreshKey, alarmMode, startAlerts) {
        // Reprograma por si algún permiso acaba de cambiar
        app.scheduler.rescheduleAll()
        next = app.scheduler.nextReminder()
        scheduledCount = app.scheduler.lastScheduledCount
        log = AlarmLog.read(context)
    }

    val allOk = notificationsOn && channelOn && exactOn && batteryOk

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Avisos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Atrás")
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
                        if (allOk) "✅ Todo listo" else "⚠️ Hay algo que revisar",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        next?.let {
                            val what = if (it.kind == AlarmScheduler.KIND_START) "empieza" else "aviso previo"
                            "Próximo aviso: ${it.activity.emoji} ${it.activity.title} ($what) · ${formatTrigger(it.triggerAt)}"
                        }
                            ?: "No hay avisos programados en el horario activo.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "$scheduledCount alarmas programadas esta semana",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionLabel("Qué avisos quieres", Modifier.padding(top = 8.dp))

            ToggleCard(
                title = "Avisar cuando empieza",
                text = "Una notificación «▶️ Empieza ahora» al inicio de cada actividad, además del aviso " +
                    "previo que elijas (5, 10, 15… min antes). Las actividades con «Sin aviso» no avisan nunca.",
                checked = startAlerts,
                onChange = {
                    app.scheduler.startAlerts = it
                    startAlerts = it
                },
            )

            SectionLabel("Permisos", Modifier.padding(top = 8.dp))

            StatusRow(
                ok = notificationsOn,
                title = "Notificaciones",
                okText = "Permitidas",
                badText = "Desactivadas: no se puede mostrar ningún aviso.",
                action = "Activar",
            ) { openAppNotificationSettings(context) }

            StatusRow(
                ok = channelOn,
                title = "Canal «Recordatorios»",
                okText = "Activo",
                badText = "El canal de recordatorios está silenciado.",
                action = "Abrir",
            ) { openChannelSettings(context) }

            StatusRow(
                ok = exactOn,
                title = "Alarmas exactas",
                okText = "Permitidas: los avisos llegan a su hora",
                badText = "Sin este permiso los avisos pueden retrasarse mucho.",
                action = "Permitir",
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                    )
                }
            }

            StatusRow(
                ok = batteryOk,
                title = "Batería sin restricciones",
                okText = "La app puede despertarse para avisarte",
                badText = "El ahorro de batería puede bloquear los avisos con la app cerrada.",
                action = "Permitir",
            ) { requestIgnoreBattery(context) }

            SectionLabel("Fiabilidad", Modifier.padding(top = 8.dp))

            ToggleCard(
                title = "Modo alarma",
                text = "Programa los avisos como alarmas del sistema. Es lo más fiable en Xiaomi, " +
                    "Huawei, Oppo… (verás un icono de alarma en la barra de estado).",
                checked = alarmMode,
                onChange = {
                    app.scheduler.alarmClockMode = it
                    alarmMode = it
                },
            )

            SectionLabel("Probar", Modifier.padding(top = 8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = {
                        if (!notificationsOn) {
                            scope.launch { snackbar.showSnackbar("Primero activa las notificaciones") }
                        } else {
                            Notifications.showTest(context, fromAlarm = false)
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f),
                ) { Text("Probar ahora") }

                Button(
                    onClick = {
                        app.scheduler.scheduleTest(60_000L)
                        scope.launch {
                            snackbar.showSnackbar("Aviso de prueba en 1 minuto. Cierra la app y bloquea el móvil.")
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f),
                ) { Text("Probar en 1 min") }
            }

            Text(
                "«Probar ahora» comprueba que el móvil muestra notificaciones. «Probar en 1 min» comprueba " +
                    "que la alarma llega con la app cerrada: si esta no aparece, el problema es la batería " +
                    "o el inicio automático del fabricante.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                SectionLabel("Historial de avisos", Modifier.weight(1f))
                TextButton(onClick = { log = AlarmLog.read(context) }) { Text("Actualizar") }
                if (log.isNotEmpty()) {
                    TextButton(onClick = {
                        AlarmLog.clear(context)
                        log = emptyList()
                    }) { Text("Borrar") }
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
                            "Todavía no ha llegado ninguna alarma. Cuando salte un aviso aparecerá aquí, " +
                                "aunque la notificación no se llegue a ver.",
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
                "Si a la hora de una actividad aquí no aparece nada, la alarma no ha llegado (el móvil la ha " +
                    "bloqueado): activa el «Modo alarma» y quita las restricciones de batería.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            SectionLabel("Según tu móvil", Modifier.padding(top = 8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Móvil detectado: ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}", style = MaterialTheme.typography.titleSmall)
                    Text(manufacturerTip(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { openAppDetails(context) }) { Text("Abrir ajustes de la app") }
                }
            }
            SectionLabel("Aplicación", Modifier.padding(top = 8.dp))
            val updateState by UpdateManager.state.collectAsStateWithLifecycle()
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Horarios ${UpdateManager.currentVersion(context)}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            when (val u = updateState) {
                                UpdateState.Checking -> "Buscando actualizaciones…"
                                UpdateState.UpToDate -> "Tienes la última versión ✓"
                                is UpdateState.Available -> "Versión ${u.info.versionName} disponible: mira la pantalla principal"
                                is UpdateState.Downloading -> "Descargando ${(u.progress * 100).toInt()} %…"
                                is UpdateState.Installing -> "Instalando…"
                                is UpdateState.Error -> u.message
                                UpdateState.Idle -> "Las actualizaciones llegan desde GitHub"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = {
                        scope.launch { UpdateManager.check(context.applicationContext, silent = false) }
                    }) { Text("Buscar") }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ToggleCard(title: String, text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
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

private fun formatTrigger(millis: Long): String {
    val dt = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val day = DAY_NAMES[dt.dayOfWeek.value - 1].lowercase()
    return "%s a las %02d:%02d".format(day, dt.hour, dt.minute)
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

private fun manufacturerTip(): String = when (Build.MANUFACTURER.lowercase()) {
    "xiaomi", "redmi", "poco" ->
        "En Xiaomi: Ajustes de la app › activa «Inicio automático» y en «Ahorro de batería» elige «Sin restricciones». " +
            "Activa también el «Modo alarma» de arriba."
    "samsung" ->
        "En Samsung: Ajustes de la app › Batería › «Sin restricciones». Revisa también que la app no esté en " +
            "«Aplicaciones en suspensión» (Ajustes › Mantenimiento › Batería › Límites de uso en segundo plano)."
    "huawei", "honor" ->
        "En Huawei/Honor: Ajustes › Batería › Inicio de aplicaciones › Horarios › «Gestionar manualmente» y activa las tres opciones. " +
            "Activa también el «Modo alarma»."
    "oppo", "realme", "oneplus", "vivo" ->
        "En Oppo/Realme/OnePlus/Vivo: Ajustes de la app › Uso de batería › permite «Actividad en segundo plano» e «Inicio automático». " +
            "Activa también el «Modo alarma»."
    else ->
        "Ajustes de la app › Batería › «Sin restricciones». Si aun así no llegan, activa el «Modo alarma»."
}
