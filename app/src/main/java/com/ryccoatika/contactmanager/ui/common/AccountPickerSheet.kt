package com.ryccoatika.contactmanager.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.ContactAccount

/** Full-CRUD accounts plus SIMs that passed the write probe. */
fun isMoveTarget(account: ContactAccount): Boolean =
    account.capability == AccountCapability.FULL_CRUD ||
        (account.capability == AccountCapability.SIM && account.writable)

/** Bottom sheet listing [accounts] to pick one; the caller pre-filters the list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountPickerSheet(
    title: String,
    accounts: List<ContactAccount>,
    onPick: (ContactAccount) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        accounts.forEach { account ->
            ListItem(
                modifier = Modifier.clickable { onPick(account) },
                headlineContent = { Text(AccountVisuals.label(context, account.type, account.name)) },
                supportingContent = {
                    Text(account.name ?: stringResource(R.string.accounts_on_this_device))
                },
                leadingContent = { AccountDot(account.type, account.name, size = 12.dp) },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
