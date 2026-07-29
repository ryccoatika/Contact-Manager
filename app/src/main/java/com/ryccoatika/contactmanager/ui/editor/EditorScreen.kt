package com.ryccoatika.contactmanager.ui.editor

import android.content.res.Configuration
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryccoatika.contactmanager.data.sim.SimRouting
import com.ryccoatika.contactmanager.ui.common.AccountDot
import com.ryccoatika.contactmanager.ui.common.AccountVisuals
import com.ryccoatika.contactmanager.ui.common.PhonePermissionPrompt
import com.ryccoatika.contactmanager.ui.common.SectionCard
import com.ryccoatika.contactmanager.ui.theme.ContactManagerTheme

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
                        "Editing in $label",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                }
            } else {
                if (!state.phonePermissionAsked &&
                    state.accounts.any { SimRouting.isSimAccount(it.type) }
                ) {
                    PhonePermissionPrompt(onAsked = viewModel::markPhonePermissionAsked)
                    Spacer(Modifier.height(12.dp))
                }
                AccountPicker(state, viewModel)
            }
            if (state.simMode) {
                SimForm(state, viewModel)
            } else {
                GroupLabel("Name")
                SectionCard(Modifier.fillMaxWidth()) {
                    EditorField(state.name, viewModel::setName, "Full name")
                    FieldDivider()
                    EditorField(state.organization, viewModel::setOrganization, "Company")
                }
                ValueGroupCard("Phone", state.phones, viewModel::setPhone, viewModel::addPhone, viewModel::removePhone)
                ValueGroupCard("Email", state.emails, viewModel::setEmail, viewModel::addEmail, viewModel::removeEmail)
                GroupLabel("Note")
                SectionCard(Modifier.fillMaxWidth()) {
                    EditorField(state.note, viewModel::setNote, "Add a note", singleLine = false, minLines = 2)
                }
            }
        }
    }
}

/** A small uppercase-ish group label sitting above a [SectionCard]. */
@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
    )
}

/** Hairline between fields grouped in the same card. */
@Composable
private fun FieldDivider() {
    HorizontalDivider(
        Modifier.padding(vertical = 2.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
    )
}

/** Borderless text input; the surrounding [SectionCard] provides the frame. */
@Composable
private fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        singleLine = singleLine,
        minLines = minLines,
        isError = isError,
        supportingText = supportingText,
        trailingIcon = trailing,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

/** A titled card holding a variable-length list of values with add/remove. */
@Composable
private fun ValueGroupCard(
    label: String,
    values: List<String>,
    onValueChange: (Int, String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
) {
    GroupLabel(label)
    SectionCard(Modifier.fillMaxWidth()) {
        values.forEachIndexed { index, value ->
            if (index > 0) FieldDivider()
            EditorField(
                value = value,
                onValueChange = { onValueChange(index, it) },
                placeholder = label,
                trailing = {
                    IconButton(onClick = { onRemove(index) }) {
                        Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Remove $label")
                    }
                },
            )
        }
        FieldDivider()
        TextButton(onClick = onAdd) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(4.dp))
            Text("Add $label")
        }
    }
}

/** SIM storage only fits a name and one number; other fields are hidden. */
@Composable
private fun SimForm(state: EditorUiState, viewModel: EditorViewModel) {
    GroupLabel("SIM contact")
    SectionCard(Modifier.fillMaxWidth()) {
        EditorField(
            value = state.name,
            onValueChange = viewModel::setName,
            placeholder = "Name",
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
        FieldDivider()
        EditorField(
            value = state.phones.firstOrNull().orEmpty(),
            onValueChange = { viewModel.setPhone(0, it) },
            placeholder = "Phone",
            isError = state.simError != null,
            supportingText = state.simError?.let { error -> { Text(error) } },
        )
    }
    Text(
        "SIM contacts store a name and one phone number only.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountPicker(state: EditorUiState, viewModel: EditorViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val selected = state.selectedAccount
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        SectionCard(
            Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccountDot(selected?.type, selected?.name, size = 12.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Save to",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        selected?.let { AccountVisuals.label(it.type, it.name) } ?: "Device",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
        }
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text("${AccountVisuals.label(account.type, account.name)} · ${account.name ?: "local"}") },
                    leadingIcon = { AccountDot(account.type, account.name, size = 10.dp) },
                    onClick = {
                        viewModel.selectAccount(account)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Preview(name = "Editor fields · light")
@Preview(name = "Editor fields · dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EditorFieldsPreview() {
    ContactManagerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp)) {
                GroupLabel("Name")
                SectionCard(Modifier.fillMaxWidth()) {
                    EditorField("Amelia Hartwell", {}, "Full name")
                    FieldDivider()
                    EditorField("Hartwell & Co", {}, "Company")
                }
                ValueGroupCard("Phone", listOf("+44 7700 900312"), { _, _ -> }, {}, {})
            }
        }
    }
}
