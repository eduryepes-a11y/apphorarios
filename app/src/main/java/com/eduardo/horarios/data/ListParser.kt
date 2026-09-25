package com.eduardo.horarios.data

/** Una línea de texto convertida en actividad. */
data class ParsedItem(val title: String, val start: Int?, val end: Int?)

/**
 * Convierte una lista pegada en actividades. Admite, por línea:
 *   «Matemáticas», «9:00 Matemáticas», «9:00-10:30 Matemáticas», «Matemáticas 9:00 - 10:30»,
 * con o sin viñetas («- », «• », «1. »).
 */
object ListParser {
    private val bullet = Regex("""^\s*(?:[-*•·]|\d+[.)])\s+""")
    private const val TIME = """(\d{1,2})[:.h](\d{2})"""
    private const val SEP = """\s*(?:-|–|—|a|to|hasta)\s*"""
    private val leading = Regex("""^$TIME(?:$SEP$TIME)?\s*[-–—:·,]?\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val trailing = Regex("""^(.+?)\s*[-–—:·,]?\s*$TIME(?:$SEP$TIME)?$""", RegexOption.IGNORE_CASE)

    private fun minutes(h: String, m: String): Int? {
        val hh = h.toIntOrNull() ?: return null
        val mm = m.toIntOrNull() ?: return null
        return if (hh in 0..23 && mm in 0..59) hh * 60 + mm else null
    }

    fun parseLine(raw: String): ParsedItem? {
        val line = raw.replace(bullet, "").trim()
        if (line.isEmpty()) return null
        leading.matchEntire(line)?.let { m ->
            val g = m.groupValues
            val start = minutes(g[1], g[2])
            val end = if (g[3].isNotEmpty()) minutes(g[3], g[4]) else null
            val title = g[5].trim()
            if (start != null && title.isNotEmpty()) return ParsedItem(title, start, end)
        }
        trailing.matchEntire(line)?.let { m ->
            val g = m.groupValues
            val title = g[1].trim()
            val start = minutes(g[2], g[3])
            val end = if (g[4].isNotEmpty()) minutes(g[4], g[5]) else null
            if (start != null && title.isNotEmpty()) return ParsedItem(title, start, end)
        }
        return ParsedItem(line, null, null)
    }

    fun parse(text: String): List<ParsedItem> = text.lines().mapNotNull { parseLine(it) }.take(50)

    /**
     * Convierte las líneas en actividades. Las que no llevan hora se colocan una detrás de otra
     * (1 h cada una) empezando en [defaultStart].
     */
    fun toActivities(items: List<ParsedItem>, daysMask: Int, defaultStart: Int = 9 * 60): List<ActivityEntity> {
        var cursor = defaultStart
        return items.mapIndexed { i, item ->
            val start = (item.start ?: cursor).coerceIn(0, 24 * 60 - 1)
            val end = item.end?.takeIf { it > start } ?: (start + 60).coerceAtMost(24 * 60)
            cursor = if (end >= 24 * 60) start else end
            ActivityEntity(
                scheduleId = 0,
                title = item.title.take(50),
                emoji = guessEmoji(item.title),
                daysMask = daysMask,
                startMinute = start,
                endMinute = end,
                colorIndex = i % 10,
                reminderMinutes = 10,
                tracked = Tracking.defaultTracked(item.title),
            )
        }
    }

    private val emojiRules = listOf(
        listOf("estudi", "study", "exam", "repas", "deberes", "homework") to "📚",
        listOf("clase", "class", "school", "cole", "uni", "instituto") to "🎓",
        listOf("gim", "gym", "entren", "workout", "pesas") to "💪",
        listOf("correr", "running", "run", "carrera") to "🏃",
        listOf("comid", "lunch", "cena", "dinner", "desayun", "breakfast", "almuerz", "merienda") to "🍽️",
        listOf("trabaj", "work", "reuni", "meeting", "oficina", "office") to "💼",
        listOf("leer", "lectura", "read", "libro", "book") to "📖",
        listOf("dormir", "sleep", "siesta", "nap", "descans") to "😴",
        listOf("compra", "shop", "súper", "super", "groceries", "grocery", "mercado", "market") to "🛒",
        listOf("limpi", "clean", "casa", "house") to "🧹",
        listOf("fútbol", "futbol", "football", "soccer", "partido") to "⚽",
        listOf("nadar", "natación", "swim", "piscina", "pool") to "🏊",
        listOf("música", "musica", "music", "guitarra", "guitar", "piano") to "🎸",
        listOf("medit", "yoga") to "🧘",
        listOf("paseo", "walk", "perro", "dog") to "🌳",
        listOf("llamar", "llamada", "phone", "call") to "📞",
        listOf("cocin", "cook") to "🍳",
        listOf("dibuj", "pint", "draw", "paint") to "🎨",
        listOf("juego", "jugar", "game", "videojuego") to "🎮",
    )

    private val wordSplit = Regex("""[^\p{L}\p{N}]+""")

    /** Elige un icono según el título. Compara con el principio de cada palabra
     *  («reunión» no debe confundirse con «uni» de universidad). */
    fun guessEmoji(title: String): String {
        val words = title.lowercase().split(wordSplit).filter { it.isNotEmpty() }
        return emojiRules.firstOrNull { (keys, _) -> keys.any { k -> words.any { it.startsWith(k) } } }?.second ?: "📌"
    }
}
