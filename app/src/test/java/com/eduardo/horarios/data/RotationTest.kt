package com.eduardo.horarios.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RotationTest {
    private val start = LocalDate.of(2026, 10, 1)

    private fun shift(on: Int, off: Int) = ActivityEntity(
        id = 1, scheduleId = 1, title = "Turno", emoji = "🏥", daysMask = 0b1111111,
        startMinute = 8 * 60, endMinute = 15 * 60, colorIndex = 0, reminderMinutes = 30,
        rotStart = start.toEpochDay(), rotOn = on, rotOff = off,
    )

    @Test
    fun fourOnFourOff() {
        val a = shift(4, 4)
        val days = (0L until 16L).map { a.occursOn(start.plusDays(it)) }
        assertEquals(
            listOf(true, true, true, true, false, false, false, false, true, true, true, true, false, false, false, false),
            days,
        )
    }

    @Test
    fun alsoWorksBeforeTheStartDate() {
        val a = shift(4, 4)
        assertFalse(a.occursOn(start.minusDays(1)))
        assertFalse(a.occursOn(start.minusDays(4)))
        assertTrue(a.occursOn(start.minusDays(5)))
        assertTrue(a.occursOn(start.minusDays(8)))
    }

    @Test
    fun ignoresTheWeekdaysAndShowsInThePlan() {
        val a = shift(2, 2).copy(daysMask = 0b0000001) // aunque la máscara diga solo lunes
        assertTrue(a.isRotation)
        assertEquals((0..6).toList(), a.weekDays())
        assertEquals(1, Planner.plan(start.plusDays(1), listOf(a), emptyList()).size)
        assertEquals(0, Planner.plan(start.plusDays(2), listOf(a), emptyList()).size)
    }

    @Test
    fun zeroDaysOnNeverOccurs() {
        val a = shift(0, 3)
        assertFalse(a.isRotation)
    }
}
