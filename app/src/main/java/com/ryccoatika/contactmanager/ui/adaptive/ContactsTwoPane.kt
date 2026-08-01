package com.ryccoatika.contactmanager.ui.adaptive

import androidx.compose.runtime.Composable
import com.ryccoatika.contactmanager.ui.home.HomeScreen

// Task 5 replaces this single-pane stub with the real list + detail two-pane.
@Composable
fun ContactsTwoPane() {
    HomeScreen(
        onContactClick = {}, onAddClick = {}, onAccountsClick = {}, onDuplicatesClick = {},
        embedded = true,
    )
}
