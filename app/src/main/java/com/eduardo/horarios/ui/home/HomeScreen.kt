package com.eduardo.horarios.ui.home

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
import com.eduardo.horarios.DAY_NAMES
import com.eduardo.horarios.DAY_SHORT
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
import com.eduardo.horarios.ui.theme.Indigo
import com.eduardo.horarios.ui.theme.StatusBarIcons
import com.eduardo.horarios.ui.theme.headerBrush
import com.eduardo.horarios.ui.theme.paletteColor
import com.eduardo.horarios.update.UpdateCard
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SPANISH = Locale.forLanguageTag("es-ES")

@Composable
fun HomeScreen(
    onOpenSchedules: () -> Unit,
    onAddActivity: (day: Int) -> Unit,
    onEditActivity: (id: Long) -> Unit,
    onOpenNotifications: () -> Unit,
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
                    text = { Text("Añadir") },
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
                    onOpenNotifications = onOpenNotifications,
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
                    TimelineRow(
                        activity = act,
                        isToday = selectedDay == today,
                        nowMinute = nowMinute,
                        isLast = act == dayActs.last(),
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
            ModeSegment("Día", Icons.Rounded.ViewDay, selected = !weekMode, modifier = Modifier.weight(1f)) { onChange(false) }
            ModeSegment("Semana", Icons.Rounded.CalendarViewWeek, selected = weekMode, modifier = Modifier.weight(1f)) { onChange(true) }
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
        Text("Tu semana", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.weight(1f))
        if (acts.isNotEmpty()) {
            Text(
                "${durationLabel(totalMin)} programadas",
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
    onOpenNotifications: () -> Unit,
) {
    val base = schedule?.let { paletteColor(it.colorIndex) } ?: Indigo
    val animatedBase by animateColorAsState(base, label = "headerColor")
    val dateText = remember {
        LocalDate.now()
            .format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", SPANISH))
            .replaceFirstChar { it.titlecase(SPANISH) }
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
                            text = schedule?.let { "${it.emoji}  ${it.name}" } ?: "Sin horario",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Cambiar horario",
                            tint = Color.White,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .clickable(onClick = onOpenNotifications),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.NotificationsActive, contentDescription = "Avisos", tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .clickable(onClick = onOpenSchedules),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = "Mis horarios", tint = Color.White)
                }
            }
            Spacer(Modifier.height(18.dp))
            NowNextCard(schedule = schedule, todayActs = todayActs, nowMinute = nowMinute)
        }
    }
}

@Composable
private fun NowNextCard(
    schedule: ScheduleEntity?,
    todayActs: List<ActivityEntity>,
    nowMinute: Int,
) {
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
                    label = "EMPIEZA AQUÍ",
                    title = "Crea tu primer horario",
                    subtitle = "Organiza tu semana y recibe avisos",
                )

                current != null -> {
                    val total = (current.endMinute - current.startMinute).coerceAtLeast(1)
                    val done = (nowMinute - current.startMinute).coerceIn(0, total)
                    GlassRow(
                        emoji = current.emoji,
                        label = "AHORA",
                        title = current.title,
                        subtitle = "${hm(current.startMinute)} – ${hm(current.endMinute)} · quedan ${durationLabel(total - done)}",
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
                            "Después: ${next.emoji} ${next.title} a las ${hm(next.startMinute)}",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                next != null -> GlassRow(
                    emoji = next.emoji,
                    label = "SIGUIENTE",
                    title = next.title,
                    subtitle = "A las ${hm(next.startMinute)} · en ${durationLabel(next.startMinute - nowMinute)}",
                )

                todayActs.isEmpty() -> GlassRow(
                    emoji = "🌤️",
                    label = "HOY",
                    title = "Día libre",
                    subtitle = "No tienes nada programado hoy",
                )

                else -> GlassRow(
                    emoji = "🎉",
                    label = "HOY",
                    title = "¡Día completado!",
                    subtitle = "Ya no te queda nada más por hoy",
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
                title = "Notificaciones desactivadas",
                text = "Actívalas para que te avise de tus actividades.",
                action = "Activar",
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
                title = "Avisos puntuales",
                text = "Permite las alarmas exactas para que los avisos lleguen a su hora.",
                action = "Permitir",
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
                    Text(DAY_SHORT[d], style = MaterialTheme.typography.titleMedium, color = fg)
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
        Text(DAY_NAMES[day], style = MaterialTheme.typography.titleLarge)
        if (isToday) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.padding(bottom = 4.dp)) { Pill("HOY", MaterialTheme.colorScheme.primary) }
        }
        Spacer(Modifier.weight(1f))
        if (acts.isNotEmpty()) {
            Text(
                "${acts.size} ${if (acts.size == 1) "actividad" else "actividades"} · ${durationLabel(totalMin)}",
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
            .alpha(if (isPast) 0.5f else 1f),
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
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isNow) {
                        Spacer(Modifier.width(8.dp))
                        Pill("AHORA", color)
                    }
                }
                Text(
                    durationLabel(activity.endMinute - activity.startMinute),
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
                        reminderShort(activity.reminderMinutes),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
        Text("Día libre", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "Pulsa «Añadir» para crear una actividad este día.",
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
        Text("Aún no tienes horarios", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "Crea uno (por ejemplo «Semana de clase» o «Vacaciones») y actívalo para recibir avisos.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onOpenSchedules, shape = RoundedCornerShape(16.dp)) {
            Text("Crear mi primer horario")
        }
    }
}
