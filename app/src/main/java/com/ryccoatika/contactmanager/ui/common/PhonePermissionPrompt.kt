package com.ryccoatika.contactmanager.ui.common

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * One-time, subtle READ_PHONE_STATE request shown next to SIM pseudo-accounts.
 * The permission only adds carrier labels for dual SIM; denial keeps the
 * existing single-unlabeled-SIM fallback, so the result is never nagged about.
 */
@Composable
fun PhonePermissionPrompt(
    onAsked: () -> Unit,
    onGranted: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val alreadyGranted = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
    }
    if (alreadyGranted) return

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        onAsked()
        if (granted) onGranted()
    }
    AssistChip(
        onClick = { launcher.launch(Manifest.permission.READ_PHONE_STATE) },
        label = { Text("Grant phone permission to label dual SIMs") },
        leadingIcon = {
            Icon(Icons.Default.SimCard, contentDescription = null, Modifier.size(18.dp))
        },
        modifier = modifier,
    )
}
