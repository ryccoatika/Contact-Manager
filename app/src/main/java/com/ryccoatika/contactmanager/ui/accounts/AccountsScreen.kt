package com.ryccoatika.contactmanager.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.ryccoatika.contactmanager.data.sim.SimRouting
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.ryccoatika.contactmanager.ui.common.AccountDot
import com.ryccoatika.contactmanager.ui.common.AccountVisuals
import com.ryccoatika.contactmanager.ui.common.CapabilityTag
import com.ryccoatika.contactmanager.ui.common.PhonePermissionPrompt
import com.ryccoatika.contactmanager.ui.common.SectionCard
import com.ryccoatika.contactmanager.ui.theme.ContactManagerTheme
import com.ryccoatika.contactmanager.ui.theme.TabularNums

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
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val simPseudoPresent = state.accounts.any { SimRouting.isSimAccount(it.type) }
                    if (simPseudoPresent && !state.phonePermissionAsked) {
                        item(key = "phone-permission-prompt") {
                            PhonePermissionPrompt(
                                onAsked = viewModel::markPhonePermissionAsked,
                                // Granting unlocks carrier labels; re-probe right away.
                                onGranted = viewModel::refresh,
                            )
                        }
                    }
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
                        leadingContent = { AccountDot(target.type, target.name, size = 12.dp) },
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
    val label = AccountVisuals.label(account.type, account.name)

    SectionCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(AccountVisuals.color(account.type, account.name)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                Text(
                    account.name ?: "On this device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${account.contactCount}",
                    style = TabularNums.merge(MaterialTheme.typography.titleMedium),
                )
                Spacer(Modifier.height(4.dp))
                CapabilityTag(account.capability, account.writable)
            }
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
    }
}

@Preview(name = "Account row · light")
@Preview(name = "Account row · dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AccountRowPreview() {
    ContactManagerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AccountRow(
                    ContactAccount("rycco@gmail.com", "com.google", AccountCapability.FULL_CRUD, 201),
                    onClick = {}, onMoveAll = {},
                )
                AccountRow(
                    ContactAccount("WhatsApp", "com.whatsapp", AccountCapability.READ_ONLY, 41, writable = false),
                    onClick = {}, onMoveAll = {},
                )
            }
        }
    }
}
