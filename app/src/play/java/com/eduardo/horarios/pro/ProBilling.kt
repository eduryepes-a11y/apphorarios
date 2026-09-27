package com.eduardo.horarios.pro

import android.app.Activity
import android.content.Context
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
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Variante Google Play: suscripción mensual «Horarios Pro» (1,49 €/mes con 7 días gratis; el precio y
 * la prueba se configuran en Play Console). Comprueba la suscripción al abrir la app y al volver a ella.
 */
object ProBilling : PurchasesUpdatedListener {
    /** Id del producto de suscripción en Play Console. */
    const val PRODUCT_ID = "horarios_pro"
    const val canPurchase = true

    private val _offer = MutableStateFlow<ProOffer?>(null)
    val offer: StateFlow<ProOffer?> = _offer
    private val _status = MutableStateFlow(StoreStatus.CONNECTING)
    val status: StateFlow<StoreStatus> = _status

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var appContext: Context
    private var client: BillingClient? = null
    private var details: ProductDetails? = null
    private var retries = 0

    fun init(context: Context) {
        appContext = context.applicationContext
        client = BillingClient.newBuilder(appContext)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        connect()
    }

    private fun connect() {
        val c = client ?: return
        _status.value = StoreStatus.CONNECTING
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    retries = 0
                    _status.value = StoreStatus.READY
                    refresh()
                } else {
                    _status.value = StoreStatus.UNAVAILABLE
                }
            }

            override fun onBillingServiceDisconnected() {
                _status.value = StoreStatus.UNAVAILABLE
                // Reintenta con espera creciente (1 s, 2 s, 4 s… hasta 1 min)
                if (retries < 6) {
                    val wait = 1000L shl retries
                    retries++
                    scope.launch {
                        delay(wait)
                        connect()
                    }
                }
            }
        })
    }

    /** Vuelve a comprobar la suscripción y el precio (al abrir la app o volver a ella). */
    fun refresh() {
        val c = client ?: return
        if (!c.isReady) {
            if (_status.value != StoreStatus.CONNECTING) connect()
            return
        }
        scope.launch {
            val purchases = c.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
            )
            if (purchases.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                handle(purchases.purchasesList)
            }
            val result = c.queryProductDetails(
                QueryProductDetailsParams.newBuilder().setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(PRODUCT_ID)
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build()
                    )
                ).build()
            )
            details = result.productDetailsList?.firstOrNull()
            _offer.value = details?.let(::toOffer)
        }
    }

    private fun bestOffer(d: ProductDetails): ProductDetails.SubscriptionOfferDetails? =
        ProRules.pickOffer(d.subscriptionOfferDetails.orEmpty()) { o ->
            o.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L }
        }

    private fun toOffer(d: ProductDetails): ProOffer? {
        val o = bestOffer(d) ?: return null
        val phases = o.pricingPhases.pricingPhaseList
        val paid = phases.lastOrNull { it.priceAmountMicros > 0 } ?: return null
        val trial = phases.firstOrNull { it.priceAmountMicros == 0L }
        return ProOffer(price = paid.formattedPrice, trialDays = trial?.let { trialDays(it.billingPeriod) } ?: 0)
    }

    /** «P7D» → 7, «P1W» → 7, «P1M» → 30. */
    private fun trialDays(period: String): Int {
        val m = Regex("""P(\d+)([DWM])""").matchEntire(period) ?: return 0
        val n = m.groupValues[1].toInt()
        return when (m.groupValues[2]) {
            "D" -> n
            "W" -> n * 7
            else -> n * 30
        }
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
        return c.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            scope.launch { handle(purchases) }
        }
    }

    private suspend fun handle(purchases: List<Purchase>) {
        val c = client ?: return
        val mine = purchases.filter { PRODUCT_ID in it.products }
        val active = mine.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        // Google cancela la compra si no se confirma en 3 días
        for (p in mine) {
            if (p.purchaseState == Purchase.PurchaseState.PURCHASED && !p.isAcknowledged) {
                c.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build())
            }
        }
        Pro.update(appContext, active)
    }

    /** Página de Google Play para gestionar o cancelar la suscripción. */
    fun manageUrl(context: Context): String? =
        "https://play.google.com/store/account/subscriptions?sku=$PRODUCT_ID&package=${context.packageName}"
}
