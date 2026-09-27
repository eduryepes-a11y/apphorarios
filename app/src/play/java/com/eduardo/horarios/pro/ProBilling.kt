package com.eduardo.horarios.pro

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Variante Google Play: suscripción mensual «Horarios Pro».
 * El precio (1,49 €/mes) y la prueba de 7 días se configuran en Play Console, en la suscripción
 * [PRODUCT_ID] (plan base mensual + oferta de prueba gratis). Aquí solo se lee lo que diga Google.
 *
 * - Al abrir la app y al volver a ella se consulta si hay suscripción activa ([refresh]).
 * - Si Google Play no contesta, se mantiene lo último que se supo (no se bloquea a quien paga).
 * - Las compras se confirman (acknowledge): si no, Google las devuelve a los 3 días.
 *
 * Usa Play Billing Library 8 con las funciones de siempre (con «listener»), sin extensiones de Kotlin.
 */
object ProBilling : PurchasesUpdatedListener {
    /** Id de la suscripción en Play Console. */
    const val PRODUCT_ID = "horarios_pro"
    const val canPurchase = true
    private const val TAG = "HorariosPro"

    private val _offer = MutableStateFlow<ProOffer?>(null)
    val offer: StateFlow<ProOffer?> = _offer
    private val _status = MutableStateFlow(StoreStatus.CONNECTING)
    val status: StateFlow<StoreStatus> = _status

    private lateinit var appContext: Context
    private var client: BillingClient? = null
    @Volatile private var details: ProductDetails? = null
    @Volatile private var connecting = false

    fun init(context: Context) {
        appContext = context.applicationContext
        client = BillingClient.newBuilder(appContext)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            // Si Google Play corta la conexión, la librería se vuelve a conectar sola
            .enableAutoServiceReconnection()
            .build()
        connect()
    }

    private fun connect() {
        val c = client ?: return
        if (connecting) return
        connecting = true
        _status.value = StoreStatus.CONNECTING
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _status.value = StoreStatus.READY
                    refresh()
                } else {
                    Log.w(TAG, "Sin conexión con Google Play: ${result.responseCode} ${result.debugMessage}")
                    _status.value = StoreStatus.UNAVAILABLE
                }
            }

            override fun onBillingServiceDisconnected() {
                connecting = false
                _status.value = StoreStatus.UNAVAILABLE
            }
        })
    }

    /** Vuelve a comprobar la suscripción y el precio (al abrir la app o volver a ella). */
    fun refresh() {
        val c = client ?: return
        if (!c.isReady) {
            connect()
            return
        }
        // 1. ¿Hay suscripción activa?
        c.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                handle(purchases)
            } else {
                // Sin respuesta fiable: se mantiene lo último que se supo
                Log.w(TAG, "No se pudo consultar la suscripción: ${result.responseCode} ${result.debugMessage}")
            }
        }
        // 2. Precio y prueba gratis, para la hoja de Pro
        c.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            ).build()
        ) { result, queryResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val d = queryResult.productDetailsList.firstOrNull { it.productId == PRODUCT_ID }
                details = d
                _offer.value = d?.let(::toOffer)
                if (d == null) Log.w(TAG, "La suscripción $PRODUCT_ID no está en Play Console (o no está activa)")
            }
        }
    }

    /** La oferta con prueba gratis si el usuario puede usarla (Google solo devuelve las que le tocan); si no, el plan base. */
    private fun bestOffer(d: ProductDetails): ProductDetails.SubscriptionOfferDetails? =
        ProRules.pickOffer(d.subscriptionOfferDetails.orEmpty()) { o ->
            o.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L }
        }

    private fun toOffer(d: ProductDetails): ProOffer? {
        val o = bestOffer(d) ?: return null
        val phases = o.pricingPhases.pricingPhaseList
        val paid = phases.lastOrNull { it.priceAmountMicros > 0 } ?: return null
        val trial = phases.firstOrNull { it.priceAmountMicros == 0L }
        return ProOffer(price = paid.formattedPrice, trialDays = trial?.let { ProRules.periodDays(it.billingPeriod) } ?: 0)
    }

    /** Abre la compra de Google Play. Devuelve false si aún no se puede (sin conexión o sin producto). */
    fun launchPurchase(activity: Activity): Boolean {
        val c = client ?: return false
        val d = details ?: return false.also { refresh() }
        val o = bestOffer(d) ?: return false
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(d)
                        .setOfferToken(o.offerToken)
                        .build()
                )
            )
            .build()
        val result = c.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "No se pudo abrir la compra: ${result.responseCode} ${result.debugMessage}")
        }
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> if (purchases != null) handle(purchases)
            // Ya la tenía (por ejemplo, comprada en otro móvil): se vuelve a leer
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
            else -> Unit // cancelada por el usuario o error: no cambia nada
        }
    }

    private fun handle(purchases: List<Purchase>) {
        val c = client ?: return
        val info = purchases.map {
            ProRules.PurchaseInfo(
                products = it.products,
                purchased = it.purchaseState == Purchase.PurchaseState.PURCHASED,
                acknowledged = it.isAcknowledged,
            )
        }
        for (i in ProRules.needsAcknowledge(info, PRODUCT_ID)) {
            c.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchases[i].purchaseToken).build()
            ) { r ->
                if (r.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "No se pudo confirmar la compra (se reintenta al volver a la app): ${r.responseCode}")
                }
            }
        }
        Pro.update(appContext, ProRules.isEntitled(info, PRODUCT_ID))
    }

    /** Página de Google Play para gestionar o cancelar la suscripción. */
    fun manageUrl(context: Context): String? =
        "https://play.google.com/store/account/subscriptions?sku=$PRODUCT_ID&package=${context.packageName}"
}
