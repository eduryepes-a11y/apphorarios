package com.eduardo.horarios.data

import com.eduardo.horarios.alarm.AlarmScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** «Esta semana es la A»: las letras se ajustan a las del colegio o la empresa. */
class WeekCalibrationTest {
    private val today = LocalDate.of(2026, 9, 30) // miércoles; sin ajuste es semana A (ciclo 2), C (ciclo 3), A (ciclo 4)

    @After
    fun reset() {
        WeekParity.offsets.value = emptyMap()
    }

    @Test
    fun withoutAdjustmentLettersComeFromTheCalendar() {
        assertEquals(1, WeekParity.of(today, 2))
        assertEquals(3, WeekParity.of(today, 3))
        assertEquals(1, WeekParity.of(today, 4))
    }

    @Test
    fun anyLetterCanBeThisWeekInEveryCycle() {
        for (cycle in WeekParity.CYCLES) {
            for (parity in 1..cycle) {
                WeekParity.offsets.value = mapOf(cycle to WeekParity.offsetFor(today, cycle, parity))
                assertEquals("ciclo $cycle, letra $parity", parity, WeekParity.of(today, cycle))
                // Toda la semana (lunes a domingo) tiene la misma letra
                assertEquals(parity, WeekParity.of(today.with(java.time.DayOfWeek.MONDAY), cycle))
                assertEquals(parity, WeekParity.of(today.with(java.time.DayOfWeek.SUNDAY), cycle))
                // Y las siguientes van en orden: A, B, C… y vuelta a empezar
                for (k in 1L..(2L * cycle)) {
                    val expected = Math.floorMod(parity - 1 + k.toInt(), cycle) + 1
                    assertEquals(expected, WeekParity.of(today.plusWeeks(k), cycle))
                }
                // Hacia atrás también
                assertEquals(Math.floorMod(parity - 2, cycle) + 1, WeekParity.of(today.minusWeeks(1), cycle))
            }
        }
    }

    @Test
    fun adjustingOneCycleDoesNotTouchTheOthers() {
        WeekParity.offsets.value = mapOf(3 to WeekParity.offsetFor(today, 3, 1))
        assertEquals(1, WeekParity.of(today, 3))
        assertEquals(1, WeekParity.of(today, 2))
        assertEquals(1, WeekParity.of(today, 4))
    }

    @Test
    fun activitiesMoveToTheirRealWeeks() {
        val b = ActivityEntity(
            id = 9, scheduleId = 1, title = "Natación", emoji = "🏊", daysMask = 0b0000100,
            startMinute = 17 * 60, endMinute = 18 * 60, colorIndex = 0, reminderMinutes = 15, weekParity = WeekParity.B,
        )
        assertFalse(b.occursOn(today)) // sin ajuste, hoy es A
        WeekParity.offsets.value = mapOf(2 to WeekParity.offsetFor(today, 2, WeekParity.B))
        assertTrue(b.occursOn(today)) // «esta semana es la B»
        assertFalse(b.occursOn(today.plusWeeks(1)))
        // Y el aviso también se mueve
        val zone = ZoneId.systemDefault()
        val monday = today.minusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val expected = today.atStartOfDay(zone).plusMinutes(17L * 60).toInstant().toEpochMilli()
        assertEquals(expected, AlarmScheduler.triggerFor(b, 2, AlarmScheduler.KIND_START, monday, emptyList()))
    }

    @Test
    fun cleanDropsInvalidAndNeutralValues() {
        assertEquals(mapOf(3 to 2), WeekParity.clean(mapOf(1 to 5, 2 to 0, 3 to -1, 4 to 8, 7 to 1)))
    }
}
