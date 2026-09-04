package com.ryccoatika.contactmanager.data.analytics

/** Records analytics calls for assertions. */
class FakeAnalytics : Analytics {
    val events = mutableListOf<AnalyticsEvent>()
    val screenViews = mutableListOf<String>()

    override fun logEvent(event: AnalyticsEvent) {
        events += event
    }

    override fun logScreenView(screenName: String) {
        screenViews += screenName
    }
}
