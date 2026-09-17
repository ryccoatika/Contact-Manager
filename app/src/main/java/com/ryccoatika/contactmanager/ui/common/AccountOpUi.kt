package com.ryccoatika.contactmanager.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.domain.model.AccountOpMode
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.domain.model.PendingAccountOp
import com.ryccoatika.contactmanager.ui.analytics.TrackScreenView

/**
 * Target picker for "move/copy all contacts of [source] to…" — shared by the
 * Accounts screen and Home's account-chip long-press menu.
 */
@Composable
fun AccountOpTargetSheet(
    mode: AccountOpMode,
    source: ContactAccount,
    accounts: List<ContactAccount>,
    onPick: (target: ContactAccount) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    TrackScreenView(
        when (mode) {
            AccountOpMode.MOVE -> "account_move_target_picker"
            AccountOpMode.COPY -> "account_copy_target_picker"
        },
    )
    AccountPickerSheet(
        title = stringResource(
            when (mode) {
                AccountOpMode.MOVE -> R.string.accounts_move_all_source_contacts_to
                AccountOpMode.COPY -> R.string.accounts_copy_all_source_contacts_to
            },
            AccountVisuals.label(context, source.type, source.name),
        ),
        accounts = accounts.filter { it.key != source.key && isMoveTarget(it) },
        onPick = onPick,
        onDismiss = onDismiss,
    )
}

/** Confirmation (with the SIM field-loss report) before a bulk account batch runs. */
@Composable
fun AccountOpConfirmDialog(
    pending: PendingAccountOp,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val move = pending.mode == AccountOpMode.MOVE
    val lostFields = pending.losses.flatMap { it.lostFields }.distinct()
    TrackScreenView(if (move) "account_move_confirm" else "account_copy_confirm")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (lostFields.isEmpty()) {
                    pluralStringResource(
                        if (move) R.plurals.accounts_move_contacts_title else R.plurals.accounts_copy_contacts_title,
                        pending.source.contactCount,
                        pending.source.contactCount,
                    )
                } else {
                    stringResource(R.string.accounts_some_fields_lost)
                },
            )
        },
        text = {
            val opBody = stringResource(
                if (move) R.string.accounts_move_dialog_body else R.string.accounts_copy_dialog_body,
                AccountVisuals.label(context, pending.source.type, pending.source.name),
                AccountVisuals.label(context, pending.target.type, pending.target.name),
            )
            val lossesText = if (lostFields.isNotEmpty()) {
                pluralStringResource(
                    R.plurals.accounts_move_dialog_losses,
                    pending.losses.size,
                    pending.losses.size,
                    lostFields.joinToString(),
                )
            } else {
                null
            }
            Text(
                buildString {
                    append(opBody)
                    if (lossesText != null) {
                        append("\n\n")
                        append(lossesText)
                    }
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(
                        when {
                            move && lostFields.isEmpty() -> R.string.accounts_move
                            move -> R.string.accounts_move_anyway
                            lostFields.isEmpty() -> R.string.accounts_copy
                            else -> R.string.accounts_copy_anyway
                        },
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.accounts_cancel))
            }
        },
    )
}
