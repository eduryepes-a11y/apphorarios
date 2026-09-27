package com.eduardo.horarios.alarm

import com.eduardo.horarios.data.ActivityEntity
import com.eduardo.horarios.data.WeekParity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RotationAlarmTest {
    private val zone = ZoneId.systemDefault()
    private val wednesday = LocalDate.of(2026, 9, 30)

    private fun millis(d: LocalDate, minute: Int) = d.atStartOfDay(zone).plusMinutes(minute.toLong()).toInstant().toEpochMilli()

    @Test
    fun twoOnTwoOffOnMondays() {
        // 2 sí, 2 no desde el miércoles 30: el lunes 28 no toca (antes, en descanso) y el lunes 5 sí
        val a = ActivityEntity(
            id = 3, scheduleId = 1, title = "Guardia", emoji = "🚑", daysMask = 0b1111111,
            startMinute = 9 * 60, endMinute = 21 * 60, colorIndex = 0, reminderMinutes = 60,
            rotStart = wednesday.toEpochDay(), rotOn = 2, rotOff = 2,
        )
        val sunday = millis(wednesday.minusDays(3), 12 * 60)
        assertEquals(millis(LocalDate.of(2026, 10, 5), 9 * 60), AlarmScheduler.triggerFor(a, 0, AlarmScheduler.KIND_START, sunday, emptyList()))
        // El miércoles 30 (su primer día) también tiene aviso
        assertEquals(millis(wednesday, 8 * 60), AlarmScheduler.triggerFor(a, 2, AlarmScheduler.KIND_BEFORE, sunday, emptyList()))
    }

    @Test
    fun fourWeekCycleFindsItsWeek() {
        val monday = LocalDate.of(2026, 9, 28) // semana A del ciclo de 4
        val d = ActivityEntity(
            id = 4, scheduleId = 1, title = "Noches", emoji = "🌙", daysMask = 0b0000001,
            startMinute = 22 * 60, endMinute = 23 * 60, colorIndex = 0, reminderMinutes = 0,
            weekParity = 4, weekCycle = 4,
        )
        assertEquals(WeekParity.A, WeekParity.of(monday, 4))
        val t = AlarmScheduler.triggerFor(d, 0, AlarmScheduler.KIND_START, millis(monday, 23 * 60), emptyList())
        assertEquals(millis(monday.plusWeeks(3), 22 * 60), t)
    }
}
