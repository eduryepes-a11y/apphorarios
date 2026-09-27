package com.eduardo.horarios.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Un horario semanal (p. ej. "Semana de clase", "Vacaciones"). Solo uno puede estar activo. */
@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val colorIndex: Int,
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** Fechas automáticas (epochDay, ambas incluidas): entre ellas este horario se activa solo. */
    val autoFrom: Long? = null,
    val autoTo: Long? = null,
    /** Sin Pro: bloqueado 🔒 porque no se eligió al terminar la suscripción (ver ProRules). */
    @ColumnInfo(defaultValue = "0") val freeLocked: Boolean = false,
) {
    val hasAutoRange: Boolean get() = autoFrom != null && autoTo != null
}

/**
 * Una actividad que se repite en uno o varios días de la semana.
 * [daysMask]: bit 0 = lunes … bit 6 = domingo.
 * [startMinute]/[endMinute]: minutos desde las 00:00.
 * [reminderMinutes]: -1 = sin aviso, 0 = al empezar, N = N minutos antes.
 */
@Entity(
    tableName = "activities",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("scheduleId")],
)
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleId: Long,
    val title: String,
    val emoji: String,
    val notes: String = "",
    val daysMask: Int,
    val startMinute: Int,
    val endMinute: Int,
    val colorIndex: Int,
    val reminderMinutes: Int,
    /** Día concreto (LocalDate.toEpochDay) si es una actividad de un solo día; null = se repite cada semana. */
    val onDate: Long? = null,
    /** Si cuenta en Progreso (se puede marcar como hecha). Las fijas, como comer o dormir, no. */
    @ColumnInfo(defaultValue = "1") val tracked: Boolean = true,
    /**
     * Semanas alternas: 0 = todas las semanas; 1..[weekCycle] = qué semana del ciclo (1 = A, 2 = B, 3 = C…).
     * El nombre viene de la v1.5, cuando solo había semanas A/B.
     */
    @ColumnInfo(defaultValue = "0") val weekParity: Int = 0,
    /** Longitud del ciclo de semanas alternas (2, 3 o 4). */
    @ColumnInfo(defaultValue = "2") val weekCycle: Int = 2,
    /** Turnos por días (p. ej. 4 sí, 4 no): día en que empieza el patrón; null = no es por turnos. */
    val rotStart: Long? = null,
    /** Días seguidos que toca y días seguidos de descanso del turno. */
    @ColumnInfo(defaultValue = "0") val rotOn: Int = 0,
    @ColumnInfo(defaultValue = "0") val rotOff: Int = 0,
)

data class ScheduleCount(
    val scheduleId: Long,
    val count: Int,
)

/** Una actividad marcada como hecha un día concreto ([epochDay] = LocalDate.toEpochDay()). */
@Entity(
    tableName = "completions",
    primaryKeys = ["activityId", "epochDay"],
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activityId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class CompletionEntity(
    val activityId: Long,
    val epochDay: Long,
    val completedAt: Long = System.currentTimeMillis(),
)

/**
 * Cambio puntual en un día concreto del horario:
 * - [TYPE_SKIP]: saltar una actividad ([activityId]) o el día entero ([activityId] = null → día libre).
 * - [TYPE_SHIFT]: retrasar [minutes] las actividades que empiezan a partir de [fromMinute].
 */
@Entity(tableName = "day_overrides", indices = [Index("epochDay")])
data class OverrideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleId: Long,
    val epochDay: Long,
    val type: Int,
    val activityId: Long? = null,
    val fromMinute: Int = 0,
    val minutes: Int = 0,
) {
    companion object {
        const val TYPE_SKIP = 0
        const val TYPE_SHIFT = 1
    }
}
