package com.eduardo.horarios.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduardo.horarios.dayShort
import androidx.compose.ui.platform.LocalContext
import com.eduardo.horarios.data.ActivityEntity
import com.eduardo.horarios.data.PlannedActivity
import com.eduardo.horarios.hasDay
import com.eduardo.horarios.hm
import com.eduardo.horarios.ui.theme.paletteColor

private val HOUR_HEIGHT = 56.dp
private val AXIS_WIDTH = 36.dp

/**
 * Vista de cuadrícula semanal: 7 columnas (L–D) y las horas en vertical.
 * Tocar un bloque abre la actividad; tocar el nombre de un día abre la vista de ese día.
 */
@Composable
fun WeekGrid(
    days: List<List<PlannedActivity>>,
    today: Int?,
    nowMinute: Int,
    onActivityClick: (PlannedActivity) -> Unit,
    onDayClick: (Int) -> Unit,
) {
    val all = days.flatten()
    val startHour = all.minOfOrNull { it.start / 60 }?.coerceAtMost(8) ?: 8
    val endHour = (all.maxOfOrNull { (it.end + 59) / 60 }?.coerceAtLeast(20) ?: 22)
        .coerceIn(startHour + 1, 24)
    val hours = endHour - startHour
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val todayTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        Column(Modifier.padding(start = 4.dp, end = 8.dp, top = 12.dp, bottom = 14.dp)) {
            // Cabecera de días
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(AXIS_WIDTH))
                for (d in 0..6) {
                    val isToday = d == today
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onDayClick(d) }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                dayShort(LocalContext.current, d),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(HOUR_HEIGHT * hours),
            ) {
                // Eje de horas
                Box(
                    Modifier
                        .width(AXIS_WIDTH)
                        .fillMaxHeight()
                ) {
                    for (h in 0 until hours) {
                        Text(
                            hm((startHour + h) * 60),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.offset(y = HOUR_HEIGHT * h - 6.dp),
                        )
                    }
                }
                // Columnas de días
                for (d in 0..6) {
                    DayColumn(
                        activities = days[d],
                        startHour = startHour,
                        hours = hours,
                        isToday = d == today,
                        nowMinute = nowMinute,
                        lineColor = lineColor,
                        todayTint = todayTint,
                        onActivityClick = onActivityClick,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayColumn(
    activities: List<PlannedActivity>,
    startHour: Int,
    hours: Int,
    isToday: Boolean,
    nowMinute: Int,
    lineColor: Color,
    todayTint: Color,
    onActivityClick: (PlannedActivity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lanes = remember(activities) { assignLanes(activities) }
    val nowColor = MaterialTheme.colorScheme.tertiary

    BoxWithConstraints(
        modifier = modifier
            .background(if (isToday) todayTint else Color.Transparent)
            .drawBehind {
                val hourPx = size.height / hours
                for (h in 0..hours) {
                    val y = h * hourPx
                    drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }
                drawLine(lineColor, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1f)
            },
    ) {
        val colWidth = maxWidth
        activities.forEach { a ->
            val (lane, count) = lanes[a.activity.id] ?: (0 to 1)
            val top = minutesToDp(a.start - startHour * 60)
            val height = minutesToDp(a.end - a.start)
            val width = colWidth / count
            ActivityBlock(
                activity = a.activity,
                height = height,
                onClick = { onActivityClick(a) },
                modifier = Modifier
                    .offset(x = width * lane, y = top)
                    .width(width)
                    .height(height),
            )
        }

        // Línea de "ahora"
        val nowOffset = nowMinute - startHour * 60
        if (isToday && nowOffset in 0..(hours * 60)) {
            Box(
                Modifier
                    .offset(y = minutesToDp(nowOffset) - 1.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(nowColor)
            )
            Box(
                Modifier
                    .offset(x = (-3).dp, y = minutesToDp(nowOffset) - 4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(nowColor)
            )
        }
    }
}

@Composable
private fun ActivityBlock(
    activity: ActivityEntity,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = paletteColor(activity.colorIndex)
    Box(modifier.padding(horizontal = 1.5.dp, vertical = 1.dp)) {
        Row(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
                .background(color.copy(alpha = 0.28f))
                .clickable(onClick = onClick),
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(color)
            )
            Column(Modifier.padding(horizontal = 2.dp, vertical = 2.dp)) {
                Text(activity.emoji, fontSize = 12.sp, maxLines = 1)
                if (height >= 44.dp) {
                    Text(
                        activity.title,
                        fontSize = 9.sp,
                        lineHeight = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (height >= 80.dp) 3 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun minutesToDp(minutes: Int): Dp = HOUR_HEIGHT * (minutes / 60f)

/** Reparte las actividades que se solapan en carriles, para dibujarlas lado a lado. */
private fun assignLanes(items: List<PlannedActivity>): Map<Long, Pair<Int, Int>> {
    val result = HashMap<Long, Pair<Int, Int>>()
    val laneOf = HashMap<Long, Int>()
    val laneEnds = mutableListOf<Int>()
    var cluster = mutableListOf<PlannedActivity>()
    var clusterEnd = -1

    fun flush() {
        val n = laneEnds.size.coerceAtLeast(1)
        cluster.forEach { result[it.activity.id] = (laneOf[it.activity.id] ?: 0) to n }
        cluster = mutableListOf()
        laneEnds.clear()
        clusterEnd = -1
    }

    for (a in items.sortedBy { it.start }) {
        if (cluster.isNotEmpty() && a.start >= clusterEnd) flush()
        var lane = laneEnds.indexOfFirst { it <= a.start }
        if (lane == -1) {
            lane = laneEnds.size
            laneEnds.add(a.end)
        } else {
            laneEnds[lane] = a.end
        }
        laneOf[a.activity.id] = lane
        cluster.add(a)
        clusterEnd = maxOf(clusterEnd, a.end)
    }
    flush()
    return result
}
