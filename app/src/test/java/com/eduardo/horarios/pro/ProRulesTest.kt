package com.eduardo.horarios.pro

import com.eduardo.horarios.data.ActivityEntity
import com.eduardo.horarios.data.ScheduleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProRulesTest {
    private fun sched(id: Long, locked: Boolean = false, auto: Boolean = false) = ScheduleEntity(
        id = id, name = "H$id", emoji = "📅", colorIndex = 0, createdAt = id, freeLocked = locked,
        autoFrom = if (auto) 20000L else null, autoTo = if (auto) 20010L else null,
    )

    private fun act(id: Long, schedule: Long, parity: Int = 0, rot: Boolean = false, onDate: Long? = null) = ActivityEntity(
        id = id, scheduleId = schedule, title = "A$id", emoji = "📚", daysMask = 0b11111,
        startMinute = 540, endMinute = 600, colorIndex = 0, reminderMinutes = 10,
        weekParity = parity, rotStart = if (rot) 20000L else null, rotOn = if (rot) 4 else 0, rotOff = if (rot) 4 else 0,
        onDate = onDate,
    )

    @Test
    fun proUnlocksEverything() {
        val s = listOf(sched(1), sched(2), sched(3, locked = true), sched(4, auto = true))
        val st = ProRules.evaluate(true, s, listOf(act(1, 1, parity = 1)))
        assertTrue(st.locked.isEmpty())
        assertFalse(st.needsChoice)
        assertTrue(st.canCreate)
    }

    @Test
    fun freeAllowsTwoSchedules() {
        assertTrue(ProRules.evaluate(false, emptyList(), emptyList()).canCreate)
        assertTrue(ProRules.evaluate(false, listOf(sched(1)), emptyList()).canCreate)
        val two = ProRules.evaluate(false, listOf(sched(1), sched(2)), emptyList())
        assertFalse(two.canCreate)
        assertFalse(two.needsChoice)
        assertTrue(two.locked.isEmpty())
    }

    @Test
    fun proFeaturesLockTheirSchedule() {
        val s = listOf(sched(1), sched(2), sched(3), sched(4, auto = true))
        val a = listOf(act(1, 2, parity = 2), act(2, 3, rot = true), act(3, 1))
        val st = ProRules.evaluate(false, s, a)
        assertEquals(LockReason.PRO_FEATURE, st.locked[2])
        assertEquals(LockReason.PRO_FEATURE, st.locked[3])
        assertEquals(LockReason.PRO_FEATURE, st.locked[4])
        assertNull(st.locked[1])
        // Solo queda 1 normal: se puede crear otro y no hay que elegir
        assertFalse(st.needsChoice)
        assertTrue(st.canCreate)
    }

    @Test
    fun oneOffActivitiesAreFreeEvenWithOldFields() {
        // Un evento de un solo día nunca es Pro (aunque tuviera campos de semanas alternas)
        assertFalse(ProRules.usesPro(act(1, 1, parity = 1, onDate = 20000)))
        assertTrue(ProRules.usesPro(act(1, 1, parity = 1)))
        assertTrue(ProRules.usesPro(act(1, 1, rot = true)))
        assertFalse(ProRules.usesPro(act(1, 1)))
    }

    @Test
    fun lapseWithMoreThanTwoNormalNeedsChoice() {
        val s = listOf(sched(1), sched(2), sched(3), sched(4))
        val st = ProRules.evaluate(false, s, emptyList())
        assertTrue(st.needsChoice)
        assertEquals(listOf(1L, 2L, 3L, 4L), st.choosable.map { it.id })
        assertFalse(st.canCreate)
        // Hay que elegir exactamente 2 de los elegibles
        assertTrue(ProRules.isValidChoice(st, setOf(2, 4)))
        assertFalse(ProRules.isValidChoice(st, setOf(2)))
        assertFalse(ProRules.isValidChoice(st, setOf(1, 2, 3)))
        assertFalse(ProRules.isValidChoice(st, setOf(2, 99)))
        assertEquals(listOf(1L, 3L), ProRules.toLockAfterChoice(st, setOf(2, 4)))
    }

    @Test
    fun afterChoiceTheRestStayLockedForGood() {
        // Elegidos 2 y 4: 1 y 3 bloqueados por el límite
        val s = listOf(sched(1, locked = true), sched(2), sched(3, locked = true), sched(4))
        val st = ProRules.evaluate(false, s, emptyList())
        assertFalse(st.needsChoice)
        assertEquals(LockReason.OVER_LIMIT, st.locked[1])
        assertEquals(LockReason.OVER_LIMIT, st.locked[3])
        assertFalse(st.canCreate)
        // Si borra uno de los libres puede crear otro nuevo…
        val afterDelete = ProRules.evaluate(false, s.filter { it.id != 4L }, emptyList())
        assertTrue(afterDelete.canCreate)
        // …pero los bloqueados siguen bloqueados (no se puede volver a elegir)
        assertEquals(setOf(1L, 3L), afterDelete.locked.keys)
        assertFalse(afterDelete.needsChoice)
        assertFalse(ProRules.isValidChoice(afterDelete, setOf(1, 2)))
    }

    @Test
    fun choiceIgnoresProFeatureSchedules() {
        val s = listOf(sched(1), sched(2), sched(3), sched(4, auto = true))
        val st = ProRules.evaluate(false, s, emptyList())
        assertTrue(st.needsChoice)
        assertEquals(listOf(1L, 2L, 3L), st.choosable.map { it.id })
        assertFalse(ProRules.isValidChoice(st, setOf(1, 4)))
    }

    @Test
    fun activeFallsBackToTheOldestFreeOne() {
        val s = listOf(sched(1, locked = true), sched(2), sched(3))
        val st = ProRules.evaluate(false, s, emptyList())
        assertEquals(2L, ProRules.fallbackActive(st, s))
        val allLocked = listOf(sched(1, auto = true))
        assertNull(ProRules.fallbackActive(ProRules.evaluate(false, allLocked, emptyList()), allLocked))
    }

    @Test
    fun importWithoutProLocksWhatExceedsTheLimit() {
        assertEquals(listOf(false, false, false), ProRules.importLocks(true, 5, listOf(false, false, true)))
        assertEquals(listOf(false, true, true), ProRules.importLocks(false, 1, listOf(false, false, false)))
        // Los que usan Pro no ocupan hueco (ya se bloquean solos)
        assertEquals(listOf(false, false, false), ProRules.importLocks(false, 0, listOf(true, false, false)))
        assertEquals(listOf(true, true), ProRules.importLocks(false, 2, listOf(false, false)))
        assertEquals(listOf(true), ProRules.importLocks(false, 3, listOf(false)))
    }

    @Test
    fun freeAccentsAndPeriods() {
        assertTrue(ProRules.isAccentFree(0))
        assertTrue(ProRules.isAccentFree(ProRules.FREE_ACCENTS - 1))
        assertFalse(ProRules.isAccentFree(ProRules.FREE_ACCENTS))
        assertFalse(ProRules.isAccentFree(-1))
        assertEquals(setOf(7, 28), ProRules.FREE_PERIODS)
    }

    @Test
    fun offerWithFreeTrialIsPreferred() {
        data class O(val name: String, val trial: Boolean)
        val offers = listOf(O("base", false), O("trial", true))
        assertEquals("trial", ProRules.pickOffer(offers) { it.trial }?.name)
        assertEquals("base", ProRules.pickOffer(listOf(O("base", false))) { it.trial }?.name)
        assertNull(ProRules.pickOffer(emptyList<O>()) { it.trial })
    }

    @Test
    fun trialPeriodsFromGooglePlay() {
        assertEquals(7, ProRules.periodDays("P7D"))
        assertEquals(7, ProRules.periodDays("P1W"))
        assertEquals(14, ProRules.periodDays("P2W"))
        assertEquals(30, ProRules.periodDays("P1M"))
        assertEquals(365, ProRules.periodDays("P1Y"))
        assertEquals(0, ProRules.periodDays(""))
        assertEquals(0, ProRules.periodDays("P1M2D")) // formato raro: mejor no inventar
    }

    @Test
    fun onlyPaidPurchasesOfOurProductGivePro() {
        val id = "horarios_pro"
        val paid = ProRules.PurchaseInfo(listOf(id), purchased = true, acknowledged = true)
        val pending = ProRules.PurchaseInfo(listOf(id), purchased = false, acknowledged = false)
        val other = ProRules.PurchaseInfo(listOf("otra_app"), purchased = true, acknowledged = false)
        assertTrue(ProRules.isEntitled(listOf(paid), id))
        assertFalse(ProRules.isEntitled(emptyList(), id))
        // Un pago pendiente (por ejemplo, en efectivo) todavía no da Pro
        assertFalse(ProRules.isEntitled(listOf(pending), id))
        assertFalse(ProRules.isEntitled(listOf(other), id))
        assertTrue(ProRules.isEntitled(listOf(pending, paid), id))
    }

    @Test
    fun paidPurchasesAreAcknowledgedOnce() {
        val id = "horarios_pro"
        val list = listOf(
            ProRules.PurchaseInfo(listOf(id), purchased = true, acknowledged = false), // hay que confirmarla
            ProRules.PurchaseInfo(listOf(id), purchased = true, acknowledged = true), // ya confirmada
            ProRules.PurchaseInfo(listOf(id), purchased = false, acknowledged = false), // pendiente: aún no
            ProRules.PurchaseInfo(listOf("otra"), purchased = true, acknowledged = false), // no es nuestra
        )
        assertEquals(listOf(0), ProRules.needsAcknowledge(list, id))
    }
}
