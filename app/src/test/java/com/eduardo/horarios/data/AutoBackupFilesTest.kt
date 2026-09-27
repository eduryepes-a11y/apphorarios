package com.eduardo.horarios.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class AutoBackupFilesTest {
    @Test
    fun namesAndDates() {
        val day = LocalDate.of(2026, 9, 27)
        assertEquals("auto-2026-09-27.json", AutoBackupFiles.nameFor(day))
        assertEquals(day, AutoBackupFiles.dateOf("auto-2026-09-27.json"))
        assertNull(AutoBackupFiles.dateOf("auto-2026-09-27.json.tmp"))
        assertNull(AutoBackupFiles.dateOf("horarios-backup-2026-09-27.json"))
        assertNull(AutoBackupFiles.dateOf("auto-2026-13-40.json"))
    }

    @Test
    fun keepsTheSevenMostRecent() {
        val start = LocalDate.of(2026, 9, 1)
        val names = (0L until 10L).map { AutoBackupFiles.nameFor(start.plusDays(it)) }.shuffled() + "otro.json"
        val pruned = AutoBackupFiles.toPrune(names)
        assertEquals(
            listOf("auto-2026-09-03.json", "auto-2026-09-02.json", "auto-2026-09-01.json"),
            pruned,
        )
    }

    @Test
    fun nothingToPruneWithFewCopies() {
        assertEquals(emptyList<String>(), AutoBackupFiles.toPrune(listOf("auto-2026-09-01.json", "auto-2026-09-02.json")))
    }
}
