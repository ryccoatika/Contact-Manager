package com.ryccoatika.contactmanager.data.analytics

/** App-facing analytics sink. Real impl logs to Firebase; NoOp/Fake for absent-Firebase/tests. */
interface Analytics {
    fun logEvent(event: AnalyticsEvent)

    fun logScreenView(screenName: String)
}
