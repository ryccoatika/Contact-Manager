package com.ryccoatika.contactmanager.data.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/** Logs to Firebase. Constructed only when Firebase is initialised (see AnalyticsModule). */
class FirebaseAnalyticsImpl(private val firebase: FirebaseAnalytics) : Analytics {

    override fun logEvent(event: AnalyticsEvent) {
        firebase.logEvent(event.name, event.params.toBundle())
    }

    override fun logScreenView(screenName: String) {
        firebase.logEvent(
            FirebaseAnalytics.Event.SCREEN_VIEW,
            Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, "ContactManager")
            },
        )
    }
}

private fun Map<String, Any>.toBundle(): Bundle = Bundle().apply {
    forEach { (key, value) ->
        when (value) {
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Double -> putDouble(key, value)
            is Boolean -> putBoolean(key, value)
            else -> putString(key, value.toString())
        }
    }
}
