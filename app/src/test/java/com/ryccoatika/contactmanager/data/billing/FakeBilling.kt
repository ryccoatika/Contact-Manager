package com.ryccoatika.contactmanager.data.billing

import android.app.Activity
import com.ryccoatika.contactmanager.domain.model.BillingEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class FakeBilling(
    initialPrices: Map<String, String> = emptyMap(),
) : Billing {
    private val _prices = MutableStateFlow(initialPrices)
    override val prices: StateFlow<Map<String, String>> = _prices

    private val _events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 4)
    override val events: SharedFlow<BillingEvent> = _events

    var startCalls = 0
        private set
    val purchasedProductIds = mutableListOf<String>()

    override fun start() {
        startCalls++
    }

    override fun purchase(activity: Activity, productId: String) {
        purchasedProductIds += productId
    }

    fun setPrices(prices: Map<String, String>) {
        _prices.value = prices
    }

    fun emit(event: BillingEvent) {
        _events.tryEmit(event)
    }
}
