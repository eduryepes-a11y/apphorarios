package com.eduardo.horarios.data

import android.content.Context
import com.eduardo.horarios.MASK_ALL
import com.eduardo.horarios.MASK_WEEKDAYS
import com.eduardo.horarios.MASK_WEEKEND
import com.eduardo.horarios.R

/** Actividad de una plantilla (el título se traduce al crearla). */
data class TemplateActivity(
    val titleRes: Int,
    val emoji: String,
    val daysMask: Int,
    val start: Int,
    val end: Int,
    val colorIndex: Int,
    val reminder: Int = 10,
) {
    fun toEntity(r: Context, scheduleId: Long): ActivityEntity {
        val title = r.getString(titleRes)
        return ActivityEntity(
        scheduleId = scheduleId,
        title = title,
        emoji = emoji,
        daysMask = daysMask,
        startMinute = start,
        endMinute = end,
        colorIndex = colorIndex,
        reminderMinutes = reminder,
        tracked = Tracking.defaultTracked(title),
    )
    }
}

data class ScheduleTemplate(
    val key: String,
    val nameRes: Int,
    val descriptionRes: Int,
    val emoji: String,
    val colorIndex: Int,
    val activities: List<TemplateActivity>,
)

private fun h(hour: Int, minute: Int = 0) = hour * 60 + minute

private const val MON_WED_FRI = 0b0010101
private const val TUE_THU = 0b0001010
private const val MON_TO_THU = 0b0001111

/** Plantillas para empezar rápido. */
val Templates = listOf(
    ScheduleTemplate(
        key = "student", nameRes = R.string.tpl_student, descriptionRes = R.string.tpl_student_desc, emoji = "🎓", colorIndex = 0,
        activities = listOf(
            TemplateActivity(R.string.tpl_act_classes, "🎓", MASK_WEEKDAYS, h(8, 30), h(14, 30), 0),
            TemplateActivity(R.string.tpl_act_lunch, "🍽️", MASK_WEEKDAYS, h(14, 30), h(15, 30), 6, 0),
            TemplateActivity(R.string.tpl_act_study, "📚", MON_TO_THU, h(17), h(19), 1),
            TemplateActivity(R.string.tpl_act_sport, "⚽", TUE_THU, h(19, 30), h(20, 30), 3, 15),
            TemplateActivity(R.string.tpl_act_review, "📝", 0b0100000, h(11), h(13), 2),
        ),
    ),
    ScheduleTemplate(
        key = "office", nameRes = R.string.tpl_office, descriptionRes = R.string.tpl_office_desc, emoji = "💼", colorIndex = 1,
        activities = listOf(
            TemplateActivity(R.string.tpl_act_work, "💻", MASK_WEEKDAYS, h(9), h(14), 1),
            TemplateActivity(R.string.tpl_act_lunch, "🍽️", MASK_WEEKDAYS, h(14), h(15), 6, 0),
            TemplateActivity(R.string.tpl_act_work, "💻", MASK_WEEKDAYS, h(15), h(18), 1, 0),
            TemplateActivity(R.string.tpl_act_gym, "💪", MON_WED_FRI, h(19), h(20), 3, 15),
            TemplateActivity(R.string.tpl_act_read, "📖", MASK_ALL, h(22, 30), h(23), 8, 0),
        ),
    ),
    ScheduleTemplate(
        key = "morning", nameRes = R.string.tpl_morning_shift, descriptionRes = R.string.tpl_morning_shift_desc, emoji = "🌅", colorIndex = 4,
        activities = listOf(
            TemplateActivity(R.string.tpl_act_wake_up, "⏰", MASK_WEEKDAYS, h(6), h(6, 30), 4, 0),
            TemplateActivity(R.string.tpl_act_shift, "🏥", MASK_WEEKDAYS, h(7), h(15), 5, 30),
            TemplateActivity(R.string.tpl_act_lunch, "🍽️", MASK_WEEKDAYS, h(15, 30), h(16, 30), 6, 0),
            TemplateActivity(R.string.tpl_act_rest, "😴", MASK_WEEKDAYS, h(16, 30), h(17, 30), 8, -1),
            TemplateActivity(R.string.tpl_act_sleep, "🌙", MASK_WEEKDAYS, h(22), h(22, 30), 9, 0),
        ),
    ),
    ScheduleTemplate(
        key = "evening", nameRes = R.string.tpl_evening_shift, descriptionRes = R.string.tpl_evening_shift_desc, emoji = "🌇", colorIndex = 5,
        activities = listOf(
            TemplateActivity(R.string.tpl_act_gym, "💪", MON_WED_FRI, h(10), h(11), 3, 15),
            TemplateActivity(R.string.tpl_act_lunch, "🍽️", MASK_WEEKDAYS, h(13), h(14), 6, 0),
            TemplateActivity(R.string.tpl_act_shift, "🏥", MASK_WEEKDAYS, h(15), h(23), 5, 30),
        ),
    ),
    ScheduleTemplate(
        key = "habits", nameRes = R.string.tpl_habits, descriptionRes = R.string.tpl_habits_desc, emoji = "🌱", colorIndex = 3,
        activities = listOf(
            TemplateActivity(R.string.tpl_act_wake_up, "⏰", MASK_ALL, h(7, 30), h(7, 45), 4, 0),
            TemplateActivity(R.string.tpl_act_exercise, "🏃", MASK_ALL, h(8), h(8, 30), 3, 0),
            TemplateActivity(R.string.tpl_act_walk, "🌳", MASK_WEEKEND, h(11), h(12), 2, 15),
            TemplateActivity(R.string.tpl_act_read, "📖", MASK_ALL, h(22), h(22, 30), 8, 0),
            TemplateActivity(R.string.tpl_act_sleep, "🌙", MASK_ALL, h(23), h(23, 30), 9, 0),
        ),
    ),
)
