@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.eduardo.horarios.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eduardo.horarios.R
import com.eduardo.horarios.data.PlannedActivity
import com.eduardo.horarios.dayName
import com.eduardo.horarios.durationLabel
import com.eduardo.horarios.hm
import com.eduardo.horarios.reminderChipLabel
import com.eduardo.horarios.ui.components.EmojiBubble
import com.eduardo.horarios.ui.theme.paletteColor

/** Hoja de acciones al tocar una actividad: hecha, saltar este día, editar. */
@Composable
fun ActivitySheet(
    planned: PlannedActivity,
    done: Boolean,
    canMarkDone: Boolean,
    onToggleDone: () -> Unit,
    onToggleSkip: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val a = planned.activity
    val color = paletteColor(a.colorIndex)
    val dayLabel = dayName(context, planned.date.dayOfWeek.value - 1).lowercase() + " " + planned.date.dayOfMonth

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiBubble(emoji = a.emoji, color = color, size = 52.dp, corner = 16.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${hm(planned.start)} – ${hm(planned.end)} · ${durationLabel(context, planned.end - planned.start)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (planned.shift != 0) {
                        Text(
                            stringResource(R.string.delayed_by, durationLabel(context, planned.shift)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
            if (a.notes.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(a.notes, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                reminderChipLabel(context, a.reminderMinutes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            if (canMarkDone && !planned.skipped) {
                SheetAction(
                    icon = if (done) Icons.Rounded.RadioButtonUnchecked else Icons.Rounded.CheckCircle,
                    tint = color,
                    text = stringResource(if (done) R.string.mark_not_done else R.string.mark_done),
                    onClick = onToggleDone,
                )
            }
            SheetAction(
                icon = if (planned.skipped) Icons.Rounded.EventAvailable else Icons.Rounded.EventBusy,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                text = stringResource(if (planned.skipped) R.string.unskip_day else R.string.skip_day, dayLabel),
                onClick = onToggleSkip,
            )
            SheetAction(
                icon = Icons.Rounded.Edit,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                text = stringResource(R.string.edit_activity),
                onClick = onEdit,
            )
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, tint: Color, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** «Voy con retraso»: elegir cuántos minutos se retrasa lo que queda de hoy. */
@Composable
fun DelaySheet(
    currentDelay: Int,
    onDelay: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(stringResource(R.string.delay_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.delay_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(10, 15, 20, 30, 45, 60).forEach { m ->
                    FilterChip(
                        selected = false,
                        onClick = { onDelay(m) },
                        label = { Text("+" + durationLabel(context, m)) },
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }
            if (currentDelay > 0) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.delay_current, durationLabel(context, currentDelay)),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onReset) { Text(stringResource(R.string.delay_reset)) }
                }
            }
        }
    }
}
