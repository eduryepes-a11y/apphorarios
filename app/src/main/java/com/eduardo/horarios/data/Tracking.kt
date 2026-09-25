package com.eduardo.horarios.data

/**
 * Decide si una actividad cuenta en Progreso por defecto.
 * Las fijas del día (comer, dormir, clase, trabajo…) no: nadie las va a marcar como hechas
 * y solo estropearían las estadísticas. Los hábitos (gimnasio, estudiar, leer…) sí.
 * El usuario siempre puede cambiarlo en el editor.
 */
object Tracking {
    /** Palabras que empiezan así → actividad fija. */
    private val fixedPrefixes = listOf(
        "comid", "almuerz", "cena", "desayun", "merienda", "lunch", "dinner", "breakfast", "brunch",
        "clase", "class", "colegio", "cole", "school", "instituto", "universidad", "uni",
        "trabaj", "oficina", "office", "jornada", "turno", "shift", "guardia",
        "dormir", "sleep", "siesta", "despert", "levant", "wake",
        "descans", "reuni", "meeting", "transporte", "commute", "trayecto",
    )

    /** Palabras exactas → actividad fija (evita que «work» atrape «workout»). */
    private val fixedWords = setOf("work", "rest", "break", "nap", "bed")

    private val wordSplit = Regex("""[^\p{L}\p{N}]+""")

    fun defaultTracked(title: String): Boolean {
        val words = title.lowercase().split(wordSplit).filter { it.isNotEmpty() }
        val fixed = words.any { w -> w in fixedWords || fixedPrefixes.any { w.startsWith(it) } }
        return !fixed
    }
}
