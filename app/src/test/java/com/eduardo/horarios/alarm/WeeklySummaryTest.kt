package com.eduardo.horarios.alarm

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class WeeklySummaryTest {
    @Test
    fun nextSundayAtSeven() {
        // Miércoles → domingo de esa semana
        assertEquals(LocalDateTime.of(2026, 9, 27, 19, 0), WeeklySummary.nextTrigger(LocalDateTime.of(2026, 9, 23, 10, 0)))
        // Domingo antes de las 19:00 → ese mismo día
        assertEquals(LocalDateTime.of(2026, 9, 27, 19, 0), WeeklySummary.nextTrigger(LocalDateTime.of(2026, 9, 27, 18, 30)))
        // Domingo después → el siguiente
        assertEquals(LocalDateTime.of(2026, 10, 4, 19, 0), WeeklySummary.nextTrigger(LocalDateTime.of(2026, 9, 27, 19, 30)))
    }
}
