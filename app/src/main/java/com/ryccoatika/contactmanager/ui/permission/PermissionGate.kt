package com.ryccoatika.contactmanager.ui.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ryccoatika.contactmanager.R

private val CONTACT_PERMISSIONS = arrayOf(
    Manifest.permission.READ_CONTACTS,
    Manifest.permission.WRITE_CONTACTS,
)

// Requested together with contacts so the user sees one flow up front, but the
// gate never blocks on them: phone permissions are optional and only improve
// SIM labels (slot + carrier + number). Denying them still unlocks the app.
private val REQUESTED_PERMISSIONS = CONTACT_PERMISSIONS + arrayOf(
    Manifest.permission.READ_PHONE_STATE,
    Manifest.permission.READ_PHONE_NUMBERS,
)

private fun allGranted(context: Context): Boolean = CONTACT_PERMISSIONS.all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

private fun showPermissionRationale(context: Context): Boolean = CONTACT_PERMISSIONS.any {
    (context as? Activity)?.shouldShowRequestPermissionRationale(it) == true
}

/** Shows [content] only when contact permissions are granted; otherwise rationale screen. */
@Composable
fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(allGranted(context)) }
    var shouldShowRationale by remember { mutableStateOf(showPermissionRationale(context)) }
    // rationale == false is ambiguous: it means "never asked" OR "denied with
    // 'don't ask again' / denied twice". Only after a request has actually come
    // back denied with the flag still false do we know it's permanent — that's
    // when "Open settings" is the only way forward.
    var requestedOnce by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        requestedOnce = true
        // Only the contacts permissions gate the app; phone results are ignored.
        granted = CONTACT_PERMISSIONS.all { result[it] == true }
        shouldShowRationale = showPermissionRationale(context)
    }

    // Re-check on every resume so a grant made in system settings (after a
    // permanent denial) unlocks the app without a restart.
    LifecycleResumeEffect(Unit) {
        granted = allGranted(context)
        shouldShowRationale = showPermissionRationale(context)
        onPauseOrDispose { }
    }

    if (granted) {
        content()
        return
    }

    // Surface sets the themed background AND content color — without it the
    // title falls back to LocalContentColor's default (black) and vanishes in
    // dark mode.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        PermissionRationale(
            onAllow = { launcher.launch(REQUESTED_PERMISSIONS) },
            permanentlyDenied = requestedOnce && !shouldShowRationale,
            onOpenSettings = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )
            },
        )
    }
}

@Composable
private fun PermissionRationale(
    onAllow: () -> Unit,
    permanentlyDenied: Boolean,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.Contacts, contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.permission_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.permission_rationale),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        if (permanentlyDenied) {
            // The system dialog can no longer be shown — settings is the only way.
            Button(onClick = onOpenSettings) {
                Text(stringResource(R.string.permission_open_settings))
            }
        } else {
            Button(onClick = onAllow) {
                Text(stringResource(R.string.permission_allow))
            }
        }
    }
}
