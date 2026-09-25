@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.eduardo.horarios.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PostAdd
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eduardo.horarios.dayShort
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.platform.testTag
import com.eduardo.horarios.longDate
import java.time.Instant
import java.time.ZoneOffset
import com.eduardo.horarios.R
import androidx.compose.ui.res.stringResource
import com.eduardo.horarios.HorariosApp
import com.eduardo.horarios.MASK_ALL
import com.eduardo.horarios.MASK_WEEKDAYS
import com.eduardo.horarios.MASK_WEEKEND
import com.eduardo.horarios.REMINDER_OPTIONS
import com.eduardo.horarios.daysSummary
import com.eduardo.horarios.durationLabel
import com.eduardo.horarios.hasDay
import com.eduardo.horarios.hm
import com.eduardo.horarios.reminderChipLabel
import com.eduardo.horarios.toggleDay
import com.eduardo.horarios.ui.components.ACTIVITY_EMOJIS
import com.eduardo.horarios.ui.components.ColorPicker
import com.eduardo.horarios.ui.components.EmojiBubble
import com.eduardo.horarios.ui.components.EmojiPicker
import com.eduardo.horarios.ui.components.SectionLabel
import com.eduardo.horarios.ui.theme.DefaultStatusBarIcons
import com.eduardo.horarios.ui.theme.headerBrush
import com.eduardo.horarios.ui.theme.paletteColor

@Composable
fun EditorScreen(
    onBack: () -> Unit,
    onBulkAdd: () -> Unit,
    vm: EditorViewModel = viewModel(factory = EditorViewModel.Factory),
) {
    DefaultStatusBarIcons()
    val form = vm.form
    val accent by animateColorAsState(paletteColor(form.colorIndex), label = "accent")

    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val startAlerts = remember { (context.applicationContext as HorariosApp).scheduler.startAlerts }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (vm.isEditing) R.string.edit_activity else R.string.new_activity)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close)) }
                },
                actions = {
                    if (!vm.isEditing) {
                        IconButton(onClick = onBulkAdd) {
                            Icon(Icons.Rounded.PostAdd, contentDescription = stringResource(R.string.bulk_add_title))
                        }
                    }
                    if (vm.isEditing) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = { vm.save(onBack) },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(56.dp),
                ) {
                    Text(stringResource(R.string.save), style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            PreviewCard(form = form, accent = accent)

            AnimatedVisibility(vm.error != null) {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        vm.error?.let { stringResource(it) } ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            OutlinedTextField(
                value = form.title,
                onValueChange = { v -> vm.setTitle(v) },
                label = { Text(stringResource(R.string.activity_name)) },
                placeholder = { Text(stringResource(R.string.activity_name_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            Column {
                SectionLabel(stringResource(R.string.icon))
                EmojiPicker(ACTIVITY_EMOJIS, form.emoji, accent) { e -> vm.update { copy(emoji = e) } }
            }

            Column {
                SectionLabel(stringResource(R.string.repeat))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip(stringResource(R.string.repeat_weekly), !form.oneOff, accent, Modifier.testTag("repeat_weekly")) {
                        vm.update { copy(oneOff = false) }
                    }
                    ChoiceChip(stringResource(R.string.repeat_once), form.oneOff, accent, Modifier.testTag("repeat_once")) {
                        vm.update { copy(oneOff = true) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (form.oneOff) {
                    DateBox(longDate(context, form.date), accent, Modifier.fillMaxWidth().testTag("date_box")) { pickDate = true }
                } else {
                    DaysPicker(mask = form.daysMask, accent = accent) { m -> vm.update { copy(daysMask = m) } }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickDaysChip(stringResource(R.string.days_weekdays), MASK_WEEKDAYS, form.daysMask, accent) { m -> vm.update { copy(daysMask = m) } }
                        QuickDaysChip(stringResource(R.string.days_weekend_short), MASK_WEEKEND, form.daysMask, accent) { m -> vm.update { copy(daysMask = m) } }
                        QuickDaysChip(stringResource(R.string.days_all_short), MASK_ALL, form.daysMask, accent) { m -> vm.update { copy(daysMask = m) } }
                    }
                }
            }

            Column {
                SectionLabel(stringResource(R.string.time))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeBox(stringResource(R.string.starts), form.startMinute, accent, Modifier.weight(1f)) { pickStart = true }
                    TimeBox(stringResource(R.string.ends), form.endMinute, accent, Modifier.weight(1f)) { pickEnd = true }
                }
                if (form.endMinute > form.startMinute) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.duration_label, durationLabel(context, form.endMinute - form.startMinute)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }

            Column {
                SectionLabel(stringResource(R.string.color))
                ColorPicker(selected = form.colorIndex, onSelect = { c -> vm.update { copy(colorIndex = c) } })
            }

            Column {
                SectionLabel(stringResource(R.string.reminder))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    REMINDER_OPTIONS.forEach { r ->
                        val sel = form.reminderMinutes == r
                        FilterChip(
                            selected = sel,
                            onClick = { vm.update { copy(reminderMinutes = r) } },
                            label = { Text(reminderChipLabel(context, r)) },
                            leadingIcon = if (sel && r >= 0) {
                                { Icon(Icons.Rounded.NotificationsActive, null, Modifier.size(18.dp)) }
                            } else null,
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.copy(alpha = 0.2f),
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                                selectedLeadingIconColor = accent,
                            ),
                        )
                    }
                }
                val hint = when {
                    form.reminderMinutes < 0 -> stringResource(R.string.hint_no_reminder)
                    form.reminderMinutes == 0 -> stringResource(R.string.hint_at_start)
                    startAlerts -> stringResource(R.string.hint_before_and_start, reminderChipLabel(context, form.reminderMinutes).lowercase())
                    else -> stringResource(R.string.hint_before, reminderChipLabel(context, form.reminderMinutes).lowercase())
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            // Seguir en Progreso
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier
                        .clickable { vm.setTracked(!form.tracked) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Insights, contentDescription = null, tint = accent)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.track_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(if (form.tracked) R.string.track_on_text else R.string.track_off_text),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = form.tracked,
                        onCheckedChange = { vm.setTracked(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = accent),
                        modifier = Modifier.testTag("switch_tracked"),
                    )
                }
            }

            Column {
                SectionLabel(stringResource(R.string.notes))
                OutlinedTextField(
                    value = form.notes,
                    onValueChange = { v -> vm.update { copy(notes = v.take(300)) } },
                    placeholder = { Text(stringResource(R.string.notes_hint)) },
                    minLines = 3,
                    shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (pickStart) {
        TimePickerDialog(
            title = stringResource(R.string.start_time),
            initialMinute = form.startMinute,
            accent = accent,
            onDismiss = { pickStart = false },
            onConfirm = { vm.setStart(it); pickStart = false },
        )
    }
    if (pickEnd) {
        TimePickerDialog(
            title = stringResource(R.string.end_time),
            initialMinute = form.endMinute,
            accent = accent,
            onDismiss = { pickEnd = false },
            onConfirm = { vm.setEnd(it); pickEnd = false },
        )
    }
    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = form.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        vm.update { copy(date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()) }
                    }
                    pickDate = false
                }) { Text(stringResource(R.string.accept)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) {
            DatePicker(state = state)
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_activity_title)) },
            text = { Text(stringResource(R.string.delete_activity_text, form.title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(onBack)
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun PreviewCard(form: EditorForm, accent: Color) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(headerBrush(accent))
            .drawBehind {
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = size.height * 0.9f,
                    center = Offset(size.width, 0f),
                )
            }
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBubble(emoji = form.emoji, color = Color.White, size = 64.dp, corner = 22.dp, alpha = 0.22f)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    form.title.ifBlank { stringResource(R.string.new_activity) },
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${hm(form.startMinute)} – ${hm(form.endMinute)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f),
                )
                Text(
                    (if (form.oneOff) longDate(context, form.date) else daysSummary(context, form.daysMask)) +
                        " · " + reminderChipLabel(context, form.reminderMinutes),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
internal fun DaysPicker(mask: Int, accent: Color, onChange: (Int) -> Unit) {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        for (d in 0..6) {
            val sel = mask.hasDay(d)
            val bg by animateColorAsState(
                if (sel) accent else MaterialTheme.colorScheme.surfaceVariant,
                label = "dayPick",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(bg)
                    .clickable { onChange(mask.toggleDay(d)) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    dayShort(context, d),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun QuickDaysChip(label: String, value: Int, current: Int, accent: Color, onSelect: (Int) -> Unit) {
    FilterChip(
        selected = current == value,
        onClick = { onSelect(value) },
        label = { Text(label) },
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = accent.copy(alpha = 0.2f),
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = accent.copy(alpha = 0.2f),
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier,
    )
}

@Composable
private fun DateBox(text: String, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Event, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun TimeBox(label: String, minute: Int, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(hm(minute), fontSize = 24.sp, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun TimePickerDialog(
    title: String,
    initialMinute: Int,
    accent: Color,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = true,
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                )
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialSelectedContentColor = Color.White,
                        selectorColor = accent,
                        timeSelectorSelectedContainerColor = accent.copy(alpha = 0.22f),
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.accept)) }
                }
            }
        }
    }
}
