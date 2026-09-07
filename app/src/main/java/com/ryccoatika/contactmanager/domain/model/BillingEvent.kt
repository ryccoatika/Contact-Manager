package com.ryccoatika.contactmanager.domain.model

/** One-shot outcomes of a donation purchase flow. */
sealed interface BillingEvent {
    /** A donation completed (already consumed, so it can be bought again). */
    data object PurchaseSuccess : BillingEvent

    data object PurchaseCancelled : BillingEvent

    data object Error : BillingEvent
}
