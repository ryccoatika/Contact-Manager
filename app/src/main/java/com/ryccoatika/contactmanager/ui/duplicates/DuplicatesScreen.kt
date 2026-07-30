package com.ryccoatika.contactmanager.ui.duplicates

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.ryccoatika.contactmanager.domain.AccountClassifier
import com.ryccoatika.contactmanager.domain.DuplicateFinder
import com.ryccoatika.contactmanager.domain.DuplicateGroup
import com.ryccoatika.contactmanager.domain.MatchConfidence
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.RawContact
import com.ryccoatika.contactmanager.domain.model.LabeledValue
import com.ryccoatika.contactmanager.ui.common.AccountDot
import com.ryccoatika.contactmanager.ui.common.AccountVisuals
import com.ryccoatika.contactmanager.ui.common.CardsSkeleton
import com.ryccoatika.contactmanager.ui.common.ContactAvatar
import com.ryccoatika.contactmanager.ui.common.SectionCard
import com.ryccoatika.contactmanager.ui.theme.ContactManagerTheme
import com.ryccoatika.contactmanager.ui.theme.extendedColors
import android.content.res.Configuration
import androidx.compose.material3.Button
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(
    onBack: () -> Unit,
    viewModel: DuplicatesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var mergePickerGroup by remember { mutableStateOf<DuplicateGroup?>(null) }
    var pendingMerge by remember { mutableStateOf<Pair<DuplicateGroup, RawContact>?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Duplicates") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
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
            CardsSkeleton(Modifier.padding(padding), count = 3, height = 150.dp)
        } else if (state.groups.isEmpty()) {
            Column(
                Modifier.padding(padding).fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))
                Text("No duplicates found", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.groups, key = { DuplicateFinder.groupKey(it) }) { group ->
                    DuplicateGroupCard(
                        group = group,
                        onLink = { viewModel.link(group) },
                        onMerge = { mergePickerGroup = group },
                        onDismiss = { viewModel.dismiss(group) },
                    )
                }
            }
        }
    }

    mergePickerGroup?.let { group ->
        ModalBottomSheet(
            onDismissRequest = { mergePickerGroup = null },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Text(
                "Merge into",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            group.memberRaws()
                // Read-only raw contacts can't receive data, so they can't be targets.
                .filter { (_, raw) ->
                    AccountClassifier.classify(raw.accountType) != AccountCapability.READ_ONLY
                }
                .forEach { (contact, raw) ->
                    ListItem(
                        modifier = Modifier.clickable {
                            mergePickerGroup = null
                            pendingMerge = group to raw
                        },
                        headlineContent = { Text(contact.displayName) },
                        supportingContent = {
                            Text(AccountVisuals.label(raw.accountType, raw.accountName))
                        },
                        leadingContent = {
                            Box(
                                Modifier.size(12.dp).clip(CircleShape)
                                    .background(AccountVisuals.color(raw.accountType, raw.accountName)),
                            )
                        },
                    )
                }
            Spacer(Modifier.height(24.dp))
        }
    }

    pendingMerge?.let { (group, target) ->
        val targetContact = group.contacts.first { c -> c.rawContacts.any { it.rawContactId == target.rawContactId } }
        val sources = group.memberRaws().filter { (_, raw) -> raw.rawContactId != target.rawContactId }
        val readOnly = sources.filter { (_, raw) ->
            AccountClassifier.classify(raw.accountType) == AccountCapability.READ_ONLY
        }
        AlertDialog(
            onDismissRequest = { pendingMerge = null },
            title = { Text("Merge ${sources.size + 1} entries?") },
            text = {
                Text(
                    buildString {
                        append(
                            "${sources.size} entries will be merged into ${targetContact.displayName} " +
                                "(${AccountVisuals.label(target.accountType, target.accountName)}) and removed.",
                        )
                        if (readOnly.isNotEmpty()) {
                            append("\n\n")
                            append(
                                readOnly.joinToString { (c, raw) ->
                                    "${c.displayName} (${AccountVisuals.label(raw.accountType, raw.accountName)})"
                                },
                            )
                            append(" will be linked (managed by app).")
                        }
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingMerge = null
                    viewModel.merge(group, target)
                }) { Text("Merge") }
            },
            dismissButton = {
                TextButton(onClick = { pendingMerge = null }) { Text("Cancel") }
            },
        )
    }
}

/** Every (owning contact, raw contact) pair of the group's members. */
private fun DuplicateGroup.memberRaws(): List<Pair<Contact, RawContact>> =
    contacts.flatMap { contact -> contact.rawContacts.map { contact to it } }

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    onLink: () -> Unit,
    onMerge: () -> Unit,
    onDismiss: () -> Unit,
) {
    SectionCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ConfidenceChip(group.confidence)
            Spacer(Modifier.weight(1f))
            Text(
                group.matchReason,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.extendedColors.brass,
            )
        }
        group.contacts.forEach { contact -> MemberRow(contact) }
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) { Text("Not duplicate") }
            TextButton(onClick = onLink) { Text("Link") }
            Button(onClick = onMerge) { Text("Merge") }
        }
    }
}

@Composable
private fun ConfidenceChip(confidence: MatchConfidence) {
    val (label, container) = when (confidence) {
        MatchConfidence.HIGH -> "Likely duplicate" to MaterialTheme.colorScheme.errorContainer
        MatchConfidence.MEDIUM -> "Possible duplicate" to MaterialTheme.colorScheme.tertiaryContainer
    }
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = container,
            disabledLabelColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = null,
    )
}

@Composable
private fun MemberRow(contact: Contact) {
    val preview = contact.rawContacts.flatMap { it.phones }.map { it.value }.distinct().firstOrNull()
        ?: contact.rawContacts.flatMap { it.emails }.map { it.value }.distinct().firstOrNull()
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(contact.displayName, style = MaterialTheme.typography.titleMedium) },
        supportingContent = preview?.let { { Text(it) } },
        leadingContent = { ContactAvatar(contact.displayName, contact.photoThumbnailUri, size = 40.dp) },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                contact.rawContacts
                    .distinctBy { it.accountType to it.accountName }
                    .forEach { raw -> AccountDot(raw.accountType, raw.accountName) }
            }
        },
    )
}

@Preview(name = "Duplicate group · light")
@Preview(name = "Duplicate group · dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DuplicateGroupPreview() {
    val group = DuplicateGroup(
        confidence = MatchConfidence.HIGH,
        matchReason = "Same number · +44 7700 900312",
        contacts = listOf(
            Contact(
                1L, "Amelia Hartwell",
                rawContacts = listOf(
                    RawContact(1L, "com.google", "rycco@gmail.com", phones = listOf(LabeledValue(1L, "+44 7700 900312", "Mobile"))),
                ),
            ),
            Contact(2L, "A. Hartwell", rawContacts = listOf(RawContact(2L, "sim", "SIM 1"))),
        ),
    )
    ContactManagerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp)) {
                DuplicateGroupCard(group, onLink = {}, onMerge = {}, onDismiss = {})
            }
        }
    }
}
