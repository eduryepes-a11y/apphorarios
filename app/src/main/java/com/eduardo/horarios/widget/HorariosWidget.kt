package com.eduardo.horarios.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.eduardo.horarios.dayName
import com.eduardo.horarios.data.localized
import com.eduardo.horarios.HorariosApp
import com.eduardo.horarios.MainActivity
import com.eduardo.horarios.R
import com.eduardo.horarios.data.ActivityEntity
import com.eduardo.horarios.data.ScheduleEntity
import com.eduardo.horarios.durationLabel
import com.eduardo.horarios.hasDay
import com.eduardo.horarios.hm
import com.eduardo.horarios.nowMinuteOfDay
import com.eduardo.horarios.todayIndex
import com.eduardo.horarios.ui.theme.paletteColor
import kotlinx.coroutines.flow.combine
import androidx.glance.color.ColorProvider as DayNightColor

/** Lo que el widget necesita saber en un momento dado. */
data class WidgetData(
    val hasSchedule: Boolean,
    val scheduleTitle: String,
    val dayLabel: String,
    val summary: String,
    val emptyText: String,
    val nowLabel: String,
    val nowMinute: Int,
    val todayActs: List<ActivityEntity>,
) {
    companion object {
        fun from(r: Context, schedule: ScheduleEntity?, activities: List<ActivityEntity>): WidgetData {
            val today = todayIndex()
            val now = nowMinuteOfDay()
            val todayActs = activities.filter { it.daysMask.hasDay(today) }.sortedBy { it.startMinute }
            val current = todayActs.firstOrNull { now >= it.startMinute && now < it.endMinute }
            val next = todayActs.firstOrNull { it.startMinute > now }
            val summary = when {
                schedule == null -> r.getString(R.string.widget_tap_to_create)
                current != null -> r.getString(
                    R.string.widget_now,
                    "${current.emoji} ${current.title}",
                    durationLabel(r, current.endMinute - now),
                )
                next != null -> r.getString(
                    R.string.widget_next,
                    "${next.emoji} ${next.title}",
                    durationLabel(r, next.startMinute - now),
                )
                todayActs.isEmpty() -> r.getString(R.string.day_free_emoji)
                else -> r.getString(R.string.day_completed_emoji)
            }
            return WidgetData(
                hasSchedule = schedule != null,
                scheduleTitle = schedule?.let { "${it.emoji} ${it.name}" } ?: r.getString(R.string.app_name),
                dayLabel = dayName(r, today),
                summary = summary,
                emptyText = r.getString(if (schedule != null) R.string.widget_nothing_today else R.string.widget_open_app),
                nowLabel = r.getString(R.string.now_badge),
                nowMinute = now,
                todayActs = todayActs,
            )
        }
    }
}

class HorariosWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = (context.applicationContext as HorariosApp).repository
        val r = context.localized()
        val initial = WidgetData.from(r, repo.getActiveSchedule(), repo.getActiveActivities())
        val data = combine(
            repo.activeSchedule,
            repo.activeActivities,
            WidgetRefresher.tick,
        ) { schedule, activities, _ -> WidgetData.from(r, schedule, activities) }

        provideContent {
            val state by data.collectAsState(initial)
            WidgetContent(state)
        }
    }
}

// ------------------------------------------------------------------
// UI del widget (Glance)
// ------------------------------------------------------------------

private val TextPrimary = DayNightColor(day = Color(0xFF15162B), night = Color(0xFFE8E8F4))
private val TextSecondary = DayNightColor(day = Color(0xFF62647A), night = Color(0xFFA6A8C0))
private val White = ColorProvider(Color.White)
private val WhiteSoft = ColorProvider(Color(0xCCFFFFFF))

@Composable
private fun WidgetContent(d: WidgetData) {
    val open = actionStartActivity<MainActivity>()
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg))
            .padding(12.dp),
    ) {
        // Cabecera fija
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ImageProvider(R.drawable.widget_hero_bg))
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .clickable(open),
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = d.scheduleTitle,
                    style = TextStyle(color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                Text(
                    text = d.dayLabel,
                    style = TextStyle(color = WhiteSoft, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
            }
            Text(
                text = d.summary,
                style = TextStyle(color = WhiteSoft, fontSize = 12.sp),
                maxLines = 1,
            )
        }

        Spacer(GlanceModifier.height(8.dp))

        if (d.todayActs.isEmpty()) {
            Column(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight().clickable(open),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = d.emptyText,
                    style = TextStyle(color = TextSecondary, fontSize = 13.sp),
                )
            }
        } else {
            // Lista del día completo (se puede deslizar arriba y abajo)
            LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(d.todayActs, itemId = { it.id }) { a ->
                    Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        ActivityRow(a, d.nowMinute, d.nowLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(a: ActivityEntity, now: Int, nowLabel: String) {
    val isNow = now >= a.startMinute && now < a.endMinute
    val isPast = now >= a.endMinute
    val titleColor = when {
        isNow -> White
        isPast -> TextSecondary
        else -> TextPrimary
    }
    val timeColor = if (isNow) WhiteSoft else TextSecondary

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ImageProvider(if (isNow) R.drawable.widget_hero_bg else R.drawable.widget_item_bg))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .width(4.dp)
                .height(30.dp)
                .cornerRadius(2.dp)
                .background(ColorProvider(if (isNow) Color.White else paletteColor(a.colorIndex))),
        ) {}
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.width(46.dp)) {
            Text(
                text = hm(a.startMinute),
                style = TextStyle(color = titleColor, fontSize = 12.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                text = hm(a.endMinute),
                style = TextStyle(color = timeColor, fontSize = 10.sp),
            )
        }
        Text(
            text = "${a.emoji} ${a.title}",
            style = TextStyle(color = titleColor, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        when {
            isNow -> Text(nowLabel, style = TextStyle(color = White, fontSize = 9.sp, fontWeight = FontWeight.Bold))
            isPast -> Text("✓", style = TextStyle(color = TextSecondary, fontSize = 12.sp))
            else -> {}
        }
    }
}
