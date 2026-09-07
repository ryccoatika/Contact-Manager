package com.ryccoatika.contactmanager.ui.support

import com.ryccoatika.contactmanager.data.billing.BillingEvent
import com.ryccoatika.contactmanager.data.billing.FakeBilling
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SupportViewModelTest {
    @Test
    fun `starts billing on creation`() {
        val billing = FakeBilling()
        SupportViewModel(billing)
        assertEquals(1, billing.startCalls)
    }

    @Test
    fun `exposes localized prices from billing`() = runTest {
        val billing = FakeBilling(initialPrices = mapOf("support_coffee" to "$1.00"))
        val vm = SupportViewModel(billing)
        assertEquals("$1.00", vm.prices.value["support_coffee"])

        billing.setPrices(mapOf("support_coffee" to "Rp15.000"))
        assertEquals("Rp15.000", vm.prices.value["support_coffee"])
    }

    @Test
    fun `passes billing events through`() = runTest {
        val billing = FakeBilling()
        val vm = SupportViewModel(billing)
        val received = mutableListOf<BillingEvent>()
        val job = launch { vm.events.collect { received += it } }
        runCurrent()

        billing.emit(BillingEvent.PurchaseSuccess)
        billing.emit(BillingEvent.Error)
        runCurrent()

        assertEquals(listOf(BillingEvent.PurchaseSuccess, BillingEvent.Error), received)
        job.cancel()
    }
}
