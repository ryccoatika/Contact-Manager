package com.ryccoatika.contactmanager.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.ui.Routes
import com.ryccoatika.contactmanager.ui.about.AboutScreen
import com.ryccoatika.contactmanager.ui.about.ContactDeveloperScreen
import com.ryccoatika.contactmanager.ui.accounts.AccountsScreen
import com.ryccoatika.contactmanager.ui.duplicates.DuplicatesScreen
import com.ryccoatika.contactmanager.ui.settings.SettingsScreen

private enum class TabletDestination { CONTACTS, DUPLICATES, SETTINGS }

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
                selected = dest == TabletDestination.SETTINGS,
                onClick = { dest = TabletDestination.SETTINGS },
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                label = { Text(stringResource(R.string.settings_title)) },
            )
        },
    ) {
        when (dest) {
            TabletDestination.CONTACTS -> ContactsTwoPane()
            TabletDestination.DUPLICATES -> CenteredPane { DuplicatesScreen(onBack = {}, embedded = true) }
            TabletDestination.SETTINGS -> CenteredPane { SettingsPane() }
        }
    }
}

/**
 * Settings as a self-contained pane: the list is the rail root (no chrome), and
 * Accounts / About / Contact keep their own top bars so back navigation works
 * inside the pane.
 */
@Composable
private fun SettingsPane() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = Routes.SETTINGS) {
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = {},
                onAccountsClick = { nav.navigate(Routes.ACCOUNTS) },
                onAboutClick = { nav.navigate(Routes.ABOUT) },
                embedded = true,
            )
        }
        composable(Routes.ACCOUNTS) {
            AccountsScreen(onBack = { nav.popBackStack() }, onAccountClick = {})
        }
        composable(Routes.ABOUT) {
            AboutScreen(
                onBack = { nav.popBackStack() },
                onContactClick = { nav.navigate(Routes.CONTACT_DEVELOPER) },
            )
        }
        composable(Routes.CONTACT_DEVELOPER) {
            ContactDeveloperScreen(onBack = { nav.popBackStack() })
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
