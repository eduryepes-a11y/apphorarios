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
)

class SchedulesViewModel(private val repo: HorariosRepository) : ViewModel() {
    val state: StateFlow<SchedulesState> = combine(repo.schedules, repo.activityCounts) { s, c ->
        SchedulesState(s, c)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SchedulesState())

    fun activate(s: ScheduleEntity) = viewModelScope.launch { repo.activate(s.id) }
    fun create(name: String, emoji: String, color: Int) =
        viewModelScope.launch { repo.createSchedule(name, emoji, color) }
    fun update(s: ScheduleEntity) = viewModelScope.launch { repo.updateSchedule(s) }
    fun duplicate(s: ScheduleEntity) = viewModelScope.launch { repo.duplicateSchedule(s) }
    fun delete(s: ScheduleEntity) = viewModelScope.launch { repo.deleteSchedule(s) }
    fun createFromTemplate(t: ScheduleTemplate) = viewModelScope.launch { repo.createFromTemplate(t, activate = false) }
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
                onClick = { choosingTemplate = true },
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
                ScheduleCard(
                    schedule = s,
                    count = state.counts[s.id] ?: 0,
                    onActivate = {
                        if (!s.isActive) {
                            vm.activate(s)
                            scope.launch { snackbar.showSnackbar(context.getString(R.string.schedule_activated, s.name)) }
                        }
                    },
                    onEdit = { editing = s },
                    onDuplicate = { vm.duplicate(s) },
                    onShare = { vm.share(context, s) },
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
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                Text(
                    pluralStringResource(R.plurals.activities_count, count, count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (schedule.isActive) {
                    Spacer(Modifier.height(6.dp))
                    Pill(stringResource(R.string.active_badge), color)
                }
            }
            RadioButton(
                selected = schedule.isActive,
                onClick = onActivate,
                colors = RadioButtonDefaults.colors(selectedColor = color),
            )
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.options))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
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
                        text = { Text(stringResource(R.string.duplicate)) },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                        onClick = { menu = false; onDuplicate() },
                    )
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

