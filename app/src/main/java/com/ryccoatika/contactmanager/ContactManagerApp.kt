package com.ryccoatika.contactmanager

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Process
import com.ryccoatika.contactmanager.ui.crash.CrashActivity
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
        installCrashScreen()
    }

    /**
     * On any uncaught exception, bring up [CrashActivity] (in a fresh task, so
     * the system restarts it in a new process) and then delegate to whatever
     * handler was installed before us — Crashlytics' when Firebase is present —
     * which persists the report and kills the crashed process. Installed after
     * Crashlytics init so its handler is the one we chain to.
     */
    private fun installCrashScreen() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                startActivity(
                    Intent(this, CrashActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
                    ),
                )
            }
            previousHandler?.uncaughtException(thread, throwable)
                ?: Process.killProcess(Process.myPid())
        }
    }
}
