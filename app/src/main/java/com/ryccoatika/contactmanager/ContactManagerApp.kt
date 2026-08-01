package com.ryccoatika.contactmanager

import android.app.Application
import android.content.pm.ApplicationInfo
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ContactManagerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Crashlytics auto-captures uncaught crashes once Firebase is initialised
        // (i.e. google-services.json is present). Keep collection off for debuggable
        // builds so dev crashes don't pollute the dashboard; on for release.
        if (FirebaseApp.getApps(this).isNotEmpty()) {
            val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!debuggable)
        }
    }
}
