package com.ryccoatika.contactmanager.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryccoatika.contactmanager.ui.common.AccountVisuals

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val requestClose = {
        if (state.dirty && !state.saving) showDiscardConfirm = true else onBack()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is EditorEvent.Saved -> onBack()
                is EditorEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    BackHandler(enabled = state.dirty && !state.saving) { showDiscardConfirm = true }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Discard changes?") },
            text = { Text("Your edits have not been saved.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    onBack()
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) { Text("Keep editing") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEdit) "Edit contact" else "New contact") },
                navigationIcon = {
                    IconButton(onClick = requestClose) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    if (state.saving) {
                        CircularProgressIndicator(
                            Modifier.padding(end = 16.dp).size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        TextButton(onClick = viewModel::save, enabled = state.canSave) {
                            Text("Save")
                        }
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
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (state.isEdit) {
                state.fixedAccountLabel?.let { label ->
                    Text(
                        "Account: $label",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                }
            } else {
                AccountPicker(state, viewModel)
                Spacer(Modifier.height(16.dp))
            }
            if (state.simMode) {
                SimForm(state, viewModel)
            } else {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    singleLine = true,
                )
                Spacer(Modifier.height(16.dp))
                DynamicValueList(
                    label = "Phone",
                    values = state.phones,
                    onValueChange = viewModel::setPhone,
                    onAdd = viewModel::addPhone,
                    onRemove = viewModel::removePhone,
                )
                Spacer(Modifier.height(16.dp))
                DynamicValueList(
                    label = "Email",
                    values = state.emails,
                    onValueChange = viewModel::setEmail,
                    onAdd = viewModel::addEmail,
                    onRemove = viewModel::removeEmail,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = state.organization,
                    onValueChange = viewModel::setOrganization,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Organization") },
                    singleLine = true,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = state.note,
                    onValueChange = viewModel::setNote,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Note") },
                    minLines = 2,
                )
            }
        }
    }
}

/** SIM storage only fits a name and one number; other fields are hidden. */
@Composable
private fun SimForm(state: EditorUiState, viewModel: EditorViewModel) {
    OutlinedTextField(
        value = state.name,
        onValueChange = viewModel::setName,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Name") },
        singleLine = true,
        isError = state.simNameTooLong,
        supportingText = {
            Text(
                if (state.simNameTooLong) {
                    "${state.name.length}/${state.simMaxNameLength} — too long for SIM"
                } else {
                    "${state.name.length}/${state.simMaxNameLength}"
                },
            )
        },
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = state.phones.firstOrNull().orEmpty(),
        onValueChange = { viewModel.setPhone(0, it) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Phone") },
        singleLine = true,
        isError = state.simError != null,
        supportingText = state.simError?.let { error -> { Text(error) } },
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "SIM contacts store a name and one phone number only.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountPicker(state: EditorUiState, viewModel: EditorViewModel) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = state.selectedAccount
                ?.let { AccountVisuals.label(it.type, it.name) }
                ?: "Device",
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            label = { Text("Save to account") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text("${AccountVisuals.label(account.type, account.name)} · ${account.name ?: "local"}") },
                    onClick = {
                        viewModel.selectAccount(account)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun DynamicValueList(
    label: String,
    values: List<String>,
    onValueChange: (Int, String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
) {
    Column {
        values.forEachIndexed { index, value ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { onValueChange(index, it) },
                    modifier = Modifier.weight(1f),
                    label = { Text(label) },
                    singleLine = true,
                )
                IconButton(onClick = { onRemove(index) }) {
                    Icon(
                        Icons.Default.RemoveCircleOutline,
                        contentDescription = "Remove $label",
                    )
                }
            }
            if (index < values.lastIndex) Spacer(Modifier.height(8.dp))
        }
        TextButton(onClick = onAdd) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(4.dp))
            Text("Add $label")
        }
    }
}
