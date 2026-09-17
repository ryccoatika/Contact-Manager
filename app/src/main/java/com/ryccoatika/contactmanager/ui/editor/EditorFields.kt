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
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.domain.sim.SimRouting
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

/** A small uppercase-ish group label sitting above a [SectionCard]. */
@Composable
internal fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
    )
}

/** Hairline between fields grouped in the same card. */
@Composable
internal fun FieldDivider() {
    HorizontalDivider(
        Modifier.padding(vertical = 2.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
    )
}

/** Borderless text input; the surrounding [SectionCard] provides the frame. */
@Composable
internal fun EditorField(
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
internal fun ValueGroupCard(
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
internal fun DateField(label: String, value: String, onPick: (String) -> Unit) {
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
