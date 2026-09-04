package com.ryccoatika.contactmanager.ui.about

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.ui.analytics.TrackScreenView
import com.ryccoatika.contactmanager.ui.theme.ContactManagerTheme
import kotlinx.coroutines.launch

/** Developer inbox + the fixed English tags used in the email subject bracket. */
private const val DEVELOPER_EMAIL = "me@ryccoatika.com"

private enum class ContactCategory(
    @StringRes val label: Int,
    val tag: String,
) {
    SUGGESTION(R.string.contact_category_suggestion, "Suggestion"),
    BUG(R.string.contact_category_bug, "Bug"),
    REVIEW(R.string.contact_category_review, "Review"),
    ASK(R.string.contact_category_ask, "Ask"),
    OTHER(R.string.contact_category_other, "Other"),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ContactDeveloperScreen(
    onBack: () -> Unit,
    embedded: Boolean = false,
) {
    TrackScreenView("contact_developer")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var category by remember { mutableStateOf(ContactCategory.SUGGESTION) }
    var expanded by remember { mutableStateOf(false) }
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    val noEmailApp = stringResource(R.string.contact_no_email_app)
    val emailCopied = stringResource(R.string.contact_email_copied)
    val clipboard = LocalClipboardManager.current

    // Compose to the developer; blank subject/body = a plain "email me" tap.
    val launchEmail: (String, String) -> Unit = { fullSubject, body ->
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$DEVELOPER_EMAIL")).apply {
            if (fullSubject.isNotEmpty()) putExtra(Intent.EXTRA_SUBJECT, fullSubject)
            if (body.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, body)
        }
        // Let the user pick which email app to use rather than jumping to a default.
        val chooser = Intent.createChooser(intent, context.getString(R.string.contact_chooser_title))
        try {
            context.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            scope.launch { snackbarHostState.showSnackbar(noEmailApp) }
        }
    }
    // Subject: "[Contact Manager][<Category>] - <subject>".
    val send: () -> Unit = {
        launchEmail("[Contact Manager][${category.tag}] - ${subject.trim()}", message.trim())
    }
    val copyEmail: () -> Unit = {
        clipboard.setText(AnnotatedString(DEVELOPER_EMAIL))
        scope.launch { snackbarHostState.showSnackbar(emailCopied) }
    }

    Scaffold(
        topBar = {
            if (!embedded) {
                TopAppBar(
                    title = { Text(stringResource(R.string.contact_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.settings_back),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.contact_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    DEVELOPER_EMAIL,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    textDecoration = TextDecoration.Underline,
                    // Tap emails the developer; long-press copies the address.
                    modifier = Modifier.combinedClickable(
                        onClick = { launchEmail("", "") },
                        onLongClick = copyEmail,
                    ),
                )
            }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
            ) {
                OutlinedTextField(
                    value = stringResource(category.label),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.contact_category_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    ContactCategory.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.label)) },
                            onClick = {
                                category = option
                                expanded = false
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text(stringResource(R.string.contact_subject_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text(stringResource(R.string.contact_message_label)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = send,
                enabled = subject.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Text(
                    stringResource(R.string.contact_send),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ContactDeveloperScreenPreview() {
    ContactManagerTheme {
        ContactDeveloperScreen(onBack = {})
    }
}
