package com.eduardo.horarios.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListParserTest {

    @Test
    fun timeRangeBeforeTitle() {
        assertEquals(ParsedItem("Matemáticas", 540, 630), ListParser.parseLine("9:00-10:30 Matemáticas"))
        assertEquals(ParsedItem("Clase", 570, 660), ListParser.parseLine("9h30 a 11h00 Clase"))
    }

    @Test
    fun timeRangeAfterTitle() {
        assertEquals(ParsedItem("Matemáticas", 540, 630), ListParser.parseLine("Matemáticas 9:00 - 10:30"))
        assertEquals(ParsedItem("Llamar a mamá", 1110, null), ListParser.parseLine("Llamar a mamá 18:30"))
    }

    @Test
    fun bulletsAndSingleTime() {
        assertEquals(ParsedItem("Inglés", 1050, null), ListParser.parseLine("- 17.30 Inglés"))
        assertEquals(ParsedItem("Leer", null, null), ListParser.parseLine("• Leer"))
        assertEquals(ParsedItem("Correr", null, null), ListParser.parseLine("2) Correr"))
    }

    @Test
    fun emptyAndInvalidLines() {
        assertNull(ListParser.parseLine("   "))
        assertNull(ListParser.parseLine("25:00 Algo")?.start)
        assertEquals(3, ListParser.parse("a\n\nb\n c ").size)
    }

    @Test
    fun untimedItemsAreChained() {
        val items = ListParser.parse("A\nB\n12:00-13:00 C\nD")
        val acts = ListParser.toActivities(items, daysMask = 1)
        assertEquals(listOf(540 to 600, 600 to 660, 720 to 780, 780 to 840), acts.map { it.startMinute to it.endMinute })
    }

    @Test
    fun endBeforeStartBecomesOneHour() {
        val acts = ListParser.toActivities(listOf(ParsedItem("X", 600, 500)), daysMask = 1)
        assertEquals(660, acts[0].endMinute)
    }

    @Test
    fun emojiGuessUsesWordStarts() {
        assertEquals("💼", ListParser.guessEmoji("Reunión de equipo"))
        assertEquals("🎓", ListParser.guessEmoji("Universidad"))
        assertEquals("📚", ListParser.guessEmoji("Estudiar física"))
        assertEquals("💪", ListParser.guessEmoji("Gimnasio"))
        assertEquals("🌳", ListParser.guessEmoji("Paseo con el perro"))
        assertEquals("📌", ListParser.guessEmoji("Ir a la playa"))
    }
}
