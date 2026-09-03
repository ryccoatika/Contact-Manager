package com.ryccoatika.contactmanager.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-app product ids for the "Support development" donations. These must exist
 *  as **consumable** managed products in the Play Console with matching ids. */
val SUPPORT_PRODUCT_IDS = listOf(
    "support_coffee",
    "support_smoothie",
    "support_pizza",
    "support_fancy_meal",
)

sealed interface BillingEvent {
    /** A donation completed (already consumed, so it can be bought again). */
    data object PurchaseSuccess : BillingEvent
    data object PurchaseCancelled : BillingEvent
    data object Error : BillingEvent
}

/**
 * Thin wrapper over Play Billing for one-time, **consumable** donations. Connects
 * on [start], queries the products' localized prices, launches the purchase flow,
 * and consumes a completed purchase so the user can donate again. Donations grant
 * nothing, so there's no entitlement to persist.
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val _prices = MutableStateFlow<Map<String, String>>(emptyMap())
    /** productId → localized formatted price (e.g. "$1.00"); empty until loaded. */
    val prices: StateFlow<Map<String, String>> = _prices.asStateFlow()

    private val _events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<BillingEvent> = _events.asSharedFlow()

    private val productDetails = mutableMapOf<String, ProductDetails>()

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        when {
            result.responseCode == BillingResponseCode.OK && purchases != null ->
                purchases.forEach(::consumePurchase)
            result.responseCode == BillingResponseCode.USER_CANCELED ->
                _events.tryEmit(BillingEvent.PurchaseCancelled)
            else -> _events.tryEmit(BillingEvent.Error)
        }
    }

    private val client = BillingClient.newBuilder(context)
        .setListener(purchasesListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .build()

    /** Connect (idempotent) and refresh prices. Safe to call on every screen open. */
    fun start() {
        if (client.isReady) {
            queryProducts()
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingResponseCode.OK) queryProducts()
            }

            override fun onBillingServiceDisconnected() {
                // Reconnected lazily on the next start(); nothing to do here.
            }
        })
    }

    fun purchase(activity: Activity, productId: String) {
        val details = productDetails[productId]
        if (details == null) {
            _events.tryEmit(BillingEvent.Error)
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build(),
                ),
            )
            .build()
        client.launchBillingFlow(activity, params)
    }

    private fun queryProducts() {
        val products = SUPPORT_PRODUCT_IDS.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        client.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode != BillingResponseCode.OK) return@queryProductDetailsAsync
            val details = queryResult.productDetailsList
            productDetails.clear()
            details.forEach { productDetails[it.productId] = it }
            _prices.value = details.associate { detail ->
                detail.productId to (detail.oneTimePurchaseOfferDetails?.formattedPrice.orEmpty())
            }
        }
    }

    private fun consumePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val params = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        client.consumeAsync(params) { result, _ ->
            _events.tryEmit(
                if (result.responseCode == BillingResponseCode.OK) BillingEvent.PurchaseSuccess
                else BillingEvent.Error,
            )
        }
    }
}
