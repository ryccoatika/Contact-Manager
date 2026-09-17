package com.ryccoatika.contactmanager.ui.accounts

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.ui.analytics.TrackScreenView
import com.ryccoatika.contactmanager.ui.common.AccountDot
import com.ryccoatika.contactmanager.ui.common.AccountVisuals
import com.ryccoatika.contactmanager.ui.common.isMoveTarget
import com.ryccoatika.contactmanager.ui.theme.ContactManagerTheme

/** Which flow [TransferAccountsSheet] is picking accounts for. */
internal enum class TransferMode { EXPORT, IMPORT }

/**
 * Account picker shown from the top-bar transfer menu — export and import share the
 * same checkbox-list idiom as [com.ryccoatika.contactmanager.ui.home.DeleteAccountsSheet],
 * but every account starts checked and the confirm button isn't destructive.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransferAccountsSheet(
    mode: TransferMode,
    accounts: List<ContactAccount>,
    onConfirm: (List<ContactAccount>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    TrackScreenView(if (mode == TransferMode.EXPORT) "export_account_picker" else "import_account_picker")
    // Export never mutates, so every account is offered; import only ever writes into a valid
    // target (FULL_CRUD, or a SIM that passed the write probe) — read-only app-managed accounts
    // (WhatsApp etc.) and non-writable SIMs are filtered out before display/precheck.
    val visibleAccounts = remember(accounts, mode) {
        if (mode == TransferMode.IMPORT) accounts.filter(::isMoveTarget) else accounts
    }
    val allKeys = remember(visibleAccounts) { visibleAccounts.map { it.key }.toSet() }
    var checkedKeys by remember(visibleAccounts) { mutableStateOf(allKeys) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            stringResource(
                if (mode == TransferMode.EXPORT) {
                    R.string.accounts_export_pick_title
                } else {
                    R.string.accounts_import_pick_title
                },
            ),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (allKeys.size > 1) {
            val allChecked = checkedKeys == allKeys
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = allChecked,
                        role = Role.Checkbox,
                        onValueChange = { checkedKeys = if (it) allKeys else emptySet() },
                    ).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = allChecked, onCheckedChange = null)
                Text(
                    stringResource(R.string.accounts_select_all),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            visibleAccounts.forEach { account ->
                val checked = account.key in checkedKeys
                ListItem(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .toggleable(
                            value = checked,
                            role = Role.Checkbox,
                            onValueChange = {
                                checkedKeys = if (it) checkedKeys + account.key else checkedKeys - account.key
                            },
                        ),
                    headlineContent = {
                        Text(account.displayLabel ?: AccountVisuals.label(context, account.type, account.name))
                    },
                    supportingContent = {
                        Text(account.name ?: stringResource(R.string.accounts_on_this_device))
                    },
                    leadingContent = { AccountDot(account.type, account.name, size = 12.dp) },
                    trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                )
            }
        }
        Button(
            onClick = { onConfirm(visibleAccounts.filter { it.key in checkedKeys }) },
            enabled = checkedKeys.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                stringResource(
                    if (mode == TransferMode.EXPORT) R.string.accounts_export else R.string.accounts_import,
                ),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** One combined vCard file vs one file per account — only offered when more than one account is picked. */
@Composable
internal fun ExportModeDialog(
    onOneFile: () -> Unit,
    onPerAccount: () -> Unit,
    onDismiss: () -> Unit,
) {
    TrackScreenView("export_mode_dialog")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.accounts_export_mode_title)) },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(selected = false, onClick = onOneFile, role = Role.Button),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.accounts_export_mode_one_file), style = MaterialTheme.typography.bodyLarge)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(selected = false, onClick = onPerAccount, role = Role.Button),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.accounts_export_mode_per_account), style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.accounts_cancel)) }
        },
    )
}

/** Summarizes a parsed import before it starts — SIM targets lose everything but name + first number. */
@Composable
internal fun ImportConfirmDialog(
    pending: PendingImport,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    TrackScreenView("import_confirm")
    val count = pending.contacts.size
    val targetLabel = pending.targets.joinToString {
        it.displayLabel ?: AccountVisuals.label(context, it.type, it.name)
    }
    val simTarget = pending.targets.any { it.capability == AccountCapability.SIM }
    val bodyText = pluralStringResource(R.plurals.accounts_import_confirm_body, count, count, targetLabel)
    val skippedText = if (pending.skippedCards > 0) {
        pluralStringResource(R.plurals.accounts_import_skipped, pending.skippedCards, pending.skippedCards)
    } else {
        null
    }
    val simWarningText = if (simTarget) stringResource(R.string.accounts_import_sim_warning) else null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(R.plurals.accounts_import_confirm_title, count, count)) },
        text = {
            Text(
                buildString {
                    append(bodyText)
                    if (skippedText != null) {
                        append("\n\n")
                        append(skippedText)
                    }
                    if (simWarningText != null) {
                        append("\n\n")
                        append(simWarningText)
                    }
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.accounts_import)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.accounts_cancel)) }
        },
    )
}

@Preview(name = "Transfer sheet · light")
@Preview(name = "Transfer sheet · dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TransferAccountsSheetPreview() {
    ContactManagerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column {
                ListItem(
                    headlineContent = { Text("rycco@gmail.com") },
                    supportingContent = { Text("Google") },
                    leadingContent = { AccountDot("com.google", "rycco@gmail.com", size = 12.dp) },
                    trailingContent = { Checkbox(checked = true, onCheckedChange = null) },
                )
                ListItem(
                    headlineContent = { Text("WhatsApp") },
                    supportingContent = { Text("On this device") },
                    leadingContent = { AccountDot("com.whatsapp", null, size = 12.dp) },
                    trailingContent = { Checkbox(checked = true, onCheckedChange = null) },
                )
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(stringResource(R.string.accounts_export))
                }
            }
        }
    }
}
