package com.eduardo.horarios.alarm

import com.eduardo.horarios.data.ActivityEntity
import com.eduardo.horarios.data.OverrideEntity
import com.eduardo.horarios.data.WeekParity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AlternateWeekAlarmTest {
    private val zone = ZoneId.systemDefault()
    private val mondayA = LocalDate.of(2026, 9, 28)
    private val mondayB = LocalDate.of(2026, 10, 5)
    private val padelB = ActivityEntity(
        id = 7, scheduleId = 1, title = "Pádel", emoji = "🎾", daysMask = 0b0000001,
        startMinute = 18 * 60, endMinute = 19 * 60, colorIndex = 0, reminderMinutes = 15, weekParity = WeekParity.B,
    )

    private fun millis(d: LocalDate, minute: Int) = d.atStartOfDay(zone).plusMinutes(minute.toLong()).toInstant().toEpochMilli()

    @Test
    fun skipsTheWeekThatIsNotItsOwn() {
        val sundayBefore = millis(mondayA.minusDays(1), 12 * 60)
        assertEquals(millis(mondayB, 18 * 60), AlarmScheduler.triggerFor(padelB, 0, AlarmScheduler.KIND_START, sundayBefore, emptyList()))
        assertEquals(millis(mondayB, 18 * 60 - 15), AlarmScheduler.triggerFor(padelB, 0, AlarmScheduler.KIND_BEFORE, sundayBefore, emptyList()))
    }

    @Test
    fun afterFiringTheNextOneIsTwoWeeksLater() {
        val justAfter = millis(mondayB, 18 * 60) + 60_000
        assertEquals(millis(mondayB.plusWeeks(2), 18 * 60), AlarmScheduler.triggerFor(padelB, 0, AlarmScheduler.KIND_START, justAfter, emptyList()))
    }

    @Test
    fun skippedDayMovesToTheNextBWeek() {
        val skip = OverrideEntity(scheduleId = 1, epochDay = mondayB.toEpochDay(), type = OverrideEntity.TYPE_SKIP, activityId = 7)
        val before = millis(mondayA, 9 * 60)
        assertEquals(millis(mondayB.plusWeeks(2), 18 * 60), AlarmScheduler.triggerFor(padelB, 0, AlarmScheduler.KIND_START, before, listOf(skip)))
    }
}
