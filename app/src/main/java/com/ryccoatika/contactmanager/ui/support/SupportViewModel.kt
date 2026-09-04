package com.ryccoatika.contactmanager.ui.support

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.ryccoatika.contactmanager.data.billing.BillingEvent
import com.ryccoatika.contactmanager.data.billing.BillingManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SupportViewModel
    @Inject
    constructor(
        private val billing: BillingManager,
    ) : ViewModel() {
        /** productId → localized price; empty until Play returns the details. */
        val prices: StateFlow<Map<String, String>> = billing.prices
        val events: SharedFlow<BillingEvent> = billing.events

        init {
            billing.start()
        }

        fun purchase(activity: Activity, productId: String) {
            billing.purchase(activity, productId)
        }
    }
