package com.ryccoatika.contactmanager.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.ui.common.AccountVisuals

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    onAccountClick: (String) -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingMove by viewModel.pendingMove.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var moveSource by remember { mutableStateOf<ContactAccount?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accounts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            // Pull down to re-probe SIM capabilities (stale after a SIM swap)
            // and re-fetch the account list.
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.accounts, key = { it.key }) { account ->
                        AccountRow(
                            account = account,
                            onClick = { onAccountClick(account.key) },
                            onMoveAll = { moveSource = account },
                        )
                    }
                }
            }
        }
    }

    moveSource?.let { source ->
        ModalBottomSheet(onDismissRequest = { moveSource = null }) {
            Text(
                "Move all ${AccountVisuals.label(source.type, source.name)} contacts to",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            state.accounts
                .filter { it.key != source.key && isMoveTarget(it) }
                .forEach { target ->
                    ListItem(
                        modifier = Modifier.clickable {
                            moveSource = null
                            viewModel.requestMoveAll(source, target)
                        },
                        headlineContent = { Text(AccountVisuals.label(target.type, target.name)) },
                        supportingContent = { Text(target.name ?: "On this device") },
                        leadingContent = {
                            Box(
                                Modifier.size(12.dp).clip(CircleShape)
                                    .background(AccountVisuals.color(target.type, target.name)),
                            )
                        },
                    )
                }
            Spacer(Modifier.height(24.dp))
        }
    }

    pendingMove?.let { pending ->
        val lostFields = pending.losses.flatMap { it.lostFields }.distinct()
        AlertDialog(
            onDismissRequest = viewModel::dismissPendingMove,
            title = {
                Text(
                    if (lostFields.isEmpty()) {
                        "Move ${pending.source.contactCount} contacts?"
                    } else {
                        "Some fields will be lost"
                    },
                )
            },
            text = {
                Text(
                    buildString {
                        append("All contacts in ${AccountVisuals.label(pending.source.type, pending.source.name)} ")
                        append("will be moved to ${AccountVisuals.label(pending.target.type, pending.target.name)}.")
                        if (lostFields.isNotEmpty()) {
                            append(
                                "\n\n${pending.losses.size} of them will lose: " +
                                    lostFields.joinToString() + ".",
                            )
                        }
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmPendingMove) {
                    Text(if (lostFields.isEmpty()) "Move" else "Move anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPendingMove) { Text("Cancel") }
            },
        )
    }
}

/** Full-CRUD accounts plus SIMs that passed the write probe. */
private fun isMoveTarget(account: ContactAccount): Boolean =
    account.capability == AccountCapability.FULL_CRUD ||
        (account.capability == AccountCapability.SIM && account.writable)

@Composable
private fun AccountRow(
    account: ContactAccount,
    onClick: () -> Unit,
    onMoveAll: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val readOnly = account.capability == AccountCapability.READ_ONLY

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(AccountVisuals.label(account.type, account.name)) },
        supportingContent = { Text(account.name ?: "On this device") },
        leadingContent = {
            Box(
                Modifier.size(12.dp).clip(CircleShape)
                    .background(AccountVisuals.color(account.type, account.name)),
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${account.contactCount}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                AssistChip(
                    onClick = onClick,
                    label = {
                        Text(
                            when (account.capability) {
                                AccountCapability.FULL_CRUD -> "Full access"
                                AccountCapability.READ_ONLY -> "Read-only"
                                AccountCapability.SIM -> "SIM"
                            },
                        )
                    },
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Account actions")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            enabled = !readOnly,
                            text = {
                                Column {
                                    Text("Move all contacts to…")
                                    if (readOnly) {
                                        Text(
                                            "Managed by app",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                            onClick = {
                                menuOpen = false
                                onMoveAll()
                            },
                        )
                    }
                }
            }
        },
    )
}
