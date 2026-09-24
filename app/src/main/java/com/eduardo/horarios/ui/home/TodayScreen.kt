@file:OptIn(ExperimentalComposeUiApi::class)

package com.eduardo.horarios.ui.home

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eduardo.horarios.BuildConfig
import com.eduardo.horarios.HorariosApp
import com.eduardo.horarios.R
import com.eduardo.horarios.data.PlannedActivity
import com.eduardo.horarios.data.ScheduleEntity
import com.eduardo.horarios.dayName
import com.eduardo.horarios.dayShort
import com.eduardo.horarios.durationLabel
import com.eduardo.horarios.hm
import com.eduardo.horarios.nowMinuteOfDay
import com.eduardo.horarios.reminderShort
import com.eduardo.horarios.ui.components.EmojiBubble
import com.eduardo.horarios.ui.components.Pill
import com.eduardo.horarios.ui.theme.StatusBarIcons
import com.eduardo.horarios.ui.theme.headerBrush
import com.eduardo.horarios.ui.theme.paletteColor
import com.eduardo.horarios.update.UpdateCard
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Reloj interno (minuto del día) que se actualiza solo. */
@Composable
fun rememberNowMinute(): Int {
    val now by produceState(initialValue = nowMinuteOfDay()) {
        while (true) {
            delay(15_000)
            value = nowMinuteOfDay()
        }
    }
    return now
}

@Composable
fun TodayScreen(
    onOpenSchedules: () -> Unit,
    onAddActivity: (day: Int) -> Unit,
    onEditActivity: (id: Long) -> Unit,
    onBulkAdd: (day: Int) -> Unit,
    vm: PlanViewModel = viewModel(factory = PlanViewModel.Factory),
) {
    StatusBarIcons(darkIcons = false)
    val state by vm.state.collectAsStateWithLifecycle()
    val nowMinute = rememberNowMinute()
    val today = LocalDate.now()
    val date = state.date
    val dayIndex = date.dayOfWeek.value - 1

    val todayPlan = remember(state.activities, state.overrides, today) {
        state.plan(today).filter { !it.skipped }
    }
    val dayPlan = remember(state.activities, state.overrides, date) { state.plan(date) }
    val dayOverrides = state.dayOverrides(date)

    var sheetFor by remember { mutableStateOf<PlannedActivity?>(null) }
    var showDelay by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            if (state.schedule != null) {
                ExtendedFloatingActionButton(
                    onClick = { onAddActivity(dayIndex) },
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
                .padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item(key = "header") {
                Header(
                    schedule = state.schedule,
                    todayPlan = todayPlan,
                    nowMinute = nowMinute,
                    todayDelay = state.dayOverrides(today).totalShift,
                    onOpenSchedules = onOpenSchedules,
                    onDelay = { showDelay = true },
                )
            }
            item(key = "banners") { PermissionBanners(vm) }
            if (BuildConfig.SELF_UPDATE) {
                item(key = "update") { UpdateCard() }
            }

            if (!state.loading && state.schedule == null) {
                item(key = "noSchedule") { NoScheduleState(onOpenSchedules) }
                return@LazyColumn
            }

            item(key = "days") {
                WeekDaySelector(
                    state = state,
                    onSelect = { DaySelection.select(it) },
                )
            }
            item(key = "dayTitle") {
                DayTitle(
                    date = date,
                    plan = dayPlan,
                    dayOff = dayOverrides.dayOff,
                    delay = dayOverrides.totalShift,
                    onToggleDayOff = { vm.setDayOff(date, !dayOverrides.dayOff) },
                    onResetDelay = { vm.resetDelays(date) },
                )
            }
            if (dayOverrides.dayOff) {
                item(key = "dayOff") { DayOffCard(onUndo = { vm.setDayOff(date, false) }) }
            }
            if (dayPlan.isEmpty() && !state.loading) {
                item(key = "empty") { EmptyDay(onBulkAdd = { onBulkAdd(dayIndex) }) }
            }
            items(dayPlan, key = { it.activity.id }) { p ->
                TimelineRow(
                    planned = p,
                    isToday = date == today,
                    nowMinute = nowMinute,
                    isLast = p == dayPlan.last(),
                    done = state.isDone(p),
                    canMarkDone = !date.isAfter(today) && !p.skipped,
                    onToggleDone = { vm.setDone(p, !state.isDone(p)) },
                    onClick = { sheetFor = p },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    sheetFor?.let { p ->
        val done = state.isDone(p)
        ActivitySheet(
            planned = p,
            done = done,
            canMarkDone = !p.date.isAfter(today),
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
    if (showDelay) {
        DelaySheet(
            currentDelay = state.dayOverrides(today).totalShift,
            onDelay = {
                vm.delayToday(it)
                showDelay = false
            },
            onReset = {
                vm.resetDelays(today)
                showDelay = false
            },
            onDismiss = { showDelay = false },
        )
    }
}

// ------------------------------------------------------------------
// Cabecera con degradado
// ------------------------------------------------------------------

@Composable
private fun Header(
    schedule: ScheduleEntity?,
    todayPlan: List<PlannedActivity>,
    nowMinute: Int,
    todayDelay: Int,
    onOpenSchedules: () -> Unit,
    onDelay: () -> Unit,
) {
    val base = schedule?.let { paletteColor(it.colorIndex) } ?: MaterialTheme.colorScheme.primary
    val animatedBase by animateColorAsState(base, label = "headerColor")
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val pattern = stringResource(R.string.date_header_pattern)
    val dateText = remember(locale, pattern) {
        LocalDate.now()
            .format(DateTimeFormatter.ofPattern(pattern, locale))
            .replaceFirstChar { it.titlecase(locale) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(headerBrush(animatedBase))
            .drawBehind {
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = size.width * 0.38f,
                    center = Offset(size.width * 0.98f, size.height * 0.05f),
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.06f),
                    radius = size.width * 0.28f,
                    center = Offset(size.width * 0.05f, size.height * 1.05f),
                )
            }
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
    ) {
        Column {
            Text(dateText, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenSchedules)
                    .padding(vertical = 2.dp),
            ) {
                Text(
                    text = schedule?.let { "${it.emoji}  ${it.name}" } ?: stringResource(R.string.no_schedule),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.change_schedule), tint = Color.White)
            }
            Spacer(Modifier.height(16.dp))
            NowNextCard(schedule, todayPlan, nowMinute, todayDelay, onDelay)
        }
    }
}

@Composable
private fun NowNextCard(
    schedule: ScheduleEntity?,
    todayPlan: List<PlannedActivity>,
    nowMinute: Int,
    todayDelay: Int,
    onDelay: () -> Unit,
) {
    val context = LocalContext.current
    val current = todayPlan.firstOrNull { nowMinute >= it.start && nowMinute < it.end }
    val next = todayPlan.firstOrNull { it.start > nowMinute }

    Surface(
        color = Color.White.copy(alpha = 0.16f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            when {
                schedule == null -> GlassRow(
                    emoji = "🗓️",
                    label = stringResource(R.string.start_here),
                    title = stringResource(R.string.create_first_schedule),
                    subtitle = stringResource(R.string.create_first_schedule_sub),
                )

                current != null -> {
                    val total = (current.end - current.start).coerceAtLeast(1)
                    val done = (nowMinute - current.start).coerceIn(0, total)
                    GlassRow(
                        emoji = current.activity.emoji,
                        label = stringResource(R.string.now_label),
                        title = current.activity.title,
                        subtitle = stringResource(
                            R.string.now_subtitle,
                            "${hm(current.start)} – ${hm(current.end)}",
                            durationLabel(context, total - done),
                        ),
                    )
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { done.toFloat() / total },
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                    )
                    if (next != null) {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.after_next, "${next.activity.emoji} ${next.activity.title}", hm(next.start)),
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                next != null -> GlassRow(
                    emoji = next.activity.emoji,
                    label = stringResource(R.string.next_label),
                    title = next.activity.title,
                    subtitle = stringResource(
                        R.string.next_subtitle,
                        hm(next.start),
                        durationLabel(context, next.start - nowMinute),
                    ),
                )

                todayPlan.isEmpty() -> GlassRow(
                    emoji = "🌤️",
                    label = stringResource(R.string.today_label),
                    title = stringResource(R.string.day_free),
                    subtitle = stringResource(R.string.day_free_sub),
                )

                else -> GlassRow(
                    emoji = "🎉",
                    label = stringResource(R.string.today_label),
                    title = stringResource(R.string.day_completed),
                    subtitle = stringResource(R.string.day_completed_sub),
                )
            }

            // «Voy con retraso»: solo si aún queda algo por empezar hoy
            if (next != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.14f))
                        .clickable(onClick = onDelay)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Timer, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (todayDelay > 0) {
                            stringResource(R.string.delayed_by, durationLabel(context, todayDelay))
                        } else {
                            stringResource(R.string.running_late)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassRow(emoji: String, label: String, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        EmojiBubble(emoji = emoji, color = Color.White, size = 52.dp, corner = 18.dp, alpha = 0.22f)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
        }
    }
}

// ------------------------------------------------------------------
// Avisos de permisos (solo si falta algo; sin pedir nada automáticamente)
// ------------------------------------------------------------------

@Composable
private fun PermissionBanners(vm: PlanViewModel) {
    val context = LocalContext.current
    var notificationsOn by remember { mutableStateOf(areNotificationsOn(context)) }
    var exactOn by remember { mutableStateOf(canExact(context)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsOn = areNotificationsOn(context)
        val newExact = canExact(context)
        if (newExact && !exactOn) vm.refreshAlarms(context.applicationContext as HorariosApp)
        exactOn = newExact
    }

    Column(Modifier.padding(horizontal = 16.dp)) {
        AnimatedVisibility(!notificationsOn) {
            Banner(
                icon = Icons.Rounded.NotificationsOff,
                title = stringResource(R.string.banner_notif_title),
                text = stringResource(R.string.banner_notif_text),
                action = stringResource(R.string.enable),
                onAction = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    )
                },
            )
        }
        AnimatedVisibility(notificationsOn && !exactOn) {
            Banner(
                icon = Icons.Rounded.AlarmOn,
                title = stringResource(R.string.banner_exact_title),
                text = stringResource(R.string.banner_exact_text),
                action = stringResource(R.string.allow),
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                        )
                    }
                },
            )
        }
    }
}

private fun areNotificationsOn(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

private fun canExact(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

@Composable
private fun Banner(icon: ImageVector, title: String, text: String, action: String, onAction: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

// ------------------------------------------------------------------
// Selector de semana y días
// ------------------------------------------------------------------

/** «22 – 28 sep» */
@Composable
fun weekRangeLabel(weekStart: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val end = weekStart.plusDays(6)
    val month = DateTimeFormatter.ofPattern("d MMM", locale)
    return if (weekStart.month == end.month) {
        "${weekStart.dayOfMonth} – ${end.format(month)}"
    } else {
        "${weekStart.format(month)} – ${end.format(month)}"
    }
}

/** Flechas para cambiar de semana + botón «Hoy» si no estás en la semana actual. */
@Composable
fun WeekNavigator(weekStart: LocalDate, isCurrentWeek: Boolean, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { DaySelection.moveWeeks(-1) }) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous_week))
        }
        Text(
            if (isCurrentWeek) stringResource(R.string.this_week) else weekRangeLabel(weekStart),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = { DaySelection.moveWeeks(1) }) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = stringResource(R.string.next_week))
        }
        AnimatedVisibility(!isCurrentWeek) {
            TextButton(onClick = { DaySelection.goToday() }) { Text(stringResource(R.string.today)) }
        }
    }
}

@Composable
private fun WeekDaySelector(state: PlanState, onSelect: (LocalDate) -> Unit) {
    val context = LocalContext.current
    val today = LocalDate.now()
    Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp)) {
        WeekNavigator(state.weekStart, state.isCurrentWeek)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (d in 0..6) {
                val date = state.weekStart.plusDays(d.toLong())
                val isSel = date == state.date
                val isToday = date == today
                val plan = state.plan(date)
                val hasItems = plan.any { !it.skipped }
                val bg by animateColorAsState(
                    if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    label = "dayBg",
                )
                val fg = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                Surface(
                    onClick = { onSelect(date) },
                    color = bg,
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = if (isSel) 6.dp else 0.dp,
                    border = if (isToday && !isSel) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier
                        .weight(1f)
                        .height(66.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(dayShort(context, d), style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.8f))
                        Text("${date.dayOfMonth}", style = MaterialTheme.typography.titleMedium, color = fg)
                        Spacer(Modifier.height(3.dp))
                        Box(
                            Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        !hasItems -> Color.Transparent
                                        isSel -> MaterialTheme.colorScheme.onPrimary
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayTitle(
    date: LocalDate,
    plan: List<PlannedActivity>,
    dayOff: Boolean,
    delay: Int,
    onToggleDayOff: () -> Unit,
    onResetDelay: () -> Unit,
) {
    val context = LocalContext.current
    val active = plan.filter { !it.skipped }
    val totalMin = active.sumOf { (it.end - it.start).coerceAtLeast(0) }
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dayName(context, date.dayOfWeek.value - 1), style = MaterialTheme.typography.titleLarge)
                if (date == LocalDate.now()) {
                    Spacer(Modifier.width(8.dp))
                    Pill(stringResource(R.string.today_badge), MaterialTheme.colorScheme.primary)
                }
            }
            if (active.isNotEmpty()) {
                Text(
                    pluralStringResource(R.plurals.activities_count, active.size, active.size) + " · " + durationLabel(context, totalMin),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.options))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (dayOff) R.string.remove_day_off else R.string.mark_day_off)) },
                    onClick = {
                        menu = false
                        onToggleDayOff()
                    },
                )
                if (delay > 0) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delay_reset)) },
                        onClick = {
                            menu = false
                            onResetDelay()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayOffCard(onUndo: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🌴", fontSize = 22.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.day_off_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.day_off_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            TextButton(onClick = onUndo) { Text(stringResource(R.string.undo)) }
        }
    }
}

// ------------------------------------------------------------------
// Línea de tiempo
// ------------------------------------------------------------------

@Composable
private fun TimelineRow(
    planned: PlannedActivity,
    isToday: Boolean,
    nowMinute: Int,
    isLast: Boolean,
    done: Boolean,
    canMarkDone: Boolean,
    onToggleDone: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = paletteColor(planned.activity.colorIndex)
    val isNow = isToday && !planned.skipped && nowMinute >= planned.start && nowMinute < planned.end
    val isPast = isToday && nowMinute >= planned.end

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 16.dp)
            .alpha(if (planned.skipped) 0.45f else if (isPast && !done) 0.55f else 1f),
    ) {
        Column(
            modifier = Modifier
                .width(48.dp)
                .padding(top = 14.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(hm(planned.start), style = MaterialTheme.typography.labelLarge)
            Text(
                hm(planned.end),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .width(28.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (!isLast) {
                Box(
                    Modifier
                        .padding(top = 20.dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
            Box(
                Modifier
                    .padding(top = 16.dp)
                    .size(if (isNow) 14.dp else 10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        ActivityCard(
            planned = planned,
            color = color,
            isNow = isNow,
            done = done,
            canMarkDone = canMarkDone,
            onToggleDone = onToggleDone,
            onClick = onClick,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp),
        )
    }
}

@Composable
private fun ActivityCard(
    planned: PlannedActivity,
    color: Color,
    isNow: Boolean,
    done: Boolean,
    canMarkDone: Boolean,
    onToggleDone: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = planned.activity
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (isNow) 8.dp else 1.dp,
        border = if (isNow) BorderStroke(2.dp, color) else null,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .background(color.copy(alpha = if (isNow) 0.16f else 0.09f))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EmojiBubble(emoji = activity.emoji, color = color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        activity.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            textDecoration = if (done || planned.skipped) TextDecoration.LineThrough else null,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isNow) {
                        Spacer(Modifier.width(8.dp))
                        Pill(stringResource(R.string.now_badge), color)
                    }
                }
                Text(
                    when {
                        planned.skipped -> stringResource(R.string.skipped_this_day)
                        planned.shift != 0 -> durationLabel(context, planned.end - planned.start) + " · " +
                            stringResource(R.string.delayed_by, durationLabel(context, planned.shift))
                        else -> durationLabel(context, planned.end - planned.start)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (planned.shift != 0 && !planned.skipped) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (activity.notes.isNotBlank() && !planned.skipped) {
                    Text(
                        activity.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (canMarkDone) {
                Spacer(Modifier.width(10.dp))
                DoneButton(done = done, color = color, onClick = onToggleDone)
            } else if (!planned.skipped) {
                val hasReminder = activity.reminderMinutes >= 0
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (hasReminder) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                        contentDescription = null,
                        tint = if (hasReminder) color else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp),
                    )
                    if (hasReminder) {
                        Text(
                            reminderShort(context, activity.reminderMinutes),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DoneButton(done: Boolean, color: Color, onClick: () -> Unit) {
    val bg by animateColorAsState(if (done) color else Color.Transparent, label = "doneBg")
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(bg)
            .border(2.dp, if (done) color else color.copy(alpha = 0.55f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = stringResource(R.string.mark_not_done),
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ------------------------------------------------------------------
// Estados vacíos
// ------------------------------------------------------------------

@Composable
private fun EmptyDay(onBulkAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🌤️", fontSize = 52.sp)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.day_free), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.empty_day_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onBulkAdd) { Text(stringResource(R.string.bulk_add_link)) }
    }
}

@Composable
private fun NoScheduleState(onOpenSchedules: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🗓️", fontSize = 64.sp)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.no_schedules_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.no_schedules_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onOpenSchedules, shape = RoundedCornerShape(16.dp)) {
            Text(stringResource(R.string.create_first_schedule))
        }
    }
}
