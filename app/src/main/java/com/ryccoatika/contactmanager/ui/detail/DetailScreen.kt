package com.ryccoatika.contactmanager.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.ryccoatika.contactmanager.domain.AccountClassifier
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.LabeledValue
import com.ryccoatika.contactmanager.domain.model.RawContact
import com.ryccoatika.contactmanager.ui.common.AccountVisuals

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onEditRawContact: (Long) -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<RawContact?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DetailEvent.NavigateBack -> onBack()
                is DetailEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    // Contact deleted elsewhere (another app, sync) while this screen is open:
    // leave instead of showing a blank page.
    LaunchedEffect(state) {
        if (!state.loading && state.contact == null) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val current = state.contact
        if (current == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                DetailHeader(current)
                Spacer(Modifier.height(16.dp))
                current.rawContacts.forEach { raw ->
                    RawContactCard(
                        raw = raw,
                        onEdit = { onEditRawContact(raw.rawContactId) },
                        onDelete = { pendingDelete = raw },
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }

    pendingDelete?.let { raw ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete contact entry?") },
            text = {
                Text(
                    "This removes the entry from " +
                        "${AccountVisuals.label(raw.accountType, raw.accountName)}. " +
                        "This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    viewModel.deleteRawContact(raw.rawContactId)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DetailHeader(contact: Contact) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (contact.photoThumbnailUri != null) {
            AsyncImage(
                model = contact.photoThumbnailUri,
                contentDescription = null,
                modifier = Modifier.size(96.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier.size(96.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    contact.displayName.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(contact.displayName, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun RawContactCard(
    raw: RawContact,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val capability = AccountClassifier.classify(raw.accountType)
    val readOnly = capability == AccountCapability.READ_ONLY
    val accountLabel = AccountVisuals.label(raw.accountType, raw.accountName)

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(10.dp).clip(CircleShape)
                        .background(AccountVisuals.color(raw.accountType, raw.accountName)),
                )
                Spacer(Modifier.width(8.dp))
                Text(accountLabel, style = MaterialTheme.typography.titleSmall)
                if (capability == AccountCapability.SIM) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "SIM",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            raw.phones.forEach { LabeledValueRow(it, fallbackLabel = "Phone") }
            raw.emails.forEach { LabeledValueRow(it, fallbackLabel = "Email") }
            raw.organization?.let { FieldRow(label = "Organization", value = it) }
            raw.note?.let { FieldRow(label = "Note", value = it) }
            Spacer(Modifier.height(8.dp))
            if (readOnly) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Lock, contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Managed by $accountLabel — edit in that app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEdit) { Text("Edit") }
                    OutlinedButton(onClick = onDelete) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
private fun LabeledValueRow(item: LabeledValue, fallbackLabel: String) {
    FieldRow(label = item.typeLabel ?: fallbackLabel, value = item.value)
}

@Composable
private fun FieldRow(label: String, value: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
