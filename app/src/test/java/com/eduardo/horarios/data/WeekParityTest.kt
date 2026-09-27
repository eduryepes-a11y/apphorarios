package com.eduardo.horarios.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeekParityTest {
    private val mondayA = LocalDate.of(2026, 9, 28)
    private val mondayB = LocalDate.of(2026, 10, 5)

    private fun act(parity: Int, mask: Int = 0b0000001) = ActivityEntity(
        id = 1, scheduleId = 1, title = "Padel", emoji = "🎾", daysMask = mask,
        startMinute = 18 * 60, endMinute = 19 * 60, colorIndex = 0, reminderMinutes = 10, weekParity = parity,
    )

    @Test
    fun weeksAlternateFromMondayToSunday() {
        assertEquals(WeekParity.A, WeekParity.of(LocalDate.of(1970, 1, 5)))
        assertEquals(WeekParity.B, WeekParity.of(LocalDate.of(1970, 1, 4)))
        assertEquals(WeekParity.A, WeekParity.of(mondayA))
        assertEquals(WeekParity.A, WeekParity.of(mondayA.plusDays(6))) // el domingo sigue siendo A
        assertEquals(WeekParity.B, WeekParity.of(mondayB))
        assertEquals(WeekParity.A, WeekParity.of(mondayB.plusWeeks(1)))
        // Antes de 1970 también alterna bien
        assertEquals(WeekParity.B, WeekParity.of(LocalDate.of(1969, 12, 29)))
    }

    @Test
    fun alternateActivitiesOnlyOnTheirWeeks() {
        assertTrue(act(WeekParity.A).occursOn(mondayA))
        assertFalse(act(WeekParity.A).occursOn(mondayB))
        assertFalse(act(WeekParity.B).occursOn(mondayA))
        assertTrue(act(WeekParity.B).occursOn(mondayB))
        assertTrue(act(WeekParity.EVERY).occursOn(mondayA))
        assertTrue(act(WeekParity.EVERY).occursOn(mondayB))
        // Y siguen respetando los días de la semana
        assertFalse(act(WeekParity.A).occursOn(mondayA.plusDays(1)))
    }

    @Test
    fun plannerSkipsTheOtherWeek() {
        val acts = listOf(act(WeekParity.A), act(WeekParity.B).copy(id = 2, title = "Inglés"))
        assertEquals(listOf("Padel"), Planner.plan(mondayA, acts, emptyList()).map { it.activity.title })
        assertEquals(listOf("Inglés"), Planner.plan(mondayB, acts, emptyList()).map { it.activity.title })
    }

    @Test
    fun cyclesOfThreeAndFourWeeks() {
        val first = LocalDate.of(1970, 1, 5)
        assertEquals(listOf(1, 2, 3, 1), (0L..3L).map { WeekParity.of(first.plusWeeks(it), 3) })
        assertEquals(listOf(1, 2, 3, 4, 1), (0L..4L).map { WeekParity.of(first.plusWeeks(it), 4) })
        assertEquals(3, WeekParity.of(mondayA, 3)) // C
        assertEquals(1, WeekParity.of(mondayB, 3)) // A
        assertEquals("C", WeekParity.letter(3))
        // Una actividad de la semana C (ciclo de 3) solo toca una semana de cada tres
        val c = act(3).copy(weekCycle = 3)
        assertTrue(c.occursOn(mondayA))
        assertFalse(c.occursOn(mondayB))
        assertFalse(c.occursOn(mondayB.plusWeeks(1)))
        assertTrue(c.occursOn(mondayA.plusWeeks(3)))
    }

    @Test
    fun oldAlternateActivitiesKeepWorking() {
        // Las de la v1.5 (A/B) tienen ciclo 2 por defecto
        assertEquals(2, act(WeekParity.B).weekCycle)
        assertTrue(act(WeekParity.B).occursOn(mondayB))
    }
}
