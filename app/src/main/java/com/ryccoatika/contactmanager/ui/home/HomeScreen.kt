package com.ryccoatika.contactmanager.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ryccoatika.contactmanager.domain.MovePlan
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.ui.common.AccountVisuals

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onContactClick: (Long) -> Unit,
    onAddClick: () -> Unit,
    onAccountsClick: () -> Unit,
    pendingFilterAccountKey: String? = null,
    onPendingFilterConsumed: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val batchProgress by viewModel.batchProgress.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showMovePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pendingMove by remember { mutableStateOf<Pair<ContactAccount, MovePlan>?>(null) }

    LaunchedEffect(pendingFilterAccountKey) {
        if (pendingFilterAccountKey != null) {
            viewModel.selectAccount(pendingFilterAccountKey)
            onPendingFilterConsumed()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.batchProgress.collect { progress ->
            if (progress?.finished == true) {
                viewModel.onBatchFinishedShown()
                snackbarHostState.showSnackbar(progress.error ?: "${progress.label} — done")
            }
        }
    }

    Scaffold(
        topBar = {
            if (state.selectionMode) {
                TopAppBar(
                    title = { Text("${state.selectedContactIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("Contacts") },
                    actions = {
                        IconButton(onClick = onAccountsClick) {
                            Icon(Icons.Default.Group, contentDescription = "Accounts")
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (state.selectionMode) {
                BottomAppBar {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showMovePicker = true }) { Text("Move to…") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showDeleteConfirm = true }) { Text("Delete") }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!state.selectionMode) {
                FloatingActionButton(onClick = onAddClick) {
                    Icon(Icons.Default.Add, contentDescription = "New contact")
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            batchProgress?.takeIf { !it.finished }?.let { progress ->
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(progress.label, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = {
                                    if (progress.total == 0) 0f
                                    else progress.done.toFloat() / progress.total
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        TextButton(onClick = viewModel::cancelBatch) { Text("Cancel") }
                    }
                }
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search ${state.contacts.size} contacts") },
                singleLine = true,
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.selectedAccountKey == null,
                        onClick = { viewModel.selectAccount(null) },
                        label = { Text("All") },
                    )
                }
                items(state.accounts, key = { it.key }) { account ->
                    FilterChip(
                        selected = state.selectedAccountKey == account.key,
                        onClick = { viewModel.selectAccount(account.key) },
                        label = { Text("${AccountVisuals.label(account.type, account.name)} · ${account.contactCount}") },
                        leadingIcon = {
                            Box(
                                Modifier.size(10.dp).clip(CircleShape)
                                    .background(AccountVisuals.color(account.type, account.name)),
                            )
                        },
                    )
                }
            }
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.contacts, key = { it.contactId }) { contact ->
                        ContactRow(
                            contact = contact,
                            selected = contact.contactId in state.selectedContactIds,
                            onClick = {
                                if (state.selectionMode) viewModel.toggleSelect(contact.contactId)
                                else onContactClick(contact.contactId)
                            },
                            onLongClick = { viewModel.toggleSelect(contact.contactId) },
                        )
                    }
                }
            }
        }
    }

    if (showMovePicker) {
        ModalBottomSheet(onDismissRequest = { showMovePicker = false }) {
            Text(
                "Move ${state.selectedContactIds.size} selected to",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            state.accounts
                .filter {
                    // Full-CRUD accounts plus SIMs that passed the write probe.
                    it.capability == AccountCapability.FULL_CRUD ||
                        (it.capability == AccountCapability.SIM && it.writable)
                }
                .forEach { account ->
                    ListItem(
                        modifier = Modifier.clickable {
                            showMovePicker = false
                            val plan = viewModel.planMove(account)
                            if (plan.losses.isEmpty()) {
                                viewModel.moveSelectedTo(account)
                            } else {
                                pendingMove = account to plan
                            }
                        },
                        headlineContent = { Text(AccountVisuals.label(account.type, account.name)) },
                        supportingContent = { Text(account.name ?: "On this device") },
                        leadingContent = {
                            Box(
                                Modifier.size(12.dp).clip(CircleShape)
                                    .background(AccountVisuals.color(account.type, account.name)),
                            )
                        },
                    )
                }
            Spacer(Modifier.height(24.dp))
        }
    }

    pendingMove?.let { (account, plan) ->
        AlertDialog(
            onDismissRequest = { pendingMove = null },
            title = { Text("Some fields will be lost") },
            text = {
                Text(
                    "Moving to ${AccountVisuals.label(account.type, account.name)} will drop: " +
                        plan.losses.flatMap { it.lostFields }.distinct().joinToString() + ".",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingMove = null
                    viewModel.moveSelectedTo(account)
                }) { Text("Move anyway") }
            },
            dismissButton = {
                TextButton(onClick = { pendingMove = null }) { Text("Cancel") }
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${state.selectedContactIds.size} contacts?") },
            text = {
                Text(
                    "Entries managed by other apps are skipped. This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteSelected()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactRow(
    contact: Contact,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        headlineContent = { Text(contact.displayName) },
        leadingContent = {
            if (selected) SelectedAvatar() else ContactAvatar(contact)
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                contact.rawContacts
                    .distinctBy { it.accountType to it.accountName }
                    .forEach { raw ->
                        Box(
                            Modifier.size(8.dp).clip(CircleShape)
                                .background(AccountVisuals.color(raw.accountType, raw.accountName)),
                        )
                    }
            }
        },
    )
}

@Composable
private fun SelectedAvatar() {
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = "Selected",
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun ContactAvatar(contact: Contact) {
    if (contact.photoThumbnailUri != null) {
        AsyncImage(
            model = contact.photoThumbnailUri,
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            Modifier.size(40.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                contact.displayName.firstOrNull()?.uppercase() ?: "?",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
