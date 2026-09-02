package com.ryccoatika.contactmanager.ui.update

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.ryccoatika.contactmanager.R
import kotlinx.coroutines.launch

// A release's Play priority (0..5, set at upload from gradle.properties) at or
// above this forces the blocking immediate flow; below it stays flexible.
private const val IMMEDIATE_PRIORITY = 4
// Escalate to immediate once a flexible-eligible update has been available this
// many days, so laggards eventually update even for a low-priority release.
private const val IMMEDIATE_STALENESS_DAYS = 14

/**
 * Play In-App Updates. On launch it asks Play whether a newer version is
 * available and picks the flow from the release's priority/staleness:
 *
 * - priority >= [IMMEDIATE_PRIORITY], or available for >= [IMMEDIATE_STALENESS_DAYS]
 *   days → **immediate** (Play's blocking full-screen updater).
 * - otherwise → **flexible**: a background download that never blocks the app;
 *   when it finishes, [snackbarHostState] shows a "Restart" prompt that installs
 *   it via `completeUpdate()`.
 *
 * An interrupted immediate update is resumed on the next resume. Only works for
 * Play-installed builds — debug/sideload reports no update and it's a no-op. All
 * failures are swallowed by design; an update is never worth interrupting for.
 */
@Composable
fun InAppUpdate(snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    // In-app updates only run inside an Activity that hosts the Play flow.
    if (remember(context) { context.findActivity() } == null) return
    val scope = rememberCoroutineScope()
    val manager = remember(context) { AppUpdateManagerFactory.create(context) }

    // Play drives the flow UI in its own activity; we don't act on the result
    // (a declined/failed update just leaves the app as-is).
    val updateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { }

    val promptRestart: () -> Unit = {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.update_downloaded),
                actionLabel = context.getString(R.string.update_restart),
                duration = SnackbarDuration.Indefinite,
            )
            if (result == SnackbarResult.ActionPerformed) manager.completeUpdate()
        }
    }

    // Flexible download reaching DOWNLOADED → offer the restart.
    DisposableEffect(manager) {
        val listener = InstallStateUpdatedListener { state ->
            if (state.installStatus() == InstallStatus.DOWNLOADED) promptRestart()
        }
        manager.registerListener(listener)
        onDispose { manager.unregisterListener(listener) }
    }

    // Kick off the check once per launch, choosing flexible vs immediate.
    LaunchedEffect(Unit) {
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) return@addOnSuccessListener
            val stalenessDays = info.clientVersionStalenessDays() ?: 0
            val type =
                if (info.updatePriority() >= IMMEDIATE_PRIORITY || stalenessDays >= IMMEDIATE_STALENESS_DAYS) {
                    AppUpdateType.IMMEDIATE
                } else {
                    AppUpdateType.FLEXIBLE
                }
            if (info.isUpdateTypeAllowed(type)) {
                manager.startUpdateFlowForResult(
                    info,
                    updateLauncher,
                    AppUpdateOptions.newBuilder(type).build(),
                )
            }
        }
    }

    LifecycleResumeEffect(Unit) {
        manager.appUpdateInfo.addOnSuccessListener { info ->
            // Resume an immediate update that was interrupted (e.g. app killed).
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                manager.startUpdateFlowForResult(
                    info,
                    updateLauncher,
                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                )
            }
            // Re-offer the restart if a flexible download finished while away.
            if (info.installStatus() == InstallStatus.DOWNLOADED) promptRestart()
        }
        onPauseOrDispose { }
    }
}

/** Unwrap the Compose ContextWrapper chain to the hosting Activity. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
