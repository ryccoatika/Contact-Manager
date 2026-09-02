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

/**
 * Play In-App Updates, flexible flow. On launch it asks Play whether a newer
 * version is available; if so it starts a background download that never blocks
 * the app. When the download finishes, [snackbarHostState] shows a "Restart"
 * prompt that installs it via `completeUpdate()`.
 *
 * Only works for Play-installed builds — for debug/sideloaded builds Play reports
 * no update and the whole thing is a no-op. All failures are swallowed by design;
 * a background update is never worth interrupting the user for.
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

    // Kick off the check once per launch.
    LaunchedEffect(Unit) {
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                manager.startUpdateFlowForResult(
                    info,
                    updateLauncher,
                    AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                )
            }
        }
    }

    // On resume, re-offer the restart if a download completed while we were away
    // (the listener above may not be registered at that moment).
    LifecycleResumeEffect(Unit) {
        manager.appUpdateInfo.addOnSuccessListener { info ->
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
