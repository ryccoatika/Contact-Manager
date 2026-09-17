package com.ryccoatika.contactmanager.domain.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsEventTest {
    @Test fun `contact move carries count and capability, no PII`() {
        val e = AnalyticsEvent.ContactMove(count = 3, targetCapability = "SIM")
        assertEquals("contact_move", e.name)
        assertEquals(mapOf("count" to 3, "target_capability" to "SIM"), e.params)
    }

    @Test fun `account visibility name flips on hidden flag`() {
        assertEquals("account_hide", AnalyticsEvent.AccountVisibility(hidden = true).name)
        assertEquals("account_show", AnalyticsEvent.AccountVisibility(hidden = false).name)
    }

    @Test fun `onboarding name flips on skipped flag`() {
        assertEquals("onboarding_skip", AnalyticsEvent.Onboarding(skipped = true).name)
        assertEquals("onboarding_complete", AnalyticsEvent.Onboarding(skipped = false).name)
    }

    @Test fun `search logs only length`() {
        assertEquals(mapOf("query_length" to 5), AnalyticsEvent.Search(queryLength = 5).params)
    }

    @Test fun `paramless events have empty params`() {
        assertEquals(emptyMap<String, Any>(), AnalyticsEvent.ContactUpdate.params)
    }
}
