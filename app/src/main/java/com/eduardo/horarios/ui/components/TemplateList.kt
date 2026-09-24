package com.eduardo.horarios.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eduardo.horarios.R
import com.eduardo.horarios.data.ScheduleTemplate
import com.eduardo.horarios.data.Templates
import com.eduardo.horarios.ui.theme.paletteColor

/** Lista de plantillas + opción «Empezar en blanco». */
@Composable
fun TemplateList(
    onPick: (ScheduleTemplate) -> Unit,
    onBlank: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Templates.forEach { t ->
            TemplateCard(
                emoji = t.emoji,
                title = stringResource(t.nameRes),
                subtitle = stringResource(t.descriptionRes),
                colorIndex = t.colorIndex,
                onClick = { onPick(t) },
            )
        }
        TemplateCard(
            emoji = "✨",
            title = stringResource(R.string.tpl_blank),
            subtitle = stringResource(R.string.tpl_blank_desc),
            colorIndex = 9,
            onClick = onBlank,
        )
    }
}

@Composable
private fun TemplateCard(emoji: String, title: String, subtitle: String, colorIndex: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            EmojiBubble(emoji = emoji, color = paletteColor(colorIndex), size = 46.dp, corner = 14.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
