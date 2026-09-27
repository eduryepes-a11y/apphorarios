package com.eduardo.horarios.data

import org.json.JSONArray
import org.json.JSONObject

/** Actividad tal y como se guarda en un archivo de copia o de horario compartido. */
data class ActivityExport(
    val title: String,
    val emoji: String,
    val notes: String,
    val daysMask: Int,
    val startMinute: Int,
    val endMinute: Int,
    val colorIndex: Int,
    val reminderMinutes: Int,
    val doneDays: List<Long> = emptyList(),
    val onDate: Long? = null,
    val tracked: Boolean = true,
    val weekParity: Int = 0,
    val weekCycle: Int = 2,
    val rotStart: Long? = null,
    val rotOn: Int = 0,
    val rotOff: Int = 0,
)

data class ScheduleExport(
    val name: String,
    val emoji: String,
    val colorIndex: Int,
    val isActive: Boolean,
    val activities: List<ActivityExport>,
    val autoFrom: Long? = null,
    val autoTo: Long? = null,
)

/** Contenido de un archivo .json de Horarios. */
data class HorariosFile(
    val kind: String,
    val schedules: List<ScheduleExport>,
) {
    val isBackup: Boolean get() = kind == KIND_BACKUP

    companion object {
        const val KIND_BACKUP = "backup"
        const val KIND_SCHEDULE = "schedule"
    }
}

class InvalidFileException : Exception()

/**
 * Formato JSON:
 * { "app": "horarios", "format": 1, "kind": "backup" | "schedule", "schedules": [ ... ] }
 */
object BackupFormat {
    private const val FORMAT = 1

    fun toJson(kind: String, schedules: List<ScheduleExport>): String {
        val root = JSONObject()
            .put("app", "horarios")
            .put("format", FORMAT)
            .put("kind", kind)
            .put("exportedAt", System.currentTimeMillis())
        val arr = JSONArray()
        for (s in schedules) {
            val acts = JSONArray()
            for (a in s.activities) {
                val o = JSONObject()
                    .put("title", a.title)
                    .put("emoji", a.emoji)
                    .put("notes", a.notes)
                    .put("daysMask", a.daysMask)
                    .put("startMinute", a.startMinute)
                    .put("endMinute", a.endMinute)
                    .put("colorIndex", a.colorIndex)
                    .put("reminderMinutes", a.reminderMinutes)
                    .put("tracked", a.tracked)
                a.onDate?.let { o.put("onDate", it) }
                if (a.weekParity != 0) {
                    o.put("weekParity", a.weekParity)
                    o.put("weekCycle", a.weekCycle)
                }
                a.rotStart?.let {
                    o.put("rotStart", it)
                    o.put("rotOn", a.rotOn)
                    o.put("rotOff", a.rotOff)
                }
                if (a.doneDays.isNotEmpty()) o.put("doneDays", JSONArray(a.doneDays))
                acts.put(o)
            }
            arr.put(
                JSONObject()
                    .put("name", s.name)
                    .put("emoji", s.emoji)
                    .put("colorIndex", s.colorIndex)
                    .put("isActive", s.isActive)
                    .put("activities", acts)
                    .apply {
                        if (s.autoFrom != null && s.autoTo != null) {
                            put("autoFrom", s.autoFrom)
                            put("autoTo", s.autoTo)
                        }
                    }
            )
        }
        root.put("schedules", arr)
        return root.toString(2)
    }

    /** Lee y valida un archivo. Lanza [InvalidFileException] si no es de Horarios. */
    fun parse(text: String): HorariosFile {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw InvalidFileException()
        }
        if (root.optString("app") != "horarios") throw InvalidFileException()
        val arr = root.optJSONArray("schedules") ?: throw InvalidFileException()

        val schedules = mutableListOf<ScheduleExport>()
        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            val name = s.optString("name").trim().take(40)
            if (name.isEmpty()) continue
            val acts = mutableListOf<ActivityExport>()
            val jActs = s.optJSONArray("activities") ?: JSONArray()
            for (j in 0 until jActs.length()) {
                val a = jActs.optJSONObject(j) ?: continue
                val title = a.optString("title").trim().take(50)
                val start = a.optInt("startMinute", -1)
                val end = a.optInt("endMinute", -1)
                val mask = a.optInt("daysMask", 0) and 0b1111111
                if (title.isEmpty() || start !in 0..1439 || end !in 1..1440 || end <= start || mask == 0) continue
                val done = mutableListOf<Long>()
                a.optJSONArray("doneDays")?.let { d -> for (k in 0 until d.length()) done += d.optLong(k) }
                acts += ActivityExport(
                    title = title,
                    emoji = a.optString("emoji").ifBlank { "📌" }.take(8),
                    notes = a.optString("notes").take(300),
                    daysMask = mask,
                    startMinute = start,
                    endMinute = end,
                    colorIndex = a.optInt("colorIndex", 0).coerceIn(0, 9),
                    reminderMinutes = a.optInt("reminderMinutes", 10).coerceIn(-1, 24 * 60),
                    doneDays = done,
                    onDate = if (a.has("onDate")) a.optLong("onDate") else null,
                    tracked = a.optBoolean("tracked", Tracking.defaultTracked(title)),
                    weekCycle = a.optInt("weekCycle", 2).coerceIn(WeekParity.CYCLES),
                    weekParity = a.optInt("weekParity", 0).takeIf { it in 0..a.optInt("weekCycle", 2).coerceIn(WeekParity.CYCLES) } ?: 0,
                    rotStart = if (a.has("rotStart") && a.optInt("rotOn", 0) > 0) a.optLong("rotStart") else null,
                    rotOn = a.optInt("rotOn", 0).coerceIn(0, Rotation.DAYS.last),
                    rotOff = a.optInt("rotOff", 0).coerceIn(0, Rotation.DAYS.last),
                )
            }
            schedules += ScheduleExport(
                name = name,
                emoji = s.optString("emoji").ifBlank { "📅" }.take(8),
                colorIndex = s.optInt("colorIndex", 0).coerceIn(0, 9),
                isActive = s.optBoolean("isActive", false),
                activities = acts,
                autoFrom = if (s.has("autoFrom") && s.has("autoTo")) s.optLong("autoFrom") else null,
                autoTo = if (s.has("autoFrom") && s.has("autoTo")) s.optLong("autoTo") else null,
            )
        }
        if (schedules.isEmpty()) throw InvalidFileException()
        val kind = if (root.optString("kind") == HorariosFile.KIND_SCHEDULE) HorariosFile.KIND_SCHEDULE else HorariosFile.KIND_BACKUP
        return HorariosFile(kind, schedules)
    }
}
