package com.eduardo.horarios.pro

import com.eduardo.horarios.data.ActivityEntity
import com.eduardo.horarios.data.ScheduleEntity
import com.eduardo.horarios.data.WeekParity
import com.eduardo.horarios.data.isRotation

/** Por qué está bloqueado un horario en la versión gratis. */
enum class LockReason {
    /** Usa algo de Pro: semanas alternas, turnos o fechas automáticas. */
    PRO_FEATURE,
    /** Pasa del límite de horarios gratis (no se eligió al terminar Pro). */
    OVER_LIMIT,
}

/** Cómo queda la app según se pague Pro o no. */
data class FreeState(
    val isPro: Boolean,
    /** Horarios bloqueados 🔒 y por qué. */
    val locked: Map<Long, LockReason>,
    /** Al terminar Pro hay más de [ProRules.FREE_SCHEDULES] horarios normales: hay que elegir cuáles se quedan. */
    val needsChoice: Boolean,
    /** Horarios normales entre los que elegir (si [needsChoice]). */
    val choosable: List<ScheduleEntity>,
    /** ¿Se puede crear (o importar, o duplicar) un horario más sin Pro? */
    val canCreate: Boolean,
    /** ¿Ya se sabe si hay suscripción? Si no, no se pide elegir ni se bloquea nada todavía. */
    val known: Boolean = true,
) {
    fun isLocked(scheduleId: Long): Boolean = scheduleId in locked
}

/**
 * Qué es gratis y qué es Pro. Sin Android: se prueba con JUnit.
 *
 * Gratis: hasta 2 horarios, la misma semana siempre, actividades de un solo día, avisos, Hoy/Semana,
 * hecho/saltado, «Voy con retraso», días libres, copia manual y Progreso de 7 y 28 días.
 * Pro: horarios ilimitados, semanas alternas, turnos, fechas automáticas, copia en una carpeta,
 * Progreso completo (90 días, constancia, CSV) y todos los colores.
 *
 * Si se deja de pagar no se borra nada: lo que usa Pro y lo que pasa del límite queda bloqueado 🔒
 * (no se puede activar, ver ni editar y no avisa) y vuelve tal cual al volver a Pro.
 */
object ProRules {
    const val FREE_SCHEDULES = 2

    /** Colores de la app gratis (los primeros de la lista). */
    const val FREE_ACCENTS = 3

    /** Periodos de Progreso gratis (días). */
    val FREE_PERIODS = setOf(7, 28)

    /** ¿Esta actividad usa algo de Pro? */
    fun usesPro(a: ActivityEntity): Boolean =
        a.onDate == null && (a.weekParity != WeekParity.EVERY || a.isRotation)

    /** ¿Este horario usa algo de Pro (fechas automáticas o alguna actividad Pro)? */
    fun usesPro(s: ScheduleEntity, activities: List<ActivityEntity>): Boolean =
        s.hasAutoRange || activities.any { it.scheduleId == s.id && usesPro(it) }

    fun isAccentFree(index: Int): Boolean = index in 0 until FREE_ACCENTS

    fun evaluate(isPro: Boolean, schedules: List<ScheduleEntity>, activities: List<ActivityEntity>): FreeState {
        if (isPro) return FreeState(true, emptyMap(), needsChoice = false, choosable = emptyList(), canCreate = true)
        val locked = LinkedHashMap<Long, LockReason>()
        val normal = mutableListOf<ScheduleEntity>()
        for (s in schedules.sortedBy { it.createdAt }) {
            when {
                usesPro(s, activities) -> locked[s.id] = LockReason.PRO_FEATURE
                s.freeLocked -> locked[s.id] = LockReason.OVER_LIMIT
                else -> normal += s
            }
        }
        val needsChoice = normal.size > FREE_SCHEDULES
        return FreeState(
            isPro = false,
            locked = locked,
            needsChoice = needsChoice,
            choosable = if (needsChoice) normal else emptyList(),
            canCreate = normal.size < FREE_SCHEDULES,
        )
    }

    /** ¿Es válida la elección de horarios que se quedan? Exactamente [FREE_SCHEDULES] de los elegibles. */
    fun isValidChoice(state: FreeState, keep: Set<Long>): Boolean =
        state.needsChoice && keep.size == FREE_SCHEDULES && keep.all { id -> state.choosable.any { it.id == id } }

    /** Horarios que quedan bloqueados al elegir [keep]. */
    fun toLockAfterChoice(state: FreeState, keep: Set<Long>): List<Long> =
        state.choosable.map { it.id }.filter { it !in keep }

    /**
     * Al importar sin Pro: cuáles de los horarios que llegan (en orden) quedan bloqueados por el límite.
     * [unlockedNormal]: horarios normales sin bloquear que ya hay. [usesPro]: si cada uno que llega usa Pro
     * (esos ya se bloquean solos y no cuentan).
     */
    fun importLocks(isPro: Boolean, unlockedNormal: Int, usesPro: List<Boolean>): List<Boolean> {
        if (isPro) return usesPro.map { false }
        var free = (FREE_SCHEDULES - unlockedNormal).coerceAtLeast(0)
        return usesPro.map { pro ->
            when {
                pro -> false
                free > 0 -> {
                    free--
                    false
                }
                else -> true
            }
        }
    }

    /** Horario a activar si el activo queda bloqueado: el más antiguo de los libres, o ninguno. */
    fun fallbackActive(state: FreeState, schedules: List<ScheduleEntity>): Long? =
        schedules.sortedBy { it.createdAt }.firstOrNull { !state.isLocked(it.id) }?.id

    /** Oferta con la que se lanza la compra: la que tiene prueba gratis (fase a 0) si la hay; si no, la primera. */
    fun <T> pickOffer(offers: List<T>, hasFreePhase: (T) -> Boolean): T? =
        offers.firstOrNull(hasFreePhase) ?: offers.firstOrNull()
}
