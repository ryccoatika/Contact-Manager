package com.ryccoatika.contactmanager.domain.analytics

/** Used when Firebase is not configured, and as the CompositionLocal default. */
object NoOpAnalytics : Analytics {
    override fun logEvent(event: AnalyticsEvent) = Unit

    override fun logScreenView(screenName: String) = Unit
}
