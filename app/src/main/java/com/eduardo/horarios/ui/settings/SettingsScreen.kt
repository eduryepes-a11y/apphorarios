@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.eduardo.horarios.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardo.horarios.HorariosApp
import com.eduardo.horarios.BuildConfig
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.rounded.Build
import com.eduardo.horarios.R
import com.eduardo.horarios.alarm.Notifications
import com.eduardo.horarios.data.SettingsStore
import com.eduardo.horarios.data.ThemeMode
import com.eduardo.horarios.ui.backup.BackupIO
import com.eduardo.horarios.ui.backup.ImportController
import com.eduardo.horarios.ui.components.SectionLabel
import com.eduardo.horarios.ui.theme.Accents
import com.eduardo.horarios.ui.theme.DefaultStatusBarIcons
import com.eduardo.horarios.ui.theme.LocalDarkTheme
import com.eduardo.horarios.update.UpdateManager
import com.eduardo.horarios.update.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(onBack: (() -> Unit)?, onOpenNotifications: () -> Unit) {
    DefaultStatusBarIcons()
    val context = LocalContext.current
    val app = context.applicationContext as HorariosApp
    val scope = rememberCoroutineScope()

    val themeMode by app.settings.themeMode.collectAsStateWithLifecycle()
    val accent by app.settings.accent.collectAsStateWithLifecycle()
    var language by remember { mutableStateOf(app.settings.language) }
    val updateState by UpdateManager.state.collectAsStateWithLifecycle()
    var alarmMode by remember { mutableStateOf(app.scheduler.alarmClockMode) }
    var startAlerts by remember { mutableStateOf(app.scheduler.startAlerts) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            app.appScope.launch {
                val ok = try {
                    BackupIO.writeBackup(context, app.repository, uri)
                    true
                } catch (e: Exception) {
                    false
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(if (ok) R.string.backup_saved else R.string.backup_error),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) ImportController.pending.value = uri
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
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
            // ---------- Apariencia ----------
            SectionLabel(stringResource(R.string.settings_appearance), Modifier.padding(top = 4.dp))
            SettingsCard {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(4.dp)) {
                        ThemeSegment(stringResource(R.string.theme_system), Icons.Rounded.BrightnessAuto, themeMode == ThemeMode.SYSTEM, Modifier.weight(1f)) {
                            app.settings.setThemeMode(ThemeMode.SYSTEM)
                        }
                        ThemeSegment(stringResource(R.string.theme_light), Icons.Rounded.LightMode, themeMode == ThemeMode.LIGHT, Modifier.weight(1f)) {
                            app.settings.setThemeMode(ThemeMode.LIGHT)
                        }
                        ThemeSegment(stringResource(R.string.theme_dark), Icons.Rounded.DarkMode, themeMode == ThemeMode.DARK, Modifier.weight(1f)) {
                            app.settings.setThemeMode(ThemeMode.DARK)
                        }
                    }
                }
            }
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_accent), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(Accents.getOrElse(accent) { Accents[0] }.nameRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(12.dp))
                val dark = LocalDarkTheme.current
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Accents.forEachIndexed { index, a ->
                        val selected = index == accent
                        val c = if (dark) a.dark else a.light
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (selected) 3.dp else 0.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .clickable { app.settings.setAccent(index) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = stringResource(a.nameRes),
                                    tint = if (dark) Color.Black else Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ---------- Idioma ----------
            SectionLabel(stringResource(R.string.settings_language), Modifier.padding(top = 8.dp))
            SettingsCard(padding = 4) {
                SettingsStore.LANGUAGES.forEach { tag ->
                    val label = when (tag) {
                        "es" -> "Español"
                        "en" -> "English"
                        else -> stringResource(R.string.language_system)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                if (language != tag) {
                                    language = tag
                                    app.settings.setLanguage(tag)
                                    Notifications.createChannel(context.applicationContext)
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = language == tag, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 12.dp))
                    }
                }
            }

            // ---------- Copia de seguridad ----------
            SectionLabel(stringResource(R.string.settings_backup), Modifier.padding(top = 8.dp))
            SettingsCard {
                Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.backup_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { exportLauncher.launch(BackupIO.backupFileName()) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.backup_export)) }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.backup_import)) }
                }
            }

            // ---------- Avisos (lo que usa todo el mundo) ----------
            SectionLabel(stringResource(R.string.notifications_title), Modifier.padding(top = 8.dp))
            ToggleCard(
                title = stringResource(R.string.diag_start_alerts),
                text = stringResource(R.string.diag_start_alerts_text),
                checked = startAlerts,
                onChange = {
                    app.scheduler.startAlerts = it
                    startAlerts = it
                    scope.launch { app.scheduler.rescheduleAll() }
                },
            )
            ToggleCard(
                title = stringResource(R.string.diag_alarm_mode),
                text = stringResource(R.string.diag_alarm_mode_text),
                checked = alarmMode,
                onChange = {
                    app.scheduler.alarmClockMode = it
                    alarmMode = it
                    scope.launch { app.scheduler.rescheduleAll() }
                },
            )

            // ---------- Acerca de ----------
            SectionLabel(stringResource(R.string.settings_about), Modifier.padding(top = 8.dp))
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.app_version, UpdateManager.currentVersion(context)),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (BuildConfig.SELF_UPDATE) Text(
                            when (val u = updateState) {
                                UpdateState.Checking -> stringResource(R.string.update_checking)
                                UpdateState.UpToDate -> stringResource(R.string.update_up_to_date)
                                is UpdateState.Available -> stringResource(R.string.update_available_home, u.info.versionName)
                                is UpdateState.Downloading -> stringResource(R.string.update_downloading_pct, (u.progress * 100).toInt())
                                is UpdateState.ReadyToInstall -> stringResource(R.string.update_ready_title, u.info.versionName)
                                is UpdateState.Error -> stringResource(u.messageRes)
                                UpdateState.Idle -> stringResource(R.string.update_from_github)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (BuildConfig.SELF_UPDATE) TextButton(onClick = {
                        scope.launch { UpdateManager.check(context.applicationContext, silent = false) }
                    }) { Text(stringResource(R.string.update_check)) }
                }
            }
            // ---------- Avanzado: diagnóstico técnico ----------
            SectionLabel(stringResource(R.string.settings_advanced), Modifier.padding(top = 8.dp))
            Surface(
                onClick = onOpenNotifications,
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().testTag("open_diagnostics"),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.diag_entry_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.settings_notifications_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsCard(padding: Int = 16, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(padding.dp)) { content() }
    }
}

@Composable
private fun ThemeSegment(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        label = "themeSegment",
    )
    val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}
