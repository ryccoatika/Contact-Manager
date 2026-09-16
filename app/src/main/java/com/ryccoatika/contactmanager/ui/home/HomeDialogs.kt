package com.ryccoatika.contactmanager.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.unit.dp
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.domain.AccountClassifier
import com.ryccoatika.contactmanager.domain.MovePlan
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.AccountOpMode
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.domain.model.RawContact
import com.ryccoatika.contactmanager.ui.analytics.TrackScreenView
import com.ryccoatika.contactmanager.ui.common.AccountDot
import com.ryccoatika.contactmanager.ui.common.AccountPickerSheet
import com.ryccoatika.contactmanager.ui.common.AccountVisuals
import com.ryccoatika.contactmanager.ui.common.ConfirmDialog
import com.ryccoatika.contactmanager.ui.common.isMoveTarget

/** Target picker for moving/copying the current selection. */
@Composable
internal fun SelectionTargetSheet(
    mode: AccountOpMode,
    selectedCount: Int,
    accounts: List<ContactAccount>,
    onPick: (ContactAccount) -> Unit,
    onDismiss: () -> Unit,
) {
    TrackScreenView(if (mode == AccountOpMode.MOVE) "move_account_picker" else "copy_account_picker")
    AccountPickerSheet(
        title = pluralStringResource(
            if (mode == AccountOpMode.MOVE) R.plurals.home_move_selected_to else R.plurals.home_copy_selected_to,
            selectedCount,
            selectedCount,
        ),
        accounts = accounts.filter(::isMoveTarget),
        onPick = onPick,
        onDismiss = onDismiss,
    )
}

/** Warns that moving/copying the selection into [account] drops fields listed in [plan]. */
@Composable
internal fun FieldsLostDialog(
    mode: AccountOpMode,
    account: ContactAccount,
    plan: MovePlan,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val move = mode == AccountOpMode.MOVE
    TrackScreenView(if (move) "move_fields_lost" else "copy_fields_lost")
    ConfirmDialog(
        title = stringResource(R.string.home_fields_lost_title),
        text = stringResource(
            if (move) R.string.home_fields_lost_text else R.string.home_fields_lost_text_copy,
            AccountVisuals.label(context, account.type, account.name),
            plan.losses
                .flatMap { it.lostFields }
                .distinct()
                .joinToString(),
        ),
        confirmLabel = stringResource(if (move) R.string.home_move_anyway else R.string.home_copy_anyway),
        dismissLabel = stringResource(R.string.home_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** Picks which raw contact the selection merges into (read-only raws excluded). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MergeTargetSheet(
    selectedCount: Int,
    memberRaws: List<Pair<Contact, RawContact>>,
    onPick: (Contact, RawContact) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    TrackScreenView("merge_target_picker")
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            pluralStringResource(R.plurals.home_merge_selected_into, selectedCount, selectedCount),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        memberRaws
            // Read-only raw contacts can't receive data, so they can't be targets.
            .filter { (_, raw) ->
                AccountClassifier.classify(raw.accountType) != AccountCapability.READ_ONLY
            }.forEach { (contact, raw) ->
                ListItem(
                    modifier = Modifier.clickable { onPick(contact, raw) },
                    headlineContent = { Text(contact.displayName) },
                    supportingContent = {
                        Text(AccountVisuals.label(context, raw.accountType, raw.accountName))
                    },
                    leadingContent = { AccountDot(raw.accountType, raw.accountName, size = 12.dp) },
                )
            }
        Spacer(Modifier.height(24.dp))
    }
}

/** Merge summary + read-only members that will be linked instead of copied. */
@Composable
internal fun MergeConfirmDialog(
    targetContact: Contact,
    targetRaw: RawContact,
    sources: List<Pair<Contact, RawContact>>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val readOnly = sources.filter { (_, raw) ->
        AccountClassifier.classify(raw.accountType) == AccountCapability.READ_ONLY
    }
    val mergeSummary = pluralStringResource(
        R.plurals.home_merge_summary,
        sources.size,
        sources.size,
        targetContact.displayName,
        AccountVisuals.label(context, targetRaw.accountType, targetRaw.accountName),
    )
    val memberTemplate = stringResource(R.string.home_merge_member)
    val linkedSuffix = stringResource(R.string.home_merge_linked_suffix)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                pluralStringResource(
                    R.plurals.home_merge_entries_title,
                    sources.size + 1,
                    sources.size + 1,
                ),
            )
        },
        text = {
            Text(
                buildString {
                    append(mergeSummary)
                    if (readOnly.isNotEmpty()) {
                        append("\n\n")
                        append(
                            readOnly.joinToString { (contact, raw) ->
                                memberTemplate.format(
                                    contact.displayName,
                                    AccountVisuals.label(context, raw.accountType, raw.accountName),
                                )
                            },
                        )
                        append(linkedSuffix)
                    }
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.home_merge)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_cancel)) }
        },
    )
}

/**
 * Per-account chooser shown when a delete selection spans several accounts.
 * Deletable accounts start checked; read-only ones are listed greyed-out so
 * the user sees why those entries survive.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeleteAccountsSheet(
    entries: List<SelectionAccountEntry>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    TrackScreenView("delete_account_picker")
    val deletableKeys = remember(entries) {
        entries.filter { it.deletable }.map { it.key }.toSet()
    }
    var checkedKeys by remember(entries) { mutableStateOf(deletableKeys) }
    val disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            stringResource(R.string.home_delete_from_accounts_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (deletableKeys.size > 1) {
            val allChecked = checkedKeys == deletableKeys
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = allChecked,
                        role = Role.Checkbox,
                        onValueChange = { checkedKeys = if (it) deletableKeys else emptySet() },
                    ).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = allChecked, onCheckedChange = null)
                Text(
                    stringResource(R.string.home_delete_select_all),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
        entries.forEach { entry ->
            val checked = entry.key in checkedKeys
            ListItem(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = checked,
                        enabled = entry.deletable,
                        role = Role.Checkbox,
                        onValueChange = {
                            checkedKeys = if (it) checkedKeys + entry.key else checkedKeys - entry.key
                        },
                    ),
                headlineContent = {
                    Text(
                        AccountVisuals.label(context, entry.type, entry.name),
                        color = if (entry.deletable) MaterialTheme.colorScheme.onSurface else disabledColor,
                    )
                },
                supportingContent = {
                    Column {
                        Text(
                            pluralStringResource(
                                R.plurals.home_delete_account_entry_count,
                                entry.entryCount,
                                entry.entryCount,
                            ),
                            color = if (entry.deletable) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                disabledColor
                            },
                        )
                        if (!entry.deletable) {
                            Text(
                                stringResource(R.string.home_delete_read_only_note),
                                color = disabledColor,
                            )
                        }
                    }
                },
                leadingContent = { AccountDot(entry.type, entry.name, size = 12.dp) },
                trailingContent = {
                    Checkbox(checked = checked, onCheckedChange = null, enabled = entry.deletable)
                },
            )
        }
        Text(
            stringResource(R.string.home_delete_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        val checkedEntryCount = entries
            .filter { it.key in checkedKeys }
            .sumOf { it.entryCount }
        Button(
            onClick = { onConfirm(checkedKeys) },
            enabled = checkedKeys.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                pluralStringResource(
                    R.plurals.home_delete_entries,
                    checkedEntryCount,
                    checkedEntryCount,
                ),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun DeleteSelectedDialog(
    selectedCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    TrackScreenView("delete_selected_confirm")
    ConfirmDialog(
        title = pluralStringResource(R.plurals.home_delete_contacts_title, selectedCount, selectedCount),
        text = stringResource(R.string.home_delete_disclaimer),
        confirmLabel = stringResource(R.string.home_delete),
        dismissLabel = stringResource(R.string.home_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
internal fun DeleteContactDialog(
    contact: Contact,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    TrackScreenView("delete_contact_confirm")
    ConfirmDialog(
        title = stringResource(R.string.home_delete_contact_title, contact.displayName),
        text = stringResource(R.string.home_delete_disclaimer),
        confirmLabel = stringResource(R.string.home_delete),
        dismissLabel = stringResource(R.string.home_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
