package com.eduardo.horarios.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlannerTest {

    // Lunes 21 de septiembre de 2026
    private val monday = LocalDate.of(2026, 9, 21)
    private val sunday = monday.plusDays(6)
    private val weekdays = 0b0011111

    private val first = activity(id = 1, start = 9 * 60, end = 10 * 60)
    private val second = activity(id = 2, start = 11 * 60, end = 12 * 60)
    private val activities = listOf(second, first)

    private fun activity(id: Long, start: Int, end: Int, days: Int = weekdays) = ActivityEntity(
        id = id, scheduleId = 1, title = "A$id", emoji = "📌",
        daysMask = days, startMinute = start, endMinute = end, colorIndex = 0, reminderMinutes = 10,
    )

    private fun skip(day: LocalDate, activityId: Long?, id: Long = 0) =
        OverrideEntity(id = id, scheduleId = 1, epochDay = day.toEpochDay(), type = OverrideEntity.TYPE_SKIP, activityId = activityId)

    private fun shift(day: LocalDate, from: Int, minutes: Int, id: Long) =
        OverrideEntity(id = id, scheduleId = 1, epochDay = day.toEpochDay(), type = OverrideEntity.TYPE_SHIFT, fromMinute = from, minutes = minutes)

    @Test
    fun planSortsByTimeAndFiltersDays() {
        val plan = Planner.plan(monday, activities, emptyList())
        assertEquals(listOf(1L, 2L), plan.map { it.activity.id })
        assertTrue(plan.none { it.skipped || it.shift != 0 })
        assertTrue(Planner.plan(sunday, activities, emptyList()).isEmpty())
    }

    @Test
    fun skipOnlyAffectsThatActivityAndDay() {
        val overrides = listOf(skip(monday, activityId = 1))
        val plan = Planner.plan(monday, activities, overrides)
        assertTrue(plan.first { it.activity.id == 1L }.skipped)
        assertFalse(plan.first { it.activity.id == 2L }.skipped)
        assertTrue(Planner.plan(monday.plusDays(1), activities, overrides).none { it.skipped })
    }

    @Test
    fun dayOffSkipsEverything() {
        val overrides = listOf(skip(monday, activityId = null))
        assertTrue(Planner.overridesFor(monday.toEpochDay(), overrides).dayOff)
        assertTrue(Planner.plan(monday, activities, overrides).all { it.skipped })
    }

    @Test
    fun delayOnlyMovesLaterActivities() {
        val overrides = listOf(shift(monday, from = 10 * 60, minutes = 15, id = 1))
        val plan = Planner.plan(monday, activities, overrides)
        val a = plan.first { it.activity.id == 1L }
        val b = plan.first { it.activity.id == 2L }
        assertEquals(9 * 60, a.start)
        assertEquals(0, a.shift)
        assertEquals(11 * 60 + 15, b.start)
        assertEquals(12 * 60 + 15, b.end)
        assertEquals(15, b.shift)
    }

    @Test
    fun delaysAccumulateInOrder() {
        val overrides = listOf(
            shift(monday, from = 0, minutes = 10, id = 1),
            shift(monday, from = 9 * 60 + 20, minutes = 5, id = 2),
        )
        val plan = Planner.plan(monday, activities, overrides)
        assertEquals(9 * 60 + 10, plan[0].start) // 9:10 < 9:20 → solo el primer retraso
        assertEquals(11 * 60 + 15, plan[1].start) // 11:10 → +5
        assertEquals(15, Planner.overridesFor(monday.toEpochDay(), overrides).totalShift)
    }

    @Test
    fun delayNearMidnightStaysInsideTheDay() {
        val late = activity(id = 3, start = 23 * 60 + 30, end = 23 * 60 + 59)
        val plan = Planner.plan(monday, listOf(late), listOf(shift(monday, from = 0, minutes = 60, id = 1)))
        assertEquals(24 * 60 - 1, plan[0].start)
        assertEquals(24 * 60, plan[0].end)
    }
}
