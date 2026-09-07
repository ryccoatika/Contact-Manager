package com.ryccoatika.contactmanager.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.domain.AccountClassifier
import com.ryccoatika.contactmanager.domain.MovePlan
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.domain.model.RawContact
import com.ryccoatika.contactmanager.ui.analytics.TrackScreenView
import com.ryccoatika.contactmanager.ui.common.AccountDot
import com.ryccoatika.contactmanager.ui.common.AccountPickerSheet
import com.ryccoatika.contactmanager.ui.common.AccountVisuals
import com.ryccoatika.contactmanager.ui.common.ConfirmDialog
import com.ryccoatika.contactmanager.ui.common.isMoveTarget

/** Target picker for moving the current selection. */
@Composable
internal fun MoveSelectedSheet(
    selectedCount: Int,
    accounts: List<ContactAccount>,
    onPick: (ContactAccount) -> Unit,
    onDismiss: () -> Unit,
) {
    TrackScreenView("move_account_picker")
    AccountPickerSheet(
        title = pluralStringResource(R.plurals.home_move_selected_to, selectedCount, selectedCount),
        accounts = accounts.filter(::isMoveTarget),
        onPick = onPick,
        onDismiss = onDismiss,
    )
}

/** Warns that moving the selection into [account] drops fields listed in [plan]. */
@Composable
internal fun FieldsLostDialog(
    account: ContactAccount,
    plan: MovePlan,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    TrackScreenView("move_fields_lost")
    ConfirmDialog(
        title = stringResource(R.string.home_fields_lost_title),
        text = stringResource(
            R.string.home_fields_lost_text,
            AccountVisuals.label(context, account.type, account.name),
            plan.losses
                .flatMap { it.lostFields }
                .distinct()
                .joinToString(),
        ),
        confirmLabel = stringResource(R.string.home_move_anyway),
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
