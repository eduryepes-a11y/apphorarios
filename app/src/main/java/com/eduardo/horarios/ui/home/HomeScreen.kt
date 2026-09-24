@file:OptIn(ExperimentalComposeUiApi::class)

package com.eduardo.horarios.ui.home

import androidx.compose.ui.ExperimentalComposeUiApi
import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eduardo.horarios.R
import com.eduardo.horarios.dayName
import com.eduardo.horarios.dayShort
import com.eduardo.horarios.dateOfThisWeek
import androidx.compose.foundation.border
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import com.eduardo.horarios.HorariosApp
import com.eduardo.horarios.data.ActivityEntity
import com.eduardo.horarios.data.ScheduleEntity
import com.eduardo.horarios.durationLabel
import com.eduardo.horarios.hasDay
import com.eduardo.horarios.hm
import com.eduardo.horarios.nowMinuteOfDay
import com.eduardo.horarios.reminderShort
import com.eduardo.horarios.todayIndex
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


@Composable
fun HomeScreen(
    onOpenSchedules: () -> Unit,
    onAddActivity: (day: Int) -> Unit,
    onEditActivity: (id: Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
    vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    StatusBarIcons(darkIcons = false)
    val state by vm.state.collectAsStateWithLifecycle()
    var selectedDay by rememberSaveable { mutableIntStateOf(todayIndex()) }
    var weekMode by rememberSaveable { mutableStateOf(false) }

    // Reloj interno para "Ahora / Siguiente"
    val nowMinute by produceState(initialValue = nowMinuteOfDay()) {
        while (true) {
            delay(15_000)
            value = nowMinuteOfDay()
        }
    }
    val today = todayIndex()
    val todayActs = remember(state.activities, today) {
        state.activities.filter { it.daysMask.hasDay(today) }.sortedBy { it.startMinute }
    }
    val dayActs = remember(state.activities, selectedDay) {
        state.activities.filter { it.daysMask.hasDay(selectedDay) }.sortedBy { it.startMinute }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            if (state.schedule != null) {
                ExtendedFloatingActionButton(
                    onClick = { onAddActivity(selectedDay) },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.navigationBarsPadding(),
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item(key = "header") {
                Header(
                    schedule = state.schedule,
                    todayActs = todayActs,
                    nowMinute = nowMinute,
                    onOpenSchedules = onOpenSchedules,
                    onOpenSettings = onOpenSettings,
                    onOpenStats = onOpenStats,
                )
            }
            item(key = "banners") { PermissionBanners(vm) }
            item(key = "update") { UpdateCard() }

            if (!state.loading && state.schedule == null) {
                item(key = "noSchedule") { NoScheduleState(onOpenSchedules) }
                return@LazyColumn
            }

            item(key = "mode") {
                ViewModeToggle(weekMode = weekMode, onChange = { weekMode = it })
            }

            if (weekMode) {
                item(key = "weekTitle") { WeekTitle(state.activities) }
                item(key = "weekGrid") {
                    WeekGrid(
                        activities = state.activities,
                        today = today,
                        nowMinute = nowMinute,
                        onActivityClick = onEditActivity,
                        onDayClick = { d ->
                            selectedDay = d
                            weekMode = false
                        },
                    )
                }
            } else {
                item(key = "days") {
                    DaySelector(
                        selected = selectedDay,
                        today = today,
                        activities = state.activities,
                        onSelect = { selectedDay = it },
                    )
                }
                item(key = "dayTitle") {
                    DayTitle(day = selectedDay, isToday = selectedDay == today, acts = dayActs)
                }
                if (dayActs.isEmpty() && !state.loading) {
                    item(key = "empty") { EmptyDay() }
                }
                items(dayActs, key = { it.id }) { act ->
                    val selectedEpoch = dateOfThisWeek(selectedDay).toEpochDay()
                    val isDone = (act.id to selectedEpoch) in state.done
                    TimelineRow(
                        activity = act,
                        isToday = selectedDay == today,
                        nowMinute = nowMinute,
                        isLast = act == dayActs.last(),
                        done = isDone,
                        canMarkDone = selectedEpoch <= LocalDate.now().toEpochDay(),
                        onToggleDone = { vm.setDone(act.id, selectedEpoch, !isDone) },
                        onClick = { onEditActivity(act.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------
// Selector Día / Semana
// ------------------------------------------------------------------

@Composable
private fun ViewModeToggle(weekMode: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp),
    ) {
        Row(Modifier.padding(4.dp)) {
            ModeSegment(stringResource(R.string.view_day), Icons.Rounded.ViewDay, selected = !weekMode, modifier = Modifier.weight(1f)) { onChange(false) }
            ModeSegment(stringResource(R.string.view_week), Icons.Rounded.CalendarViewWeek, selected = weekMode, modifier = Modifier.weight(1f)) { onChange(true) }
        }
    }
}

@Composable
private fun ModeSegment(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        label = "segment",
    )
    val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
private fun WeekTitle(acts: List<ActivityEntity>) {
    val totalMin = acts.sumOf { a ->
        val days = (0..6).count { a.daysMask.hasDay(it) }
        (a.endMinute - a.startMinute).coerceAtLeast(0) * days
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(stringResource(R.string.your_week), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.weight(1f))
        if (acts.isNotEmpty()) {
            Text(
                stringResource(R.string.week_scheduled, durationLabel(LocalContext.current, totalMin)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ------------------------------------------------------------------
// Cabecera con degradado
// ------------------------------------------------------------------

@Composable
private fun Header(
    schedule: ScheduleEntity?,
    todayActs: List<ActivityEntity>,
    nowMinute: Int,
    onOpenSchedules: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
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
            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
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
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        dateText,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.8f),
                    )
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
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.change_schedule),
                            tint = Color.White,
                        )
                    }
                }
                HeaderButton(Icons.Rounded.Insights, stringResource(R.string.stats), onOpenStats)
                Spacer(Modifier.width(8.dp))
                HeaderButton(Icons.Rounded.CalendarMonth, stringResource(R.string.my_schedules), onOpenSchedules)
                Spacer(Modifier.width(8.dp))
                HeaderButton(Icons.Rounded.Settings, stringResource(R.string.settings), onOpenSettings)
            }
            Spacer(Modifier.height(18.dp))
            NowNextCard(schedule = schedule, todayActs = todayActs, nowMinute = nowMinute)
        }
    }
}

@Composable
private fun HeaderButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun NowNextCard(
    schedule: ScheduleEntity?,
    todayActs: List<ActivityEntity>,
    nowMinute: Int,
) {
    val context = LocalContext.current
    val current = todayActs.firstOrNull { nowMinute >= it.startMinute && nowMinute < it.endMinute }
    val next = todayActs.firstOrNull { it.startMinute > nowMinute }

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
                    val total = (current.endMinute - current.startMinute).coerceAtLeast(1)
                    val done = (nowMinute - current.startMinute).coerceIn(0, total)
                    GlassRow(
                        emoji = current.emoji,
                        label = stringResource(R.string.now_label),
                        title = current.title,
                        subtitle = stringResource(
                            R.string.now_subtitle,
                            "${hm(current.startMinute)} – ${hm(current.endMinute)}",
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
                            stringResource(R.string.after_next, "${next.emoji} ${next.title}", hm(next.startMinute)),
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                next != null -> GlassRow(
                    emoji = next.emoji,
                    label = stringResource(R.string.next_label),
                    title = next.title,
                    subtitle = stringResource(
                        R.string.next_subtitle,
                        hm(next.startMinute),
                        durationLabel(context, next.startMinute - nowMinute),
                    ),
                )

                todayActs.isEmpty() -> GlassRow(
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
// Avisos de permisos
// ------------------------------------------------------------------

@Composable
private fun PermissionBanners(vm: HomeViewModel) {
    val context = LocalContext.current
    var notificationsOn by remember { mutableStateOf(areNotificationsOn(context)) }
    var exactOn by remember { mutableStateOf(canExact(context)) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsOn = areNotificationsOn(context)
    }

    LaunchedEffect(Unit) {
        if (!notificationsOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
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
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    context.startActivity(intent)
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
private fun Banner(
    icon: ImageVector,
    title: String,
    text: String,
    action: String,
    onAction: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
// Selector de días
// ------------------------------------------------------------------

@Composable
private fun DaySelector(
    selected: Int,
    today: Int,
    activities: List<ActivityEntity>,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (d in 0..6) {
            val isSel = d == selected
            val isToday = d == today
            val hasItems = activities.any { it.daysMask.hasDay(d) }
            val bg by animateColorAsState(
                if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                label = "dayBg",
            )
            val fg = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            Surface(
                onClick = { onSelect(d) },
                color = bg,
                shape = RoundedCornerShape(18.dp),
                shadowElevation = if (isSel) 8.dp else 0.dp,
                border = if (isToday && !isSel) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(dayShort(LocalContext.current, d), style = MaterialTheme.typography.titleMedium, color = fg)
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .size(6.dp)
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

@Composable
private fun DayTitle(day: Int, isToday: Boolean, acts: List<ActivityEntity>) {
    val totalMin = acts.sumOf { (it.endMinute - it.startMinute).coerceAtLeast(0) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(dayName(LocalContext.current, day), style = MaterialTheme.typography.titleLarge)
        if (isToday) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.padding(bottom = 4.dp)) { Pill(stringResource(R.string.today_badge), MaterialTheme.colorScheme.primary) }
        }
        Spacer(Modifier.weight(1f))
        if (acts.isNotEmpty()) {
            Text(
                pluralStringResource(R.plurals.activities_count, acts.size, acts.size) + " · " + durationLabel(LocalContext.current, totalMin),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ------------------------------------------------------------------
// Línea de tiempo
// ------------------------------------------------------------------

@Composable
private fun TimelineRow(
    activity: ActivityEntity,
    isToday: Boolean,
    nowMinute: Int,
    isLast: Boolean,
    done: Boolean,
    canMarkDone: Boolean,
    onToggleDone: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = paletteColor(activity.colorIndex)
    val isNow = isToday && nowMinute >= activity.startMinute && nowMinute < activity.endMinute
    val isPast = isToday && nowMinute >= activity.endMinute

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 16.dp)
            .alpha(if (isPast && !done) 0.5f else 1f),
    ) {
        Column(
            modifier = Modifier
                .width(48.dp)
                .padding(top = 14.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(hm(activity.startMinute), style = MaterialTheme.typography.labelLarge)
            Text(
                hm(activity.endMinute),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Punto + línea vertical
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
            activity = activity,
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
    activity: ActivityEntity,
    color: Color,
    isNow: Boolean,
    done: Boolean,
    canMarkDone: Boolean,
    onToggleDone: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (isNow) 10.dp else 1.dp,
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
                            textDecoration = if (done) TextDecoration.LineThrough else null,
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
                    durationLabel(LocalContext.current, activity.endMinute - activity.startMinute),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (activity.notes.isNotBlank()) {
                    Text(
                        activity.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            val hasReminder = activity.reminderMinutes >= 0
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (hasReminder) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                    contentDescription = null,
                    tint = if (hasReminder) color else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp),
                )
                if (hasReminder) {
                    Text(
                        reminderShort(LocalContext.current, activity.reminderMinutes),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (canMarkDone) {
                Spacer(Modifier.width(10.dp))
                DoneButton(done = done, color = color, onClick = onToggleDone)
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
private fun EmptyDay() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🌤️", fontSize = 56.sp)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.day_free), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.empty_day_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
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
