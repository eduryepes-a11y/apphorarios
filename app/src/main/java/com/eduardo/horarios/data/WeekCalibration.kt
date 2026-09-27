package com.eduardo.horarios.data

import android.content.Context
import java.time.LocalDate

/**
 * Guarda el ajuste de las letras de las semanas alternas («esta semana es la A») para que
 * coincidan con las del colegio o la empresa. Uno por cada ciclo (2, 3 o 4 semanas).
 */
object WeekCalibration {
    private const val PREFS = "week_calibration"

    /** Carga el ajuste guardado. Se llama al arrancar la app, antes de programar los avisos. */
    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        WeekParity.offsets.value = WeekParity.clean(WeekParity.CYCLES.associateWith { prefs.getInt("c$it", 0) })
    }

    /** Sustituye todos los ajustes (al restaurar una copia). */
    fun replaceAll(context: Context, offsets: Map<Int, Int>) {
        val clean = WeekParity.clean(offsets)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            clear()
            clean.forEach { (c, o) -> putInt("c$c", o) }
        }.apply()
        WeekParity.offsets.value = clean
    }

    /** «Esta semana es la [parity]» en el ciclo de [cycle] semanas. Hay que reprogramar los avisos después. */
    fun setThisWeek(context: Context, cycle: Int, parity: Int, today: LocalDate = LocalDate.now()) {
        val next = WeekParity.offsets.value.toMutableMap()
        next[cycle] = WeekParity.offsetFor(today, cycle, parity)
        replaceAll(context, next)
    }
}
