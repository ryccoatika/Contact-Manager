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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomAppBar
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryccoatika.contactmanager.domain.AccountClassifier
import com.ryccoatika.contactmanager.domain.MovePlan
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.domain.model.RawContact
import android.content.res.Configuration
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.ryccoatika.contactmanager.domain.model.LabeledValue
import com.ryccoatika.contactmanager.ui.common.AccountDot
import com.ryccoatika.contactmanager.ui.common.AccountVisuals
import com.ryccoatika.contactmanager.ui.common.ContactAvatar
import com.ryccoatika.contactmanager.ui.common.ContactListSkeleton
import com.ryccoatika.contactmanager.ui.common.SearchField
import com.ryccoatika.contactmanager.ui.common.SelectedAvatar
import com.ryccoatika.contactmanager.ui.theme.ContactManagerTheme
import com.ryccoatika.contactmanager.ui.theme.TabularNums
import kotlin.math.abs
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onContactClick: (Long) -> Unit,
    onAddClick: () -> Unit,
    onAccountsClick: () -> Unit,
    onDuplicatesClick: () -> Unit,
    pendingFilterAccountKey: String? = null,
    onPendingFilterConsumed: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val batchProgress by viewModel.batchProgress.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showMovePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMergePicker by remember { mutableStateOf(false) }
    var pendingMove by remember { mutableStateOf<Pair<ContactAccount, MovePlan>?>(null) }
    var pendingMergeTarget by remember { mutableStateOf<Pair<Contact, RawContact>?>(null) }

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
                snackbarHostState.showSnackbar(
                    progress.error ?: "Moved ${progress.total} contact${if (progress.total == 1) "" else "s"}",
                )
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
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            } else {
                TopAppBar(
                    title = { Text("Contacts") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                    actions = {
                        IconButton(onClick = onDuplicatesClick) {
                            BadgedBox(
                                badge = {
                                    if (state.duplicateCount > 0) {
                                        Badge { Text("${state.duplicateCount}") }
                                    }
                                },
                            ) {
                                Icon(Icons.Default.Difference, contentDescription = "Duplicates")
                            }
                        }
                        IconButton(onClick = onAccountsClick) {
                            Icon(Icons.Default.ManageAccounts, contentDescription = "Accounts")
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (state.selectionMode) {
                BottomAppBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showMovePicker = true }) { Text("Move to…") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showDeleteConfirm = true }) { Text("Delete") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = { showMergePicker = true },
                        enabled = state.selectedContactIds.size >= 2,
                    ) { Text("Merge") }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!state.selectionMode) {
                FloatingActionButton(
                    onClick = onAddClick,
                    shape = MaterialTheme.shapes.medium,
                    // Shift clear of the fast-scroll rail when it's shown.
                    modifier = if (state.contacts.size > FAST_SCROLL_MIN_CONTACTS) {
                        Modifier.padding(end = 28.dp)
                    } else {
                        Modifier
                    },
                ) {
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    progress.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "${progress.done}/${progress.total}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = {
                                    if (progress.total == 0) 0f
                                    else progress.done.toFloat() / progress.total
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        // IconButton keeps the 48dp minimum touch target.
                        IconButton(onClick = viewModel::cancelBatch) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel batch operation")
                        }
                    }
                }
            }
            SearchField(
                query = state.query,
                placeholder = "Search ${state.contacts.size} contacts",
                onQueryChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                trailing = if (state.query.isNotEmpty()) {
                    {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                } else {
                    null
                },
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
                        leadingIcon = { AccountDot(account.type, account.name, size = 10.dp) },
                    )
                }
            }
            if (state.loading) {
                ContactListSkeleton()
            } else if (state.contacts.isEmpty()) {
                EmptyState(
                    query = state.query,
                    accountFiltered = state.selectedAccountKey != null,
                )
            } else {
                val listState = rememberLazyListState()
                val scope = rememberCoroutineScope()
                val sections by remember { derivedStateOf { sectionsOf(state.contacts) } }
                val letterIndex by remember {
                    derivedStateOf {
                        buildMap {
                            var index = 0
                            sections.forEach { section ->
                                put(section.letter, index)
                                index += section.contacts.size + 1
                            }
                        }
                    }
                }
                val railShown = state.contacts.size > FAST_SCROLL_MIN_CONTACTS
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        // Keep row content (incl. trailing provenance dots) clear of the rail.
                        contentPadding = PaddingValues(end = if (railShown) 30.dp else 0.dp),
                    ) {
                        sections.forEach { section ->
                            stickyHeader(key = "header-${section.letter}") {
                                SectionHeader(section.letter)
                            }
                            items(section.contacts, key = { it.contactId }) { contact ->
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
                    if (railShown) {
                        AlphabetRail(
                            onLetterSelected = { letter ->
                                nearestSectionIndex(letter, letterIndex)?.let { index ->
                                    scope.launch { listState.animateScrollToItem(index) }
                                }
                            },
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                }
            }
        }
    }

    if (showMovePicker) {
        ModalBottomSheet(
            onDismissRequest = { showMovePicker = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
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
                        leadingContent = { AccountDot(account.type, account.name, size = 12.dp) },
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

    if (showMergePicker) {
        val selectedContacts = state.contacts.filter { it.contactId in state.selectedContactIds }
        val memberRaws = selectedContacts.flatMap { contact -> contact.rawContacts.map { contact to it } }
        ModalBottomSheet(
            onDismissRequest = { showMergePicker = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Text(
                "Merge ${state.selectedContactIds.size} selected into",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            memberRaws
                // Read-only raw contacts can't receive data, so they can't be targets.
                .filter { (_, raw) ->
                    AccountClassifier.classify(raw.accountType) != AccountCapability.READ_ONLY
                }
                .forEach { (contact, raw) ->
                    ListItem(
                        modifier = Modifier.clickable {
                            showMergePicker = false
                            pendingMergeTarget = contact to raw
                        },
                        headlineContent = { Text(contact.displayName) },
                        supportingContent = {
                            Text(AccountVisuals.label(raw.accountType, raw.accountName))
                        },
                        leadingContent = { AccountDot(raw.accountType, raw.accountName, size = 12.dp) },
                    )
                }
            Spacer(Modifier.height(24.dp))
        }
    }

    pendingMergeTarget?.let { (targetContact, targetRaw) ->
        val sources = state.contacts
            .filter { it.contactId in state.selectedContactIds }
            .flatMap { contact -> contact.rawContacts.map { contact to it } }
            .filter { (_, raw) -> raw.rawContactId != targetRaw.rawContactId }
        val readOnly = sources.filter { (_, raw) ->
            AccountClassifier.classify(raw.accountType) == AccountCapability.READ_ONLY
        }
        AlertDialog(
            onDismissRequest = { pendingMergeTarget = null },
            title = { Text("Merge ${sources.size + 1} entries?") },
            text = {
                Text(
                    buildString {
                        append(
                            "${sources.size} entries will be merged into ${targetContact.displayName} " +
                                "(${AccountVisuals.label(targetRaw.accountType, targetRaw.accountName)}) and removed.",
                        )
                        if (readOnly.isNotEmpty()) {
                            append("\n\n")
                            append(
                                readOnly.joinToString { (contact, raw) ->
                                    "${contact.displayName} (${AccountVisuals.label(raw.accountType, raw.accountName)})"
                                },
                            )
                            append(" will be linked (managed by app).")
                        }
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingMergeTarget = null
                    viewModel.mergeSelected(targetRaw)
                }) { Text("Merge") }
            },
            dismissButton = {
                TextButton(onClick = { pendingMergeTarget = null }) { Text("Cancel") }
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

/** Fast-scroll only earns its screen space on long lists. */
private const val FAST_SCROLL_MIN_CONTACTS = 30

private val RAIL_LETTERS = ('A'..'Z').toList() + '#'

private data class ContactSection(val letter: Char, val contacts: List<Contact>)

/** First-letter sections in list order: A–Z first, non-letter names under "#" at the end. */
private fun sectionsOf(contacts: List<Contact>): List<ContactSection> {
    val grouped = contacts.groupBy { contact ->
        val first = contact.displayName.trim().firstOrNull()?.uppercaseChar()
        if (first?.isLetter() == true) first else '#'
    }
    val letters = grouped.keys.filter { it != '#' }.sorted()
    return (letters + listOfNotNull('#'.takeIf { it in grouped }))
        .map { ContactSection(it, grouped.getValue(it)) }
}

/**
 * List index of the section for [letter], falling back to the alphabetically
 * closest populated section so dragging over empty letters still tracks.
 */
private fun nearestSectionIndex(letter: Char, letterIndex: Map<Char, Int>): Int? {
    if (letterIndex.isEmpty()) return null
    letterIndex[letter]?.let { return it }
    if (letter == '#') return letterIndex.values.max()
    val closest = letterIndex.keys.filter { it != '#' }.minByOrNull { abs(it - letter) }
    return letterIndex[closest ?: '#']
}

@Composable
private fun SectionHeader(letter: Char) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
        Text(
            letter.toString(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

/** Slim A–Z + # rail on the right edge; tap or drag to jump between sections. */
@Composable
private fun AlphabetRail(
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    var railHeightPx by remember { mutableIntStateOf(0) }
    var active by remember { mutableStateOf<Char?>(null) }
    fun letterAt(y: Float): Char? {
        if (railHeightPx <= 0) return null
        val index = (y / railHeightPx * RAIL_LETTERS.size).toInt()
        return RAIL_LETTERS[index.coerceIn(0, RAIL_LETTERS.lastIndex)]
    }
    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(vertical = 8.dp, horizontal = 4.dp)
                .width(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f))
                .onSizeChanged { railHeightPx = it.height }
                // One gesture owns both a tap and a drag, so a plain tap jumps too.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        letterAt(down.position.y)?.let { active = it; onLetterSelected(it) }
                        var change = down
                        while (change.pressed) {
                            val event = awaitPointerEvent()
                            change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.pressed) {
                                change.consume()
                                letterAt(change.position.y)?.let {
                                    if (it != active) onLetterSelected(it)
                                    active = it
                                }
                            }
                        }
                        active = null
                    }
                },
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RAIL_LETTERS.forEach { letter ->
                val on = letter == active
                Text(
                    letter.toString(),
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        // Touch hint: a large centered bubble of the letter under the finger.
        active?.let { letter ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(96.dp)
                    .shadow(10.dp, CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    letter.toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.displaySmall,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(query: String, accountFiltered: Boolean) {
    val (title, hint) = when {
        query.isNotBlank() -> "No matches for \"${query.trim()}\"" to
            "Try a different name, number or email."
        accountFiltered -> "No contacts in this account" to
            "Pick another account or move contacts into it."
        else -> "No contacts yet" to "Tap + to add your first contact."
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (query.isNotBlank()) Icons.Default.SearchOff else Icons.Default.Contacts,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        modifier = Modifier
            .heightIn(min = 64.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
            Text(contact.displayName, style = MaterialTheme.typography.titleMedium)
        },
        supportingContent = {
            contactSubtitle(contact)?.let {
                Text(
                    it,
                    style = TabularNums.merge(MaterialTheme.typography.bodyMedium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        },
        leadingContent = {
            if (selected) SelectedAvatar() else ContactAvatar(contact.displayName, contact.photoThumbnailUri)
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                contact.rawContacts
                    .distinctBy { it.accountType to it.accountName }
                    .forEach { raw -> AccountDot(raw.accountType, raw.accountName) }
            }
        },
    )
}

/** First non-blank of: organization, a phone, an email — a hint under the name. */
private fun contactSubtitle(contact: Contact): String? =
    contact.rawContacts.firstNotNullOfOrNull { it.organization?.takeIf(String::isNotBlank) }
        ?: contact.rawContacts.flatMap { it.phones }.firstOrNull()?.value
        ?: contact.rawContacts.flatMap { it.emails }.firstOrNull()?.value

private fun previewContact() = Contact(
    contactId = 1L,
    displayName = "Amelia Hartwell",
    rawContacts = listOf(
        RawContact(
            rawContactId = 1L,
            accountType = "com.google",
            accountName = "rycco@gmail.com",
            organization = "Hartwell & Co",
            phones = listOf(LabeledValue(1L, "+44 7700 900312", "Mobile")),
        ),
        RawContact(rawContactId = 2L, accountType = "sim", accountName = "SIM 1"),
    ),
)

@Preview(name = "Contact row · light")
@Preview(name = "Contact row · dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ContactRowPreview() {
    ContactManagerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ContactRow(previewContact(), selected = false, onClick = {}, onLongClick = {})
        }
    }
}
