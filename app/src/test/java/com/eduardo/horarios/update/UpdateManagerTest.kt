package com.eduardo.horarios.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {
    @Test
    fun comparesVersions() {
        assertTrue(UpdateManager.isNewer("1.2.0", "1.1.2"))
        assertTrue(UpdateManager.isNewer("1.10.0", "1.9.9"))
        assertTrue(UpdateManager.isNewer("1.2.1-beta", "1.2.0"))
        assertFalse(UpdateManager.isNewer("1.1.2", "1.1.2"))
        assertFalse(UpdateManager.isNewer("1.2", "1.2.0"))
        assertFalse(UpdateManager.isNewer("1.1.9", "1.2.0"))
    }
}
