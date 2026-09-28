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

    /** Orden estable: por fecha de creación y, si coinciden, por id. */
    val oldestFirst = compareBy<ScheduleEntity>({ it.createdAt }, { it.id })

    /**
     * [known] = false: aún no se sabe si hay suscripción (Google Play no ha contestado). Entonces no se
     * bloquea nada ni se pide elegir, para no molestar a quien sí paga; solo se limita crear horarios nuevos.
     */
    fun evaluate(isPro: Boolean, schedules: List<ScheduleEntity>, activities: List<ActivityEntity>, known: Boolean = true): FreeState {
        if (isPro) return FreeState(true, emptyMap(), needsChoice = false, choosable = emptyList(), canCreate = true, known = known)
        if (!known) {
            return FreeState(false, emptyMap(), needsChoice = false, choosable = emptyList(), canCreate = schedules.size < FREE_SCHEDULES, known = false)
        }
        val locked = LinkedHashMap<Long, LockReason>()
        val normal = mutableListOf<ScheduleEntity>()
        for (s in schedules.sortedWith(oldestFirst)) {
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
        schedules.sortedWith(oldestFirst).firstOrNull { !state.isLocked(it.id) }?.id

    /**
     * Para quitar Pro hacen falta [LAPSE_CONFIRMATIONS] respuestas seguidas de Google Play sin compra
     * (una sola puede ser una caché vieja o una consulta que se cruzó con la compra).
     */
    const val LAPSE_CONFIRMATIONS = 2

    fun confirmsLapse(consecutiveNegatives: Int): Boolean = consecutiveNegatives >= LAPSE_CONFIRMATIONS

    /**
     * Días que se fía de un «Pro activo» guardado sin volver a comprobarlo con Google Play (por ejemplo,
     * sin internet). Corto a propósito: la app no sabe cuándo acaba el mes pagado, así que quitar internet
     * al final de la suscripción da como mucho estos días de más.
     */
    const val CACHE_DAYS = 3

    fun isCacheFresh(verifiedAt: Long, now: Long): Boolean =
        verifiedAt in 1..now && now - verifiedAt <= CACHE_DAYS * 24L * 60 * 60 * 1000

    /** Días de un periodo de Google Play: «P7D» → 7, «P1W» → 7, «P1M» → 30, «P1Y» → 365. 0 si no se entiende. */
    fun periodDays(period: String): Int {
        val m = Regex("""P(\d+)([DWMY])""").matchEntire(period.trim()) ?: return 0
        val n = m.groupValues[1].toInt()
        return when (m.groupValues[2]) {
            "D" -> n
            "W" -> n * 7
            "M" -> n * 30
            else -> n * 365
        }
    }

    /** Una compra, con lo que importa para saber si da Pro. */
    data class PurchaseInfo(val products: List<String>, val purchased: Boolean, val acknowledged: Boolean)

    /** ¿Alguna compra comprada (no pendiente) de [productId] da Pro? */
    fun isEntitled(purchases: List<PurchaseInfo>, productId: String): Boolean =
        purchases.any { productId in it.products && it.purchased }

    /** Compras de [productId] ya pagadas que aún hay que confirmar (Google las anula a los 3 días si no). */
    fun needsAcknowledge(purchases: List<PurchaseInfo>, productId: String): List<Int> =
        purchases.mapIndexedNotNull { i, p -> if (productId in p.products && p.purchased && !p.acknowledged) i else null }

    /** Oferta con la que se lanza la compra: la que tiene prueba gratis (fase a 0) si la hay; si no, la primera. */
    fun <T> pickOffer(offers: List<T>, hasFreePhase: (T) -> Boolean): T? =
        offers.firstOrNull(hasFreePhase) ?: offers.firstOrNull()
}
