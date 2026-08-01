package com.ryccoatika.contactmanager.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.ui.accounts.AccountsScreen
import com.ryccoatika.contactmanager.ui.duplicates.DuplicatesScreen

private enum class TabletDestination { CONTACTS, DUPLICATES, ACCOUNTS }

/** Tablet/expanded layout: a navigation rail + the active destination's content. */
@Composable
fun TabletShell() {
    var dest by rememberSaveable { mutableStateOf(TabletDestination.CONTACTS) }
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            item(
                selected = dest == TabletDestination.CONTACTS,
                onClick = { dest = TabletDestination.CONTACTS },
                icon = { Icon(Icons.Default.People, contentDescription = null) },
                label = { Text(stringResource(R.string.home_title)) },
            )
            item(
                selected = dest == TabletDestination.DUPLICATES,
                onClick = { dest = TabletDestination.DUPLICATES },
                icon = { Icon(Icons.Default.Difference, contentDescription = null) },
                label = { Text(stringResource(R.string.duplicates_title)) },
            )
            item(
                selected = dest == TabletDestination.ACCOUNTS,
                onClick = { dest = TabletDestination.ACCOUNTS },
                icon = { Icon(Icons.Default.ManageAccounts, contentDescription = null) },
                label = { Text(stringResource(R.string.accounts_title)) },
            )
        },
    ) {
        when (dest) {
            TabletDestination.CONTACTS -> ContactsTwoPane()
            TabletDestination.DUPLICATES -> CenteredPane { DuplicatesScreen(onBack = {}, embedded = true) }
            TabletDestination.ACCOUNTS -> CenteredPane { AccountsScreen(onBack = {}, onAccountClick = {}, embedded = true) }
        }
    }
}

/** Width-caps a single pane so it isn't stretched edge-to-edge on wide screens. */
@Composable
private fun CenteredPane(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier.widthIn(max = 720.dp).fillMaxSize().padding(horizontal = 8.dp),
        ) { content() }
    }
}
