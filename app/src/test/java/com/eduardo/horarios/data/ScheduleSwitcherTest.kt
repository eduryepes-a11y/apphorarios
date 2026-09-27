package com.eduardo.horarios.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ScheduleSwitcherTest {
    private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day).toEpochDay()

    private val normal = ScheduleEntity(id = 1, name = "Clases", emoji = "📚", colorIndex = 0, isActive = true, createdAt = 1)
    private val holidays = ScheduleEntity(
        id = 2, name = "Vacaciones", emoji = "🏖️", colorIndex = 1, createdAt = 2,
        autoFrom = d(8, 1), autoTo = d(8, 15),
    )
    private val exams = ScheduleEntity(
        id = 3, name = "Exámenes", emoji = "📝", colorIndex = 2, createdAt = 3,
        autoFrom = d(8, 10), autoTo = d(8, 12),
    )
    private val all = listOf(normal, holidays, exams)

    @Test
    fun ownerIsTheShortestRangeThatCoversTheDay() {
        assertNull(ScheduleSwitcher.ownerOn(d(7, 31), all))
        assertEquals(2L, ScheduleSwitcher.ownerOn(d(8, 1), all)?.id)
        assertEquals(3L, ScheduleSwitcher.ownerOn(d(8, 11), all)?.id)
        assertEquals(2L, ScheduleSwitcher.ownerOn(d(8, 15), all)?.id)
        assertNull(ScheduleSwitcher.ownerOn(d(8, 16), all))
    }

    @Test
    fun rangeStartActivatesAndRemembersTheUsualOne() {
        val r = ScheduleSwitcher.onNewDay(d(7, 31), d(8, 1), all, activeId = 1, baseId = null)
        assertEquals(SwitchDecision(activateId = 2, baseId = 1), r)
    }

    @Test
    fun rangeEndGoesBackToTheUsualOne() {
        val r = ScheduleSwitcher.onNewDay(d(8, 15), d(8, 16), all, activeId = 2, baseId = 1)
        assertEquals(SwitchDecision(activateId = 1, baseId = null), r)
    }

    @Test
    fun nothingChangesInTheMiddleOfARange() {
        val r = ScheduleSwitcher.onNewDay(d(8, 2), d(8, 3), all, activeId = 2, baseId = 1)
        assertEquals(SwitchDecision(activateId = null, baseId = 1), r)
    }

    @Test
    fun manualChangeDuringTheRangeIsRespected() {
        // El usuario activó «Clases» a mano en medio de las vacaciones: no se vuelve a cambiar
        val middle = ScheduleSwitcher.onNewDay(d(8, 2), d(8, 3), all, activeId = 1, baseId = 1)
        assertNull(middle.activateId)
        // …y al terminar tampoco
        val end = ScheduleSwitcher.onNewDay(d(8, 15), d(8, 16), all, activeId = 1, baseId = 1)
        assertNull(end.activateId)
        assertNull(end.baseId)
    }

    @Test
    fun nestedRangesGoBackStepByStep() {
        // Vacaciones → Exámenes (dentro) → Vacaciones → Clases
        val toExams = ScheduleSwitcher.onNewDay(d(8, 9), d(8, 10), all, activeId = 2, baseId = 1)
        assertEquals(SwitchDecision(3, 1), toExams)
        val backToHolidays = ScheduleSwitcher.onNewDay(d(8, 12), d(8, 13), all, activeId = 3, baseId = 1)
        assertEquals(SwitchDecision(2, 1), backToHolidays)
        val backToNormal = ScheduleSwitcher.onNewDay(d(8, 15), d(8, 16), all, activeId = 2, baseId = 1)
        assertEquals(SwitchDecision(1, null), backToNormal)
    }

    @Test
    fun phoneOffForDaysStillSwitches() {
        // Última comprobación antes de las vacaciones y la siguiente en medio
        val r = ScheduleSwitcher.onNewDay(d(7, 20), d(8, 5), all, activeId = 1, baseId = null)
        assertEquals(SwitchDecision(2, 1), r)
        // Y si se salta todo el periodo, no cambia nada
        val skipped = ScheduleSwitcher.onNewDay(d(7, 20), d(8, 20), all, activeId = 1, baseId = null)
        assertEquals(SwitchDecision(null, null), skipped)
    }

    @Test
    fun deletedUsualScheduleIsNotRestored() {
        val withoutNormal = listOf(holidays.copy(isActive = true))
        val r = ScheduleSwitcher.onNewDay(d(8, 15), d(8, 16), withoutNormal, activeId = 2, baseId = 1)
        assertEquals(SwitchDecision(null, null), r)
    }

    @Test
    fun savingARangeThatCoversTodayActivatesItNow() {
        val before = listOf(normal, holidays.copy(autoFrom = null, autoTo = null))
        val r = ScheduleSwitcher.onRangesChanged(d(8, 5), before, listOf(normal, holidays), activeId = 1, baseId = null)
        assertEquals(SwitchDecision(2, 1), r)
    }

    @Test
    fun removingTheRangeTodayGoesBack() {
        val after = listOf(normal, holidays.copy(autoFrom = null, autoTo = null))
        val r = ScheduleSwitcher.onRangesChanged(d(8, 5), listOf(normal, holidays), after, activeId = 2, baseId = 1)
        assertEquals(SwitchDecision(1, null), r)
    }

    @Test
    fun futureRangeDoesNothingYet() {
        val before = listOf(normal, holidays.copy(autoFrom = null, autoTo = null))
        val r = ScheduleSwitcher.onRangesChanged(d(7, 1), before, listOf(normal, holidays), activeId = 1, baseId = null)
        assertEquals(SwitchDecision(null, null), r)
    }
}
