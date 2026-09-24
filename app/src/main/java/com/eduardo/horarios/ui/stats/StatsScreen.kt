@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)

package com.eduardo.horarios.ui.stats

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eduardo.horarios.R
import com.eduardo.horarios.dayShort
import com.eduardo.horarios.durationLabel
import com.eduardo.horarios.ui.components.EmojiBubble
import com.eduardo.horarios.ui.components.SectionLabel
import com.eduardo.horarios.ui.theme.DefaultStatusBarIcons
import com.eduardo.horarios.ui.theme.paletteColor

@Composable
fun StatsScreen(
    onBack: (() -> Unit)?,
    vm: StatsViewModel = viewModel(factory = StatsViewModel.Factory),
) {
    DefaultStatusBarIcons()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
            if (!state.loading && state.schedule == null) {
                Text(
                    stringResource(R.string.stats_no_schedule),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
                return@Column
            }

            state.schedule?.let {
                Text(
                    "${it.emoji} ${it.name}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            // Periodo
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(4.dp)) {
                    PeriodSegment(stringResource(R.string.stats_7_days), state.periodDays == 7, Modifier.weight(1f)) { vm.period.value = 7 }
                    PeriodSegment(stringResource(R.string.stats_28_days), state.periodDays == 28, Modifier.weight(1f)) { vm.period.value = 28 }
                }
            }

            // Tarjetas resumen
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    value = state.percent?.let { "$it %" } ?: "—",
                    label = stringResource(R.string.stats_completion),
                    sub = stringResource(R.string.stats_done_of, state.done, state.planned),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = "🔥 ${state.streak}",
                    label = stringResource(R.string.stats_streak),
                    sub = pluralStringResource(R.plurals.days_count, state.streak, state.streak),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = durationLabel(context, state.weeklyMinutes),
                    label = stringResource(R.string.stats_weekly_hours),
                    sub = stringResource(R.string.stats_scheduled),
                    modifier = Modifier.weight(1f),
                    small = true,
                )
            }

            if (!state.anyCompletion) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.stats_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            // Esta semana
            SectionLabel(stringResource(R.string.stats_this_week), Modifier.padding(top = 8.dp))
            Card {
                WeekChart(state.week)
            }

            // Por actividad
            SectionLabel(stringResource(R.string.stats_by_activity), Modifier.padding(top = 8.dp))
            Card {
                if (state.perActivity.isEmpty()) {
                    Text(
                        stringResource(R.string.stats_no_activities),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.perActivity.forEachIndexed { i, s ->
                    if (i > 0) Spacer(Modifier.height(14.dp))
                    ActivityStatRow(s)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
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
        )
    }
}

@Composable
private fun StatTile(value: String, label: String, sub: String, modifier: Modifier, small: Boolean = false) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            Text(
                value,
                fontSize = if (small) 18.sp else 22.sp,
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Barras por día: fondo = actividades del día, relleno = hechas. */
@Composable
private fun WeekChart(week: List<DayStat>) {
    val context = LocalContext.current
    val maxPlanned = (week.maxOfOrNull { it.planned } ?: 0).coerceAtLeast(1)
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        week.forEach { d ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (d.isFuture || d.planned == 0) "" else "${d.done}/${d.planned}",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    val trackFraction by animateFloatAsState(d.planned.toFloat() / maxPlanned, label = "track")
                    val doneFraction by animateFloatAsState(d.done.toFloat() / maxPlanned, label = "done")
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(trackFraction.coerceAtLeast(0.02f))
                            .clip(RoundedCornerShape(10.dp))
                            .background(primary.copy(alpha = if (d.isFuture) 0.08f else 0.18f))
                    )
                    if (doneFraction > 0f) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(doneFraction)
                                .clip(RoundedCornerShape(10.dp))
                                .background(primary)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    dayShort(context, d.day),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (d.isToday) primary else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ActivityStatRow(s: ActivityStat) {
    val context = LocalContext.current
    val color = paletteColor(s.activity.colorIndex)
    Row(verticalAlignment = Alignment.CenterVertically) {
        EmojiBubble(emoji = s.activity.emoji, color = color, size = 40.dp, corner = 12.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.activity.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.stats_done_of, s.done, s.planned),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { if (s.planned == 0) 0f else s.done.toFloat() / s.planned },
                color = color,
                trackColor = color.copy(alpha = 0.18f),
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.stats_per_week, durationLabel(context, s.weeklyMinutes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
