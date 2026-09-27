package com.eduardo.horarios.pro

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Variante GitHub: sin tienda. Todo desbloqueado. */
object ProBilling {
    const val canPurchase = false
    val offer: StateFlow<ProOffer?> = MutableStateFlow(null)
    val status: StateFlow<StoreStatus> = MutableStateFlow(StoreStatus.UNAVAILABLE)

    fun init(context: Context) {
        Pro.update(context, true)
    }

    fun refresh() = Unit

    fun launchPurchase(activity: Activity): Boolean = false

    fun manageUrl(context: Context): String? = null
}
