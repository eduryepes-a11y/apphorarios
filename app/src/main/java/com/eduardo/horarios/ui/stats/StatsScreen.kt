@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)

package com.eduardo.horarios.ui.stats

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eduardo.horarios.R
import com.eduardo.horarios.data.DayCell
import com.eduardo.horarios.data.HabitStat
import com.eduardo.horarios.data.StatsResult
import com.eduardo.horarios.data.WeekHours
import com.eduardo.horarios.dayShort
import com.eduardo.horarios.durationLabel
import com.eduardo.horarios.ui.components.EmojiBubble
import com.eduardo.horarios.ui.components.SectionLabel
import com.eduardo.horarios.ui.theme.DefaultStatusBarIcons
import com.eduardo.horarios.ui.theme.paletteColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Composable
fun StatsScreen(
    onBack: (() -> Unit)?,
    vm: StatsViewModel = viewModel(factory = StatsViewModel.Factory),
) {
    DefaultStatusBarIcons()
    val state by vm.state.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Exportar: el usuario elige dónde guardar el CSV
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    val csv = vm.exportCsv() ?: return@withContext false
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
                    }.isSuccess
                }
                Toast.makeText(context, if (ok) R.string.stats_export_done else R.string.stats_export_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats)) },
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
            val result = state.result
            if (!state.loading && (state.schedule == null || result == null)) {
                Text(
                    stringResource(R.string.stats_no_schedule),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
                return@Column
            }
            if (result == null) return@Column

            state.schedule?.let {
                Text(
                    "${it.emoji} ${it.name}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            // ---------- Tu semana en horas (no hace falta marcar nada) ----------
            SectionLabel(stringResource(R.string.stats_week_hours), Modifier.padding(top = 4.dp))
            WeekHoursCard(result.weekHours)

            // ---------- Periodo ----------
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Row(Modifier.padding(4.dp)) {
                    PeriodSegment(stringResource(R.string.stats_7_days), period == 7, Modifier.weight(1f).testTag("period_7")) { vm.period.value = 7 }
                    PeriodSegment(stringResource(R.string.stats_28_days), period == 28, Modifier.weight(1f).testTag("period_28")) { vm.period.value = 28 }
                    PeriodSegment(stringResource(R.string.stats_90_days), period == 90, Modifier.weight(1f).testTag("period_90")) { vm.period.value = 90 }
                }
            }

            // ---------- Lo que pasó ----------
            SectionLabel(stringResource(R.string.stats_happened), Modifier.padding(top = 4.dp))
            val h = result.happened
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    value = h.delayDays.toString(),
                    label = stringResource(R.string.stats_delays),
                    sub = if (h.delayDays == 0) stringResource(R.string.stats_no_delays)
                    else stringResource(R.string.stats_delays_avg, durationLabel(context, h.averageDelay)),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = h.skipped.toString(),
                    label = stringResource(R.string.stats_skipped),
                    sub = pluralStringResource(R.plurals.stats_last_days, result.periodDays, result.periodDays),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = h.daysOff.toString(),
                    label = stringResource(R.string.stats_days_off),
                    sub = pluralStringResource(R.plurals.stats_last_days, result.periodDays, result.periodDays),
                    modifier = Modifier.weight(1f),
                )
            }

            // ---------- Constancia (solo lo que se sigue) ----------
            SectionLabel(stringResource(R.string.stats_consistency), Modifier.padding(top = 4.dp))
            if (!state.anyTracked) {
                InfoCard(stringResource(R.string.stats_no_activities))
            } else {
                ConsistencyCard(result, empty = !state.anyCompletion)

                // ---------- Tus hábitos ----------
                if (result.habits.isNotEmpty()) {
                    SectionLabel(stringResource(R.string.stats_habits), Modifier.padding(top = 4.dp))
                    Card {
                        result.habits.forEachIndexed { i, habit ->
                            if (i > 0) Spacer(Modifier.height(16.dp))
                            HabitRow(habit)
                        }
                    }
                }
            }

            // ---------- Exportar ----------
            OutlinedButton(
                onClick = { exportLauncher.launch("horarios-${java.time.LocalDate.now()}.csv") },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .testTag("export_csv"),
            ) {
                Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.stats_export))
            }
            Text(
                stringResource(R.string.stats_export_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ------------------------------------------------------------------
// Tu semana en horas
// ------------------------------------------------------------------

@Composable
private fun WeekHoursCard(w: WeekHours) {
    val context = LocalContext.current
    Card {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                durationLabel(context, w.totalMinutes),
                fontSize = 28.sp,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("week_total"),
            )
        }
        val delta = w.deltaMinutes
        Text(
            when {
                w.previousMinutes == 0 && w.totalMinutes == 0 -> ""
                delta == 0 -> stringResource(R.string.stats_vs_last_week_same)
                delta > 0 -> stringResource(R.string.stats_vs_last_week_more, durationLabel(context, delta))
                else -> stringResource(R.string.stats_vs_last_week_less, durationLabel(context, abs(delta)))
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (w.items.isEmpty()) return@Card
        Spacer(Modifier.height(12.dp))

        // Barra apilada con el reparto
        val top = w.items.take(5)
        val restMinutes = w.items.drop(5).sumOf { it.minutes }
        val total = w.totalMinutes.coerceAtLeast(1)
        Row(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp)),
        ) {
            top.forEach { item ->
                Box(
                    Modifier
                        .weight(item.minutes.toFloat() / total)
                        .fillMaxSize()
                        .background(paletteColor(item.colorIndex)),
                )
            }
            if (restMinutes > 0) {
                Box(
                    Modifier
                        .weight(restMinutes.toFloat() / total)
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        top.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(paletteColor(item.colorIndex)),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${item.emoji} ${item.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    durationLabel(context, item.minutes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (restMinutes > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.stats_other), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    durationLabel(context, restMinutes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ------------------------------------------------------------------
// Constancia: porcentaje + calendario de colores
// ------------------------------------------------------------------

@Composable
private fun ConsistencyCard(r: StatsResult, empty: Boolean) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    r.percent?.let { "$it %" } ?: "—",
                    fontSize = 28.sp,
                    style = MaterialTheme.typography.headlineSmall,
                    color = primary,
                )
                Text(
                    stringResource(R.string.stats_completion) + " · " + stringResource(R.string.stats_done_of, r.done, r.planned),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        // Sin nada marcado aún: un mensaje de ánimo en vez de un calendario gris
        if (empty) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                    .padding(vertical = 20.dp, horizontal = 16.dp)
                    .testTag("consistency_empty"),
            ) {
                Text("🌱", fontSize = 34.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.stats_empty_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.stats_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                )
            }
            return@Card
        }

        // 7 filas (L…D) × 12 semanas
        val weeks = r.heatmap.chunked(7)
        Row(Modifier.fillMaxWidth().testTag("heatmap"), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (d in 0..6) {
                    Box(Modifier.height(18.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (d % 2 == 0) dayShort(context, d) else "",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(14.dp),
                        )
                    }
                }
            }
            weeks.forEach { week ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    week.forEach { cell -> HeatCell(cell, primary) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.stats_less), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            listOf(0f, 0.34f, 0.67f, 1f).forEach { f ->
                Box(
                    Modifier
                        .padding(horizontal = 1.dp)
                        .size(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (f == 0f) MaterialTheme.colorScheme.surfaceVariant else primary.copy(alpha = 0.25f + 0.75f * f)),
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.stats_more), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HeatCell(cell: DayCell, primary: Color) {
    val color = when {
        cell.future -> Color.Transparent
        cell.planned == 0 || cell.done == 0 -> MaterialTheme.colorScheme.surfaceVariant
        else -> primary.copy(alpha = 0.25f + 0.75f * cell.ratio)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(18.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .then(
                if (cell.future) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                else Modifier
            ),
    )
}

// ------------------------------------------------------------------
// Hábitos
// ------------------------------------------------------------------

@Composable
private fun HabitRow(h: HabitStat) {
    val color = paletteColor(h.activity.colorIndex)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("habit_row")) {
        EmojiBubble(emoji = h.activity.emoji, color = color, size = 40.dp, corner = 12.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    h.activity.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (h.streakWeeks > 0) {
                    Text(
                        pluralStringResource(R.plurals.stats_streak_weeks, h.streakWeeks, h.streakWeeks),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            val goal = h.weeklyGoal.coerceAtLeast(1)
            LinearProgressIndicator(
                progress = { (h.weekDone.toFloat() / goal).coerceAtMost(1f) },
                color = color,
                trackColor = color.copy(alpha = 0.18f),
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.stats_habit_week, h.weekDone, h.weekPlanned),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ------------------------------------------------------------------
// Piezas comunes
// ------------------------------------------------------------------

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun InfoCard(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(14.dp),
        )
    }
}

@Composable
private fun PeriodSegment(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        label = "periodBg",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatTile(value: String, label: String, sub: String, modifier: Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            Text(
                value,
                fontSize = 22.sp,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
