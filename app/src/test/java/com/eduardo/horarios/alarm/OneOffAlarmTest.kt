package com.eduardo.horarios.alarm

import com.eduardo.horarios.data.ActivityEntity
import com.eduardo.horarios.data.OverrideEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class OneOffAlarmTest {
    private val zone = ZoneId.systemDefault()
    private val date = LocalDate.of(2026, 10, 7) // miércoles
    private val dentist = ActivityEntity(
        id = 5, scheduleId = 1, title = "Dentista", emoji = "🦷", daysMask = 0b0000100,
        startMinute = 17 * 60, endMinute = 18 * 60, colorIndex = 0, reminderMinutes = 30,
        onDate = date.toEpochDay(),
    )

    private fun millis(d: LocalDate, minute: Int) = d.atStartOfDay(zone).plusMinutes(minute.toLong()).toInstant().toEpochMilli()

    @Test
    fun firesOnItsDay() {
        val before = millis(date.minusDays(3), 9 * 60)
        assertEquals(millis(date, 17 * 60 - 30), AlarmScheduler.triggerFor(dentist, 2, AlarmScheduler.KIND_BEFORE, before, emptyList()))
        assertEquals(millis(date, 17 * 60), AlarmScheduler.triggerFor(dentist, 2, AlarmScheduler.KIND_START, before, emptyList()))
    }

    @Test
    fun noAlarmOncePassed() {
        val after = millis(date, 18 * 60)
        assertEquals(AlarmScheduler.NO_ALARM.toLong(), AlarmScheduler.triggerFor(dentist, 2, AlarmScheduler.KIND_START, after, emptyList()))
    }

    @Test
    fun noAlarmIfSkipped() {
        val skip = OverrideEntity(scheduleId = 1, epochDay = date.toEpochDay(), type = OverrideEntity.TYPE_SKIP, activityId = 5)
        val before = millis(date.minusDays(1), 9 * 60)
        assertEquals(AlarmScheduler.NO_ALARM.toLong(), AlarmScheduler.triggerFor(dentist, 2, AlarmScheduler.KIND_START, before, listOf(skip)))
    }

    @Test
    fun delayMovesTheAlarm() {
        val shift = OverrideEntity(id = 1, scheduleId = 1, epochDay = date.toEpochDay(), type = OverrideEntity.TYPE_SHIFT, fromMinute = 0, minutes = 20)
        val before = millis(date.minusDays(1), 9 * 60)
        val t = AlarmScheduler.triggerFor(dentist, 2, AlarmScheduler.KIND_START, before, listOf(shift))
        assertEquals(millis(date, 17 * 60 + 20), t)
        assertTrue(t > before)
    }
}
