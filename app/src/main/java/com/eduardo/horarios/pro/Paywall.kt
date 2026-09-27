package com.eduardo.horarios.pro

import kotlinx.coroutines.flow.MutableStateFlow

/** Qué hizo abrir la pantalla de Pro (para explicar al usuario por qué). */
enum class PaywallReason { GENERAL, SCHEDULES, ALTERNATE, ROTATION, AUTO_DATES, BACKUP_FOLDER, STATS, COLORS, LOCKED_SCHEDULE }

/** Abre la hoja «Horarios Pro» desde cualquier pantalla. */
object Paywall {
    val open = MutableStateFlow<PaywallReason?>(null)

    fun show(reason: PaywallReason = PaywallReason.GENERAL) {
        open.value = reason
    }

    fun close() {
        open.value = null
    }
}
