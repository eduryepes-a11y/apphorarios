@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)

package com.eduardo.horarios.ui.schedules

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Lock
import com.eduardo.horarios.pro.FreeState
import com.eduardo.horarios.pro.LockReason
import com.eduardo.horarios.pro.Paywall
import com.eduardo.horarios.pro.PaywallReason
import com.eduardo.horarios.ui.pro.ProBadge
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.ui.platform.testTag
import com.eduardo.horarios.shortRange
import com.eduardo.horarios.shortDay
import java.time.LocalDate
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Share
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.eduardo.horarios.R
import com.eduardo.horarios.ui.backup.BackupIO
import com.eduardo.horarios.ui.backup.ImportController
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eduardo.horarios.HorariosApp
import com.eduardo.horarios.data.HorariosRepository
import com.eduardo.horarios.data.ScheduleEntity
import com.eduardo.horarios.data.ScheduleTemplate
import com.eduardo.horarios.ui.components.TemplateList
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.navigationBarsPadding
import com.eduardo.horarios.ui.components.ColorPicker
import com.eduardo.horarios.ui.components.EmojiPicker
import com.eduardo.horarios.ui.components.Pill
import com.eduardo.horarios.ui.components.SCHEDULE_EMOJIS
import com.eduardo.horarios.ui.components.SectionLabel
import com.eduardo.horarios.ui.theme.DefaultStatusBarIcons
import com.eduardo.horarios.ui.theme.headerBrush
import com.eduardo.horarios.ui.theme.paletteColor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ------------------------------------------------------------------
// ViewModel
// ------------------------------------------------------------------

data class SchedulesState(
    val schedules: List<ScheduleEntity> = emptyList(),
    val counts: Map<Long, Int> = emptyMap(),
    /** Qué está bloqueado 🔒 sin Pro. */
    val free: FreeState = FreeState(true, emptyMap(), needsChoice = false, choosable = emptyList(), canCreate = true),
)

class SchedulesViewModel(private val repo: HorariosRepository) : ViewModel() {
    val state: StateFlow<SchedulesState> = combine(repo.schedules, repo.activityCounts, repo.freeState) { s, c, f ->
        SchedulesState(s, c, f)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SchedulesState())

    fun activate(s: ScheduleEntity) = viewModelScope.launch { repo.activate(s.id) }
    fun create(name: String, emoji: String, color: Int) =
        viewModelScope.launch { repo.createSchedule(name, emoji, color) }
    fun update(s: ScheduleEntity) = viewModelScope.launch { repo.updateSchedule(s) }
    fun duplicate(s: ScheduleEntity) = viewModelScope.launch { repo.duplicateSchedule(s) }
    fun delete(s: ScheduleEntity) = viewModelScope.launch { repo.deleteSchedule(s) }
    fun createFromTemplate(t: ScheduleTemplate) = viewModelScope.launch { repo.createFromTemplate(t, activate = false) }
    fun setAutoRange(s: ScheduleEntity, from: Long?, to: Long?) = viewModelScope.launch { repo.setAutoRange(s.id, from, to) }
    fun share(context: Context, s: ScheduleEntity) = viewModelScope.launch { BackupIO.shareSchedule(context, repo, s) }

    companion object {
        val Factory = viewModelFactory {
            initializer { SchedulesViewModel((this[APPLICATION_KEY] as HorariosApp).repository) }
        }
    }
}

// ------------------------------------------------------------------
// Pantalla
// ------------------------------------------------------------------

@Composable
fun SchedulesScreen(
    onBack: (() -> Unit)?,
    vm: SchedulesViewModel = viewModel(factory = SchedulesViewModel.Factory),
) {
    DefaultStatusBarIcons()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) ImportController.pending.value = uri
    }

    var editing by remember { mutableStateOf<ScheduleEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var choosingTemplate by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ScheduleEntity?>(null) }
    var dating by remember { mutableStateOf<ScheduleEntity?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_schedules), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Rounded.FileOpen, contentDescription = stringResource(R.string.import_schedule))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    // Sin Pro: como mucho 2 horarios
                    if (state.free.canCreate) choosingTemplate = true else Paywall.show(PaywallReason.SCHEDULES)
                },
                modifier = Modifier.testTag("new_schedule"),
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_schedule)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.schedules_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }
            items(state.schedules, key = { it.id }) { s ->
                val lock = state.free.locked[s.id]
                ScheduleCard(
                    schedule = s,
                    count = state.counts[s.id] ?: 0,
                    lock = lock,
                    isPro = state.free.isPro,
                    onActivate = {
                        if (lock != null) {
                            Paywall.show(PaywallReason.LOCKED_SCHEDULE)
                        } else if (!s.isActive) {
                            vm.activate(s)
                            scope.launch { snackbar.showSnackbar(context.getString(R.string.schedule_activated, s.name)) }
                        }
                    },
                    onEdit = { editing = s },
                    onDuplicate = { if (state.free.canCreate) vm.duplicate(s) else Paywall.show(PaywallReason.SCHEDULES) },
                    onShare = { vm.share(context, s) },
                    onDates = { if (state.free.isPro) dating = s else Paywall.show(PaywallReason.AUTO_DATES) },
                    onDelete = { deleting = s },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    if (choosingTemplate) {
        ModalBottomSheet(
            onDismissRequest = { choosingTemplate = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            ) {
                Text(stringResource(R.string.new_schedule), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.template_pick_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )
                TemplateList(
                    onPick = { t ->
                        choosingTemplate = false
                        vm.createFromTemplate(t)
                        scope.launch { snackbar.showSnackbar(context.getString(R.string.template_created, context.getString(t.nameRes))) }
                    },
                    onBlank = {
                        choosingTemplate = false
                        creating = true
                    },
                )
            }
        }
    }

    if (creating) {
        ScheduleDialog(
            title = stringResource(R.string.new_schedule),
            initial = null,
            onDismiss = { creating = false },
            onSave = { name, emoji, color ->
                vm.create(name, emoji, color)
                creating = false
            },
        )
    }
    editing?.let { s ->
        ScheduleDialog(
            title = stringResource(R.string.edit_schedule),
            initial = s,
            onDismiss = { editing = null },
            onSave = { name, emoji, color ->
                vm.update(s.copy(name = name, emoji = emoji, colorIndex = color))
                editing = null
            },
        )
    }
    dating?.let { s ->
        AutoRangeDialog(
            schedule = s,
            onDismiss = { dating = null },
            onSave = { from, to ->
                vm.setAutoRange(s, from, to)
                dating = null
                if (from != null && to != null) {
                    val msg = context.getString(
                        R.string.auto_dates_saved,
                        s.name,
                        shortRange(context, LocalDate.ofEpochDay(from), LocalDate.ofEpochDay(to)),
                    )
                    scope.launch { snackbar.showSnackbar(msg) }
                }
            },
        )
    }
    deleting?.let { s ->
        val n = state.counts[s.id] ?: 0
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_schedule_title, s.name)) },
            text = { Text(if (n > 0) pluralStringResource(R.plurals.delete_schedule_text, n, n) else stringResource(R.string.cannot_undo)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(s)
                    deleting = null
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ScheduleCard(
    schedule: ScheduleEntity,
    count: Int,
    lock: LockReason?,
    isPro: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onShare: () -> Unit,
    onDates: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val color = paletteColor(schedule.colorIndex)
    var menu by remember { mutableStateOf(false) }

    Surface(
        onClick = onActivate,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = if (schedule.isActive) BorderStroke(2.dp, color) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = if (schedule.isActive) 8.dp else 0.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(headerBrush(color)),
                contentAlignment = Alignment.Center,
            ) {
                Text(schedule.emoji, fontSize = 28.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    schedule.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (lock != null) {
                    // Bloqueado sin Pro: no se ve su contenido, solo por qué
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(if (lock == LockReason.PRO_FEATURE) R.string.lock_pro_feature else R.string.lock_over_limit),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("locked_${schedule.id}"),
                        )
                    }
                } else Text(
                    pluralStringResource(R.plurals.activities_count, count, count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (lock == null && schedule.hasAutoRange) {
                    Text(
                        "📅 " + shortRange(context, LocalDate.ofEpochDay(schedule.autoFrom!!), LocalDate.ofEpochDay(schedule.autoTo!!)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("auto_range_${schedule.id}"),
                    )
                }
                if (schedule.isActive) {
                    Spacer(Modifier.height(6.dp))
                    Pill(stringResource(R.string.active_badge), color)
                }
            }
            if (lock != null) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = stringResource(R.string.locked),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            } else {
                RadioButton(
                    selected = schedule.isActive,
                    onClick = onActivate,
                    colors = RadioButtonDefaults.colors(selectedColor = color),
                )
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.testTag("schedule_menu_${schedule.id}")) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.options))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                  if (lock != null) {
                    // Bloqueado: solo desbloquear con Pro o borrar
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.unlock_with_pro)) },
                        leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                        onClick = { menu = false; Paywall.show(PaywallReason.LOCKED_SCHEDULE) },
                        modifier = Modifier.testTag("menu_unlock"),
                    )
                  } else {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit)) },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { menu = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share)) },
                        leadingIcon = { Icon(Icons.Rounded.Share, null) },
                        onClick = { menu = false; onShare() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.auto_dates_menu)) },
                        leadingIcon = { Icon(Icons.Rounded.DateRange, null) },
                        trailingIcon = { if (!isPro) ProBadge() },
                        onClick = { menu = false; onDates() },
                        modifier = Modifier.testTag("menu_auto_dates"),
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.duplicate)) },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                        onClick = { menu = false; onDuplicate() },
                    )
                  }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleDialog(
    title: String,
    initial: ScheduleEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, emoji: String, color: Int) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: SCHEDULE_EMOJIS.first()) }
    var color by remember { mutableIntStateOf(initial?.colorIndex ?: 0) }
    val accent = paletteColor(color)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(R.string.name)) },
                    placeholder = { Text(stringResource(R.string.schedule_name_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(20.dp))
                SectionLabel(stringResource(R.string.icon))
                EmojiPicker(SCHEDULE_EMOJIS, emoji, accent) { emoji = it }
                Spacer(Modifier.height(20.dp))
                SectionLabel(stringResource(R.string.color))
                ColorPicker(selected = color, onSelect = { color = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), emoji, color) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
    )
}


/** Elegir las fechas en que este horario se activa solo. */
@Composable
private fun AutoRangeDialog(
    schedule: ScheduleEntity,
    onDismiss: () -> Unit,
    onSave: (from: Long?, to: Long?) -> Unit,
) {
    fun toMillis(epochDay: Long?) = epochDay?.let { it * 86_400_000L }
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = toMillis(schedule.autoFrom),
        initialSelectedEndDateMillis = toMillis(schedule.autoTo),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        Math.floorDiv(state.selectedStartDateMillis!!, 86_400_000L),
                        Math.floorDiv(state.selectedEndDateMillis!!, 86_400_000L),
                    )
                },
                enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
                modifier = Modifier.testTag("auto_dates_save"),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (schedule.hasAutoRange) {
                    TextButton(
                        onClick = { onSave(null, null) },
                        modifier = Modifier.testTag("auto_dates_clear"),
                    ) { Text(stringResource(R.string.auto_dates_clear), color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    ) {
        DateRangePicker(
            state = state,
            title = {
                Column(Modifier.padding(start = 24.dp, end = 16.dp, top = 16.dp)) {
                    Text(
                        stringResource(R.string.auto_dates_title, "${schedule.emoji} ${schedule.name}"),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.auto_dates_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("auto_dates_text"),
                    )
                }
            },
            headline = {
                // La cabecera por defecto («Fecha de inicio - Fecha de finalización») no cabe en español
                val context = LocalContext.current
                val start = state.selectedStartDateMillis?.let { LocalDate.ofEpochDay(Math.floorDiv(it, 86_400_000L)) }
                val end = state.selectedEndDateMillis?.let { LocalDate.ofEpochDay(Math.floorDiv(it, 86_400_000L)) }
                Text(
                    when {
                        start != null && end != null -> shortRange(context, start, end)
                        start != null -> shortDay(context, start) + " – …"
                        else -> stringResource(R.string.auto_dates_pick)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp).testTag("auto_dates_headline"),
                )
            },
            showModeToggle = true,
            modifier = Modifier.weight(1f),
        )
    }
}
