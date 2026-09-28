package com.eduardo.horarios.pro

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado de Horarios Pro.
 * - [active]: hay suscripción activa (o es la versión de GitHub, que lo tiene todo).
 * - [known]: ya se sabe de verdad (respuesta de Google Play o guardado de antes). Mientras no se
 *   sabe, no se bloquea ni se pide elegir horarios, para no molestar a quien sí paga.
 */
data class ProState(val active: Boolean, val known: Boolean)

object Pro {
    private const val PREFS = "pro"
    private const val KEY_ACTIVE = "active"
    private const val KEY_VERIFIED_AT = "verified_at"

    private val _state = MutableStateFlow(ProState(active = false, known = false))
    val state: StateFlow<ProState> = _state.asStateFlow()

    val isActive: Boolean get() = _state.value.active

    /** Solo para pruebas: fuerza gratis (false) o Pro (true) y no deja que la tienda lo cambie. */
    @Volatile
    private var testOverride: Boolean? = null

    /** Carga lo último que se supo y arranca la tienda (Google Play en la variante play). */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (testOverride == null && prefs.contains(KEY_ACTIVE)) {
            val active = prefs.getBoolean(KEY_ACTIVE, false)
            val fresh = ProRules.isCacheFresh(prefs.getLong(KEY_VERIFIED_AT, 0), System.currentTimeMillis())
            // Un «Pro» guardado hace demasiado sin comprobar no vale (por ejemplo, si se bloquea Google Play):
            // hasta que Google Play conteste se usa como gratis, pero sin bloquear nada
            _state.value = if (active && !fresh) ProState(false, known = false) else ProState(active, known = true)
        }
        ProBilling.init(context.applicationContext)
    }

    /** Lo llama la tienda cuando sabe si hay suscripción. */
    fun update(context: Context, active: Boolean) {
        if (testOverride != null) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, active)
            .putLong(KEY_VERIFIED_AT, System.currentTimeMillis())
            .apply()
        _state.value = ProState(active, known = true)
        // Recién comprado (o renovado): la hoja de Pro, si estaba abierta, ya no hace falta
        if (active) Paywall.close()
    }

    fun setForTests(active: Boolean) {
        testOverride = active
        _state.value = ProState(active, known = true)
        if (active) Paywall.close()
    }
}

/** Precio de Pro tal y como lo da Google Play («1,49 €») y días de prueba gratis (0 si no hay). */
data class ProOffer(val price: String, val trialDays: Int)

/** Conexión con la tienda. */
enum class StoreStatus { CONNECTING, READY, UNAVAILABLE }
