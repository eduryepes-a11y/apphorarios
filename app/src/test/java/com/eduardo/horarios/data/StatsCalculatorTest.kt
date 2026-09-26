package com.eduardo.horarios.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatsCalculatorTest {

    private val monday = LocalDate.of(2026, 9, 21)
    private val wednesday = monday.plusDays(2)

    private fun act(id: Long, title: String, days: Int, start: Int, end: Int, tracked: Boolean = true) = ActivityEntity(
        id = id, scheduleId = 1, title = title, emoji = "📌", daysMask = days,
        startMinute = start, endMinute = end, colorIndex = 0, reminderMinutes = 10, tracked = tracked,
    )

    private fun skip(d: LocalDate, activityId: Long?) =
        OverrideEntity(scheduleId = 1, epochDay = d.toEpochDay(), type = OverrideEntity.TYPE_SKIP, activityId = activityId)

    private fun shift(d: LocalDate, minutes: Int, id: Long) =
        OverrideEntity(id = id, scheduleId = 1, epochDay = d.toEpochDay(), type = OverrideEntity.TYPE_SHIFT, fromMinute = 0, minutes = minutes)

    private fun done(a: Long, d: LocalDate) = CompletionEntity(a, d.toEpochDay())

    @Test
    fun weeklyGoalIsEveryPlannedDay() {
        assertEquals(0, StatsCalculator.weeklyGoal(0))
        assertEquals(1, StatsCalculator.weeklyGoal(1))
        assertEquals(3, StatsCalculator.weeklyGoal(3))
        assertEquals(7, StatsCalculator.weeklyGoal(7))
    }

    @Test
    fun weekHoursCountEverythingAndCompareWithLastWeek() {
        val work = act(1, "Trabajo", 0b0011111, 9 * 60, 10 * 60, tracked = false)
        val gym = act(2, "Gimnasio", 0b0000001, 12 * 60, 14 * 60)
        val acts = listOf(work, gym)
        val same = StatsCalculator.weekHours(monday, acts, emptyList())
        assertEquals(420, same.totalMinutes)
        assertEquals(420, same.previousMinutes)
        assertEquals(0, same.deltaMinutes)
        assertEquals(listOf("Trabajo", "Gimnasio"), same.items.map { it.title })

        // Esta semana: gimnasio saltado el lunes y el martes libre → 420 - 120 - 60
        val o = listOf(skip(monday, 2), skip(monday.plusDays(1), null))
        val less = StatsCalculator.weekHours(monday, acts, o)
        assertEquals(240, less.totalMinutes)
        assertEquals(-180, less.deltaMinutes)
    }

    @Test
    fun whatHappenedCountsDelaysSkipsAndDaysOff() {
        val work = act(1, "Trabajo", 0b0011111, 9 * 60, 10 * 60, tracked = false)
        val o = listOf(
            shift(monday, 15, 1),
            shift(wednesday, 25, 2),
            skip(monday.plusDays(3), 1),
            skip(monday.plusDays(4), null),
        )
        val h = StatsCalculator.whatHappened(monday, monday.plusDays(6), listOf(work), o)
        assertEquals(2, h.delayDays)
        assertEquals(20, h.averageDelay)
        assertEquals(1, h.skipped)
        assertEquals(1, h.daysOff)
    }

    @Test
    fun streakCountsWeeksMeetingTheGoal() {
        val gym = act(2, "Gimnasio", 0b0010101, 18 * 60, 19 * 60) // L, X, V → meta 3
        val lunch = act(3, "Comida", 0b1111111, 14 * 60, 15 * 60, tracked = false)
        val completions = listOf(
            done(2, monday),                                                        // esta semana: 1 de 3
            done(2, monday.minusDays(7)), done(2, monday.minusDays(5)), done(2, monday.minusDays(3)),   // 3 de 3 ✓
            done(2, monday.minusDays(14)), done(2, monday.minusDays(12)), done(2, monday.minusDays(10)), // 3 de 3 ✓
            done(2, monday.minusDays(21)), done(2, monday.minusDays(19)),           // 2 de 3 ✗ → corta (meta 100 %)
        )
        val r = StatsCalculator.compute(wednesday, 20 * 60, listOf(gym, lunch), completions, emptyList(), periodDays = 7)

        val habit = r.habits.single()
        assertEquals("Gimnasio", habit.activity.title)
        assertEquals(2, habit.streakWeeks) // la semana en curso aún no rompe la racha
        assertEquals(3, habit.weekPlanned)
        assertEquals(1, habit.weekDone)
        assertEquals(3, habit.weeklyGoal)

        // Últimos 7 días (17–23): vie 18, lun 21 y mié 23 → hechas el viernes y el lunes. La comida no cuenta.
        assertEquals(3, r.planned)
        assertEquals(2, r.done)
        assertEquals(66, r.percent)
    }

    @Test
    fun oneOffActivitiesAreNotHabitsButCountInConsistency() {
        val gym = act(2, "Gimnasio", 0b0010101, 18 * 60, 19 * 60)
        val dentist = act(9, "Dentista", 0b0000100, 17 * 60, 18 * 60).copy(onDate = wednesday.toEpochDay())
        val r = StatsCalculator.compute(wednesday, 20 * 60, listOf(gym, dentist), listOf(done(9, wednesday)), emptyList(), 7)
        assertEquals(listOf("Gimnasio"), r.habits.map { it.activity.title })
        assertEquals(4, r.planned) // gimnasio vie 18, lun 21, mié 23 + dentista mié 23
        assertEquals(1, r.done)
    }

    @Test
    fun skippedDaysDoNotBreakTheStreak() {
        val gym = act(2, "Gimnasio", 0b0010101, 18 * 60, 19 * 60)
        // Semana pasada: lunes y miércoles hechos; el viernes se saltó con «No hacerla» → 2 de 2 ✓
        val lastWeek = monday.minusDays(7)
        val completions = listOf(done(2, lastWeek), done(2, lastWeek.plusDays(2)))
        val overrides = listOf(skip(lastWeek.plusDays(4), 2))
        val r = StatsCalculator.compute(wednesday, 12 * 60, listOf(gym), completions, overrides, 7)
        assertEquals(1, r.habits.single().streakWeeks)
    }

    @Test
    fun heatmapCoversTwelveWeeksAndMarksTheFuture() {
        val gym = act(2, "Gimnasio", 0b1111111, 8 * 60, 9 * 60)
        val r = StatsCalculator.compute(wednesday, 12 * 60, listOf(gym), listOf(done(2, wednesday)), emptyList(), 7)
        assertEquals(StatsCalculator.HEATMAP_WEEKS * 7, r.heatmap.size)
        assertEquals(monday.minusWeeks(11), r.heatmap.first().date)
        val today = r.heatmap.first { it.date == wednesday }
        assertEquals(1, today.planned)
        assertEquals(1, today.done)
        assertTrue(r.heatmap.first { it.date == wednesday.plusDays(1) }.future)
        assertFalse(today.future)
    }

    @Test
    fun csvHasOneRowPerActivityAndDay() {
        val a = act(1, "Leer, estudiar", 0b0000001, 9 * 60, 10 * 60)
        val csv = StatsCalculator.csv(monday, monday.plusDays(6), listOf(a), listOf(done(1, monday)), listOf(shift(monday, 10, 1)))
        val lines = csv.trim().lines()
        assertEquals("date,activity,start,end,delay_min,status,tracked", lines[0])
        assertEquals(2, lines.size)
        assertEquals("2026-09-21,\"Leer, estudiar\",09:10,10:10,10,done,yes", lines[1])
    }
}
