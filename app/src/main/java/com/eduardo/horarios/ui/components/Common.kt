@file:OptIn(ExperimentalLayoutApi::class)

package com.eduardo.horarios.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduardo.horarios.ui.theme.Palette

val ACTIVITY_EMOJIS = listOf(
    "📚", "💻", "💼", "💪", "🏃", "🧘", "🍽️", "☕", "🍳", "🛒",
    "🧹", "🎓", "📝", "📞", "🚗", "🚌", "🎮", "🎸", "🎨", "🎬",
    "⚽", "🏊", "🌳", "😴", "❤️", "👨‍👩‍👧", "🐶", "⭐",
)

val SCHEDULE_EMOJIS = listOf(
    "💼", "🎓", "🏖️", "🏠", "💪", "📅", "☀️", "🌙", "🎄", "🧳", "⚡", "🌱",
)

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
fun EmojiBubble(
    emoji: String,
    color: Color,
    size: Dp = 44.dp,
    corner: Dp = 14.dp,
    alpha: Float = 0.22f,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(color.copy(alpha = alpha)),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = (size.value * 0.5f).sp)
    }
}

@Composable
fun Pill(text: String, color: Color, textColor: Color = Color.White) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, color = textColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
    }
}

@Composable
fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Palette.forEachIndexed { index, color ->
            val isSel = index == selected
            val scale by animateFloatAsState(if (isSel) 1.1f else 1f, label = "colorScale")
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSel) 3.dp else 0.dp,
                        color = if (isSel) MaterialTheme.colorScheme.surface else Color.Transparent,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSel) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun EmojiPicker(emojis: List<String>, selected: String, accent: Color, onSelect: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        emojis.forEach { e ->
            val isSel = e == selected
            val bg by animateColorAsState(
                if (isSel) accent.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
                label = "emojiBg",
            )
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(bg)
                    .border(
                        width = 2.dp,
                        color = if (isSel) accent else Color.Transparent,
                        shape = RoundedCornerShape(14.dp),
                    )
                    .clickable { onSelect(e) },
                contentAlignment = Alignment.Center,
            ) {
                Text(e, fontSize = 22.sp)
            }
        }
    }
}
