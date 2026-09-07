package com.ryccoatika.contactmanager.ui.detail

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
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
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.domain.AccountClassifier
import com.ryccoatika.contactmanager.domain.analytics.AnalyticsEvent
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.LabeledValue
import com.ryccoatika.contactmanager.domain.model.RawContact
import com.ryccoatika.contactmanager.ui.analytics.LocalAnalytics
import com.ryccoatika.contactmanager.ui.analytics.TrackScreenView
import com.ryccoatika.contactmanager.ui.common.AccountDot
import com.ryccoatika.contactmanager.ui.common.AccountVisuals
import com.ryccoatika.contactmanager.ui.common.CapabilityTag
import com.ryccoatika.contactmanager.ui.common.ContactAvatar
import com.ryccoatika.contactmanager.ui.common.DetailSkeleton
import com.ryccoatika.contactmanager.ui.common.QuickActionPill
import com.ryccoatika.contactmanager.ui.common.SectionCard
import com.ryccoatika.contactmanager.ui.theme.ContactManagerTheme
import com.ryccoatika.contactmanager.ui.theme.TabularNums
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onEditRawContact: (Long) -> Unit,
    embedded: Boolean = false,
    // Identity handed over from the list row so the hero avatar can render while
    // the full contact still loads — the open shared-element morph needs a target
    // on its first frame. 0L / blank (tablet, previews) keeps the plain skeleton.
    contactId: Long = 0L,
    initialName: String = "",
    initialPhotoUri: String? = null,
    // The far end of the avatar shared element started in HomeScreen's list row.
    // Null on tablet / previews — the avatar just renders in place.
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    TrackScreenView("contact_detail")
    val analytics = LocalAnalytics.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
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
            if (!embedded) {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.detail_cd_back),
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
        // While loading, stand in a minimal contact from the handed-over identity
        // so the hero (and its shared avatar) is on screen for the open transition.
        val current = state.contact ?: if (state.loading && contactId != 0L) {
            Contact(
                contactId = contactId,
                displayName = initialName,
                photoThumbnailUri = initialPhotoUri?.ifEmpty { null },
            )
        } else {
            null
        }
        if (current == null) {
            DetailSkeleton(Modifier.padding(padding))
        } else {
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                val heroPhone = current.rawContacts
                    .flatMap { it.phones }
                    .firstOrNull()
                    ?.value
                val editableRawId = current.rawContacts
                    .firstOrNull { AccountClassifier.classify(it.accountType) != AccountCapability.READ_ONLY }
                    ?.rawContactId
                Spacer(Modifier.height(8.dp))
                DetailHero(
                    contact = current,
                    phone = heroPhone,
                    canEdit = editableRawId != null,
                    onCall = {
                        heroPhone?.let {
                            analytics.logEvent(AnalyticsEvent.CallContact)
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$it")))
                        }
                    },
                    onMessage = {
                        heroPhone?.let {
                            analytics.logEvent(AnalyticsEvent.MessageContact)
                            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$it")))
                        }
                    },
                    onEdit = { editableRawId?.let(onEditRawContact) },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
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
        TrackScreenView("delete_contact_confirm")
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.detail_delete_dialog_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.detail_delete_dialog_message,
                        AccountVisuals.label(context, raw.accountType, raw.accountName),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    viewModel.deleteRawContact(raw.rawContactId)
                }) { Text(stringResource(R.string.detail_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.detail_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun DetailHero(
    contact: Contact,
    phone: String?,
    canEdit: Boolean,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onEdit: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    SectionCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(116.dp), contentAlignment = Alignment.Center) {
                // Soft pine glow behind the avatar.
                Box(
                    Modifier.matchParentSize().clip(CircleShape).background(
                        Brush.radialGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                Color.Transparent,
                            ),
                        ),
                    ),
                )
                val avatarModifier =
                    if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                        with(sharedTransitionScope) {
                            Modifier.sharedElement(
                                rememberSharedContentState(key = "contact-avatar-${contact.contactId}"),
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                        }
                    } else {
                        Modifier
                    }
                ContactAvatar(contact.displayName, contact.photoThumbnailUri, size = 88.dp, modifier = avatarModifier)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                contact.displayName,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            contact.rawContacts.firstNotNullOfOrNull { it.organization?.takeIf(String::isNotBlank) }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (phone != null || canEdit) {
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (phone != null) {
                        QuickActionPill(stringResource(R.string.detail_action_call), Icons.Default.Call, onCall, Modifier.weight(1f))
                        QuickActionPill(
                            stringResource(R.string.detail_action_message),
                            Icons.AutoMirrored.Filled.Message,
                            onMessage,
                            Modifier.weight(1f),
                        )
                    }
                    if (canEdit) {
                        QuickActionPill(stringResource(R.string.detail_action_edit), Icons.Default.Edit, onEdit, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun RawContactCard(
    raw: RawContact,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val capability = AccountClassifier.classify(raw.accountType)
    val readOnly = capability == AccountCapability.READ_ONLY
    val accountLabel = AccountVisuals.label(context, raw.accountType, raw.accountName)

    SectionCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccountDot(raw.accountType, raw.accountName, size = 10.dp)
            Spacer(Modifier.width(8.dp))
            Text(accountLabel, style = MaterialTheme.typography.titleSmall)
            if (capability == AccountCapability.SIM) {
                Spacer(Modifier.width(8.dp))
                CapabilityTag(AccountCapability.SIM)
            }
        }
        Spacer(Modifier.height(8.dp))
        raw.phones.forEach { LabeledValueRow(it, fallbackLabel = stringResource(R.string.detail_label_phone)) }
        raw.emails.forEach { LabeledValueRow(it, fallbackLabel = stringResource(R.string.detail_label_email)) }
        raw.websites.forEach { LabeledValueRow(it, fallbackLabel = stringResource(R.string.detail_label_website)) }
        raw.addresses.forEach { LabeledValueRow(it, fallbackLabel = stringResource(R.string.detail_label_address)) }
        raw.organization?.let { FieldRow(label = stringResource(R.string.detail_label_company), value = it) }
        raw.jobTitle?.let { FieldRow(label = stringResource(R.string.detail_label_job_title), value = it) }
        raw.nickname?.let { FieldRow(label = stringResource(R.string.detail_label_nickname), value = it) }
        raw.birthday?.let { FieldRow(label = stringResource(R.string.detail_label_birthday), value = formatContactDate(it)) }
        raw.anniversary?.let { FieldRow(label = stringResource(R.string.detail_label_anniversary), value = formatContactDate(it)) }
        raw.note?.let { FieldRow(label = stringResource(R.string.detail_label_note), value = it) }
        Spacer(Modifier.height(8.dp))
        if (readOnly) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.detail_managed_by, accountLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit) { Text(stringResource(R.string.detail_edit)) }
                OutlinedButton(onClick = onDelete) { Text(stringResource(R.string.detail_delete)) }
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
        Text(value, style = TabularNums.merge(MaterialTheme.typography.bodyLarge))
    }
}

// Provider dates are ISO "yyyy-MM-dd" (UTC); show a friendly form, keep the raw
// string for year-less "--MM-dd" values that don't parse.
private val detailIsoDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    .apply { timeZone = TimeZone.getTimeZone("UTC") }
private val detailPrettyDate = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    .apply { timeZone = TimeZone.getTimeZone("UTC") }

private fun formatContactDate(iso: String): String = try {
    detailIsoDate.parse(iso)?.let { detailPrettyDate.format(it) } ?: iso
} catch (e: Exception) {
    iso
}

@Preview(name = "Detail · light")
@Preview(name = "Detail · dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DetailPreview() {
    val contact = Contact(
        contactId = 1L,
        displayName = "Amelia Hartwell",
        rawContacts = listOf(
            RawContact(
                rawContactId = 1L,
                accountType = "com.google",
                accountName = "rycco@gmail.com",
                organization = "Hartwell & Co",
                phones = listOf(LabeledValue(1L, "+44 7700 900312", "Mobile")),
                emails = listOf(LabeledValue(2L, "amelia@hartwell.co", "Work")),
            ),
        ),
    )
    ContactManagerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp)) {
                DetailHero(contact, phone = "+44 7700 900312", canEdit = true, onCall = {}, onMessage = {}, onEdit = {})
                Spacer(Modifier.height(12.dp))
                RawContactCard(contact.rawContacts.first(), onEdit = {}, onDelete = {})
            }
        }
    }
}
