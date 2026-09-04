package com.ryccoatika.contactmanager.ui.editor

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.data.sim.SimRouting
import com.ryccoatika.contactmanager.ui.analytics.TrackScreenView
import com.ryccoatika.contactmanager.ui.common.AccountDot
import com.ryccoatika.contactmanager.ui.common.AccountVisuals
import com.ryccoatika.contactmanager.ui.common.CardsSkeleton
import com.ryccoatika.contactmanager.ui.common.PhonePermissionPrompt
import com.ryccoatika.contactmanager.ui.common.SectionCard
import com.ryccoatika.contactmanager.ui.theme.ContactManagerTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onBack: () -> Unit,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TrackScreenView(if (state.isEdit) "editor_edit" else "editor_new")
    // While the open transition is still running, keep the screen light (top bar
    // only) so the spread stays smooth; compose the heavy form once it settles.
    // Only defers on ENTER — on exit the form stays put and animates out intact.
    val entering = animatedVisibilityScope?.transition?.let {
        it.targetState == EnterExitState.Visible && it.currentState != EnterExitState.Visible
    } ?: false
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
        TrackScreenView("discard_edit")
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text(stringResource(R.string.editor_discard_title)) },
            text = { Text(stringResource(R.string.editor_discard_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    onBack()
                }) { Text(stringResource(R.string.editor_discard_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) { Text(stringResource(R.string.editor_discard_keep_editing)) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isEdit) stringResource(R.string.editor_title_edit) else stringResource(R.string.editor_title_new),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = requestClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.editor_cancel))
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
                            Text(stringResource(R.string.editor_save))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (entering) {
            // Spread animates an empty screen (top bar only); form comes next frame.
            Box(Modifier.padding(padding).fillMaxSize())
            return@Scaffold
        }
        if (state.loading) {
            CardsSkeleton(Modifier.padding(padding), count = 4, height = 84.dp)
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                // Shrink the scroll viewport by the keyboard height so the last
                // fields (note) can scroll clear of the IME instead of hiding behind it.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (state.isEdit) {
                state.fixedAccountLabel?.let { label ->
                    Text(
                        stringResource(R.string.editor_editing_in, label),
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
                GroupLabel(stringResource(R.string.editor_name))
                SectionCard(Modifier.fillMaxWidth()) {
                    EditorField(state.name, viewModel::setName, stringResource(R.string.editor_full_name))
                    FieldDivider()
                    EditorField(state.nickname, viewModel::setNickname, stringResource(R.string.editor_nickname))
                    FieldDivider()
                    EditorField(state.organization, viewModel::setOrganization, stringResource(R.string.editor_company))
                    FieldDivider()
                    EditorField(state.jobTitle, viewModel::setJobTitle, stringResource(R.string.editor_job_title))
                }
                ValueGroupCard(
                    stringResource(R.string.editor_phone),
                    state.phones,
                    viewModel::setPhone,
                    viewModel::addPhone,
                    viewModel::removePhone,
                )
                ValueGroupCard(
                    stringResource(R.string.editor_email),
                    state.emails,
                    viewModel::setEmail,
                    viewModel::addEmail,
                    viewModel::removeEmail,
                )
                ValueGroupCard(
                    stringResource(R.string.editor_website),
                    state.websites,
                    viewModel::setWebsite,
                    viewModel::addWebsite,
                    viewModel::removeWebsite,
                )
                ValueGroupCard(
                    stringResource(R.string.editor_address),
                    state.addresses,
                    viewModel::setAddress,
                    viewModel::addAddress,
                    viewModel::removeAddress,
                    singleLine = false,
                )
                GroupLabel(stringResource(R.string.editor_dates))
                SectionCard(Modifier.fillMaxWidth()) {
                    DateField(stringResource(R.string.editor_birthday), state.birthday, viewModel::setBirthday)
                    FieldDivider()
                    DateField(stringResource(R.string.editor_anniversary), state.anniversary, viewModel::setAnniversary)
                }
                GroupLabel(stringResource(R.string.editor_note))
                SectionCard(Modifier.fillMaxWidth()) {
                    EditorField(state.note, viewModel::setNote, stringResource(R.string.editor_add_note), singleLine = false, minLines = 2)
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
    singleLine: Boolean = true,
) {
    GroupLabel(label)
    SectionCard(Modifier.fillMaxWidth()) {
        values.forEachIndexed { index, value ->
            if (index > 0) FieldDivider()
            EditorField(
                value = value,
                onValueChange = { onValueChange(index, it) },
                placeholder = label,
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 2,
                trailing = {
                    IconButton(onClick = { onRemove(index) }) {
                        Icon(Icons.Default.RemoveCircleOutline, contentDescription = stringResource(R.string.editor_remove_field, label))
                    }
                },
            )
        }
        FieldDivider()
        TextButton(onClick = onAdd) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(4.dp))
            Text(stringResource(R.string.editor_add_field, label))
        }
    }
}

/** A tappable row that opens a date picker; stores/clears an ISO "yyyy-MM-dd" string. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, value: String, onPick: (String) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { showPicker = true }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.CalendarMonth,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (value.isBlank()) stringResource(R.string.editor_add_field, label) else prettyDate(value),
                style = MaterialTheme.typography.bodyLarge,
                color = if (value.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        if (value.isNotBlank()) {
            IconButton(onClick = { onPick("") }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.editor_clear_field, label))
            }
        }
    }
    if (showPicker) {
        TrackScreenView("date_picker")
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = isoToMillis(value))
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showPicker = false
                    pickerState.selectedDateMillis?.let { onPick(millisToIso(it)) }
                }) { Text(stringResource(R.string.editor_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.editor_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

// Dates are stored as provider ISO "yyyy-MM-dd" in UTC; the picker is UTC too.
private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    .apply { timeZone = TimeZone.getTimeZone("UTC") }
private val prettyFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    .apply { timeZone = TimeZone.getTimeZone("UTC") }

private fun prettyDate(iso: String): String = try {
    isoFormat.parse(iso)?.let { prettyFormat.format(it) } ?: iso
} catch (e: Exception) {
    iso
}

private fun isoToMillis(iso: String): Long? = try {
    isoFormat.parse(iso)?.time
} catch (e: Exception) {
    null
}

private fun millisToIso(millis: Long): String = isoFormat.format(Date(millis))

/** SIM storage only fits a name and one number; other fields are hidden. */
@Composable
private fun SimForm(state: EditorUiState, viewModel: EditorViewModel) {
    GroupLabel(stringResource(R.string.editor_sim_contact))
    SectionCard(Modifier.fillMaxWidth()) {
        EditorField(
            value = state.name,
            onValueChange = viewModel::setName,
            placeholder = stringResource(R.string.editor_name),
            isError = state.simNameTooLong,
            supportingText = {
                Text(
                    if (state.simNameTooLong) {
                        stringResource(
                            R.string.editor_sim_name_counter_too_long,
                            state.name.length,
                            state.simMaxNameLength,
                        )
                    } else {
                        stringResource(
                            R.string.editor_sim_name_counter,
                            state.name.length,
                            state.simMaxNameLength,
                        )
                    },
                )
            },
        )
        FieldDivider()
        EditorField(
            value = state.phones.firstOrNull().orEmpty(),
            onValueChange = { viewModel.setPhone(0, it) },
            placeholder = stringResource(R.string.editor_phone),
            isError = state.simError != null,
            supportingText = state.simError?.let { error -> { Text(error) } },
        )
    }
    Text(
        stringResource(R.string.editor_sim_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountPicker(state: EditorUiState, viewModel: EditorViewModel) {
    val context = LocalContext.current
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
                        stringResource(R.string.editor_save_to),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        selected?.let { AccountVisuals.label(context, it.type, it.name) } ?: stringResource(R.string.editor_device),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
        }
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.accounts.forEach { account ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                R.string.editor_account_option,
                                AccountVisuals.label(context, account.type, account.name),
                                account.name ?: stringResource(R.string.editor_account_local),
                            ),
                        )
                    },
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
