package com.eduardo.horarios.data

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
)

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
