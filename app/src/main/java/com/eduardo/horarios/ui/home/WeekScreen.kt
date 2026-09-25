package com.eduardo.horarios.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eduardo.horarios.R
import com.eduardo.horarios.data.PlannedActivity
import com.eduardo.horarios.durationLabel
import com.eduardo.horarios.ui.theme.DefaultStatusBarIcons
import java.time.LocalDate

/** Pestaña Semana: cuadrícula L–D con las horas. */
@Composable
fun WeekScreen(
    onOpenDay: () -> Unit,
    onAddActivity: (day: Int) -> Unit,
    onEditActivity: (id: Long) -> Unit,
    vm: PlanViewModel = viewModel(factory = PlanViewModel.Factory),
) {
    DefaultStatusBarIcons()
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val nowMinute = rememberNowMinute()
    val today = LocalDate.now()
    var sheetFor by remember { mutableStateOf<PlannedActivity?>(null) }

    val days = remember(state.activities, state.overrides, state.weekStart) {
        (0..6).map { d -> state.plan(state.weekStart.plusDays(d.toLong())).filter { !it.skipped } }
    }
    val totalMin = days.flatten().sumOf { it.end - it.start }
    val todayIndex = if (state.isCurrentWeek) today.dayOfWeek.value - 1 else null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            if (state.schedule != null) {
                ExtendedFloatingActionButton(
                    onClick = { onAddActivity(state.date.dayOfWeek.value - 1) },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(20.dp),
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                    Text(stringResource(R.string.view_week), style = MaterialTheme.typography.headlineSmall)
                    state.schedule?.let {
                        Text(
                            "${it.emoji} ${it.name}" +
                                if (totalMin > 0) " · " + stringResource(R.string.week_scheduled, durationLabel(context, totalMin)) else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                WeekNavigator(
                    weekStart = state.weekStart,
                    isCurrentWeek = state.isCurrentWeek,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            if (!state.loading && state.schedule == null) {
                item {
                    Text(
                        stringResource(R.string.no_schedules_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                    )
                }
            } else {
                item {
                    WeekGrid(
                        days = days,
                        today = todayIndex,
                        nowMinute = nowMinute,
                        onActivityClick = { sheetFor = it },
                        onDayClick = { d ->
                            DaySelection.select(state.weekStart.plusDays(d.toLong()))
                            onOpenDay()
                        },
                    )
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.week_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    sheetFor?.let { p ->
        val done = state.isDone(p)
        ActivitySheet(
            planned = p,
            done = done,
            canMarkDone = p.activity.tracked && !p.date.isAfter(today),
            onToggleDone = {
                vm.setDone(p, !done)
                sheetFor = null
            },
            onToggleSkip = {
                vm.setSkipped(p, !p.skipped)
                sheetFor = null
            },
            onEdit = {
                sheetFor = null
                onEditActivity(p.activity.id)
            },
            onDismiss = { sheetFor = null },
        )
    }
}
