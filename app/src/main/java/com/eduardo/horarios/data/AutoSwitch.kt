package com.eduardo.horarios.data

/** Qué hacer al cambiar de día: qué horario activar (si hay que cambiar) y cuál es el horario «de siempre». */
data class SwitchDecision(val activateId: Long?, val baseId: Long?)

/**
 * Horarios que se activan solos entre dos fechas (vacaciones, exámenes…). Sin Android: se prueba con JUnit.
 *
 * Solo actúa en los cambios: cuando empieza un periodo activa su horario y recuerda el que había
 * (el «de siempre»); cuando termina, vuelve a ese. Si en medio el usuario activa otro a mano, se respeta.
 */
object ScheduleSwitcher {

    /** Horario con fechas automáticas que cubre [epochDay]; si se solapan, el periodo más corto. */
    fun ownerOn(epochDay: Long, schedules: List<ScheduleEntity>): ScheduleEntity? =
        schedules
            .filter { s -> s.hasAutoRange && epochDay >= s.autoFrom!! && epochDay <= s.autoTo!! }
            .minWithOrNull(compareBy<ScheduleEntity> { it.autoTo!! - it.autoFrom!! }.thenBy { it.createdAt })

    /**
     * [prevOwnerId]: horario de fechas que mandaba antes (el día anterior o con las fechas de antes).
     * [nowOwnerId]: el que manda ahora. [activeId]: el activo. [baseId]: el «de siempre» guardado.
     */
    fun decide(
        prevOwnerId: Long?,
        nowOwnerId: Long?,
        activeId: Long?,
        baseId: Long?,
        existingIds: Set<Long>,
    ): SwitchDecision {
        if (prevOwnerId == nowOwnerId) return SwitchDecision(null, baseId)
        if (nowOwnerId != null) {
            // Empieza un periodo: se guarda el de siempre (si venimos de uno normal) y se activa el del periodo
            val base = if (prevOwnerId == null) activeId else baseId
            return SwitchDecision(if (nowOwnerId != activeId) nowOwnerId else null, base)
        }
        // Termina un periodo: se vuelve al de siempre, salvo que el usuario haya cambiado a mano
        val restore = baseId?.takeIf { activeId == prevOwnerId && it != activeId && it in existingIds }
        return SwitchDecision(restore, null)
    }

    /** Decisión al pasar del día [lastDay] (null = nunca se ha comprobado) a [today]. */
    fun onNewDay(lastDay: Long?, today: Long, schedules: List<ScheduleEntity>, activeId: Long?, baseId: Long?): SwitchDecision =
        decide(
            prevOwnerId = lastDay?.let { ownerOn(it, schedules)?.id },
            nowOwnerId = ownerOn(today, schedules)?.id,
            activeId = activeId,
            baseId = baseId,
            existingIds = schedules.map { it.id }.toSet(),
        )

    /** Decisión al cambiar las fechas de un horario ([before] y [after] son la lista antes y después). */
    fun onRangesChanged(today: Long, before: List<ScheduleEntity>, after: List<ScheduleEntity>, activeId: Long?, baseId: Long?): SwitchDecision =
        decide(
            prevOwnerId = ownerOn(today, before)?.id,
            nowOwnerId = ownerOn(today, after)?.id,
            activeId = activeId,
            baseId = baseId,
            existingIds = after.map { it.id }.toSet(),
        )
}
