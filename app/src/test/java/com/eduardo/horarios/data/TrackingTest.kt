package com.eduardo.horarios.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingTest {
    @Test
    fun fixedActivitiesAreNotTracked() {
        listOf("Comida", "Cena con amigos", "Clases", "Trabajo", "Turno de noche", "Dormir", "Siesta",
            "Lunch", "Classes", "Work", "Sleep", "Wake up", "Break", "Reunión de equipo").forEach {
            assertFalse(it, Tracking.defaultTracked(it))
        }
    }

    @Test
    fun habitsAreTracked() {
        listOf("Gimnasio", "Estudiar", "Leer", "Correr", "Piano", "Dentista",
            "Gym", "Workout", "Study", "Read", "Walk the dog").forEach {
            assertTrue(it, Tracking.defaultTracked(it))
        }
    }

    @Test
    fun pastedListUsesDefaults() {
        val acts = ListParser.toActivities(ListParser.parse("Comida\nGimnasio"), daysMask = 1)
        assertFalse(acts[0].tracked)
        assertTrue(acts[1].tracked)
    }
}
