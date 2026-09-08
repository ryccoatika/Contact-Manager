package com.ryccoatika.contactmanager.ui.home

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.domain.AccountClassifier
import com.ryccoatika.contactmanager.domain.MovePlan
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.AccountOpMode
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.domain.model.LabeledValue
import com.ryccoatika.contactmanager.domain.model.RawContact
import com.ryccoatika.contactmanager.ui.analytics.TrackScreenView
import com.ryccoatika.contactmanager.ui.common.AccountDot
import com.ryccoatika.contactmanager.ui.common.AccountOpConfirmDialog
import com.ryccoatika.contactmanager.ui.common.AccountOpTargetSheet
import com.ryccoatika.contactmanager.ui.common.AccountVisuals
import com.ryccoatika.contactmanager.ui.common.AlphabetRail
import com.ryccoatika.contactmanager.ui.common.CollectUiEvents
import com.ryccoatika.contactmanager.ui.common.ContactAvatar
import com.ryccoatika.contactmanager.ui.common.ContactListSkeleton
import com.ryccoatika.contactmanager.ui.common.EmptyState
import com.ryccoatika.contactmanager.ui.common.SearchField
import com.ryccoatika.contactmanager.ui.common.SelectedAvatar
import com.ryccoatika.contactmanager.ui.review.rememberReviewLauncher
import com.ryccoatika.contactmanager.ui.theme.ContactManagerTheme
import com.ryccoatika.contactmanager.ui.theme.TabularNums
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    onContactClick: (Contact) -> Unit,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDuplicatesClick: () -> Unit,
    pendingFilterAccountKey: String? = null,
    onPendingFilterConsumed: () -> Unit = {},
    embedded: Boolean = false,
    // Set only from the phone nav graph, to morph a row's avatar into the detail
    // header. Null on tablet / in previews — the avatar just renders in place.
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    TrackScreenView("home")
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val batchProgress by viewModel.batchProgress.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val launchReview = rememberReviewLauncher()
    // Hoisted from the list: the FAB (in the Scaffold slot) reacts to its scroll.
    val listState = rememberLazyListState()
    // FAB hides while scrolling down, shows when scrolling up or back at the top.
    var fabVisible by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        var lastIndex = 0
        var lastOffset = 0
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val scrollingDown = index > lastIndex || (index == lastIndex && offset > lastOffset)
                val atTop = index == 0 && offset == 0
                fabVisible = atTop || !scrollingDown
                lastIndex = index
                lastOffset = offset
            }
    }
    // The one row whose avatar carries the shared-element modifier: the last
    // tapped contact. Keeps every other row free of shared-element bookkeeping
    // during scroll. Saveable so the pop-back morph still finds the row after
    // Home left composition while the detail screen was open.
    var transitionContactId by rememberSaveable { mutableStateOf<Long?>(null) }
    // In selection mode, Back clears the selection instead of leaving the app.
    BackHandler(enabled = state.selectionMode) { viewModel.clearSelection() }
    // Selection move/copy: which picker is open, and a parked op with field losses.
    var selectionPickerMode by remember { mutableStateOf<AccountOpMode?>(null) }
    var pendingSelectionOp by remember { mutableStateOf<Triple<AccountOpMode, ContactAccount, MovePlan>?>(null) }
    // Account-chip long-press: "move/copy all contacts of <account> to…" flow.
    var accountOpRequest by remember { mutableStateOf<Pair<AccountOpMode, ContactAccount>?>(null) }
    val pendingAccountOp by viewModel.pendingAccountOp.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pendingDeleteContact by remember { mutableStateOf<Contact?>(null) }
    var showMergePicker by remember { mutableStateOf(false) }
    var pendingMergeTarget by remember { mutableStateOf<Pair<Contact, RawContact>?>(null) }

    LaunchedEffect(pendingFilterAccountKey) {
        if (pendingFilterAccountKey != null) {
            viewModel.selectAccount(pendingFilterAccountKey)
            onPendingFilterConsumed()
        }
    }
    CollectUiEvents(viewModel.events, snackbarHostState)
    // A completed merge is a review-worthy moment; ask Play (it decides + throttles).
    LaunchedEffect(Unit) {
        viewModel.requestReview.collect { launchReview() }
    }
    LaunchedEffect(Unit) {
        viewModel.batchProgress.collect { progress ->
            if (progress?.finished == true) {
                viewModel.onBatchFinishedShown()
                // Only a clean move (no error) counts as a review-worthy moment;
                // fire before the snackbar, which suspends until it's dismissed.
                if (progress.error == null) launchReview()
                snackbarHostState.showSnackbar(
                    progress.error ?: progress.finishedMessage ?: context.resources.getQuantityString(
                        R.plurals.home_moved_contacts,
                        progress.total,
                        progress.total,
                    ),
                )
            }
        }
    }

    Scaffold(
        topBar = {
            if (state.selectionMode) {
                SelectionTopBar(
                    selectedCount = state.selectedContactIds.size,
                    onClearSelection = viewModel::clearSelection,
                )
            } else if (!embedded) {
                HomeTopBar(
                    duplicateCount = state.duplicateCount,
                    onDuplicatesClick = onDuplicatesClick,
                    onSettingsClick = onSettingsClick,
                )
            }
        },
        bottomBar = {
            if (state.selectionMode) {
                SelectionBottomBar(
                    mergeEnabled = state.selectedContactIds.size >= 2,
                    onMove = { selectionPickerMode = AccountOpMode.MOVE },
                    onCopy = { selectionPickerMode = AccountOpMode.COPY },
                    onDelete = { showDeleteConfirm = true },
                    onMerge = { showMergePicker = true },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            AnimatedVisibility(
                visible = fabVisible && !state.selectionMode,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
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
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.home_new_contact),
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            batchProgress?.takeIf { !it.finished }?.let { progress ->
                BatchProgressBar(
                    label = progress.label,
                    done = progress.done,
                    total = progress.total,
                    onCancel = viewModel::cancelBatch,
                )
            }
            // Local input state keeps the cursor stable while typing; filtering is
            // debounced to the ViewModel so the list only re-filters after a pause.
            var queryInput by remember { mutableStateOf(state.query) }
            LaunchedEffect(state.query) {
                if (state.query != queryInput) queryInput = state.query
            }
            LaunchedEffect(queryInput) {
                delay(500)
                if (queryInput != state.query) viewModel.setQuery(queryInput)
            }
            SearchField(
                query = queryInput,
                placeholder = stringResource(R.string.home_search_hint, state.contacts.size),
                onQueryChange = { queryInput = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                trailing = if (queryInput.isNotEmpty()) {
                    {
                        IconButton(onClick = {
                            queryInput = ""
                            viewModel.setQuery("")
                        }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.home_clear_search),
                            )
                        }
                    }
                } else {
                    null
                },
            )
            AccountChipsRow(
                accounts = state.accounts,
                selectedAccountKey = state.selectedAccountKey,
                onSelect = viewModel::selectAccount,
                onMoveAll = { accountOpRequest = AccountOpMode.MOVE to it },
                onCopyAll = { accountOpRequest = AccountOpMode.COPY to it },
                onHide = viewModel::hideAccount,
            )
            if (state.loading) {
                ContactListSkeleton()
            } else if (state.contacts.isEmpty()) {
                val (title, hint) = when {
                    state.query.isNotBlank() -> {
                        stringResource(R.string.home_empty_search_title, state.query.trim()) to
                            stringResource(R.string.home_empty_search_hint)
                    }

                    state.selectedAccountKey != null -> {
                        stringResource(R.string.home_empty_account_title) to
                            stringResource(R.string.home_empty_account_hint)
                    }

                    else -> {
                        stringResource(R.string.home_empty_none_title) to
                            stringResource(R.string.home_empty_none_hint)
                    }
                }
                EmptyState(
                    icon = if (state.query.isNotBlank()) Icons.Default.SearchOff else Icons.Default.Contacts,
                    title = title,
                    hint = hint,
                )
            } else {
                HomeContactList(
                    contacts = state.contacts,
                    rowExtras = state.rowExtras,
                    selectedContactIds = state.selectedContactIds,
                    selectionMode = state.selectionMode,
                    listState = listState,
                    transitionContactId = transitionContactId,
                    currentSelection = { viewModel.uiState.value.selectedContactIds },
                    onSetSelection = viewModel::setSelection,
                    onToggleSelect = viewModel::toggleSelect,
                    onOpenContact = { contact ->
                        // Mark before navigating so this row's avatar carries the
                        // shared-element modifier when the transition snapshots
                        // the outgoing screen.
                        transitionContactId = contact.contactId
                        onContactClick(contact)
                    },
                    onDeleteRequest = { pendingDeleteContact = it },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
        }
    }

    selectionPickerMode?.let { mode ->
        SelectionTargetSheet(
            mode = mode,
            selectedCount = state.selectedContactIds.size,
            accounts = state.accounts,
            onPick = { account ->
                selectionPickerMode = null
                val plan = when (mode) {
                    AccountOpMode.MOVE -> viewModel.planMove(account)
                    AccountOpMode.COPY -> viewModel.planCopy(account)
                }
                if (plan.losses.isEmpty()) {
                    viewModel.runSelectionOp(mode, account)
                } else {
                    pendingSelectionOp = Triple(mode, account, plan)
                }
            },
            onDismiss = { selectionPickerMode = null },
        )
    }

    pendingSelectionOp?.let { (mode, account, plan) ->
        FieldsLostDialog(
            mode = mode,
            account = account,
            plan = plan,
            onConfirm = {
                pendingSelectionOp = null
                viewModel.runSelectionOp(mode, account)
            },
            onDismiss = { pendingSelectionOp = null },
        )
    }

    // Target picker for the account chip's "Move/copy all contacts to…" actions.
    accountOpRequest?.let { (mode, source) ->
        AccountOpTargetSheet(
            mode = mode,
            source = source,
            accounts = state.accounts,
            onPick = { target ->
                accountOpRequest = null
                viewModel.requestAccountOp(mode, source, target)
            },
            onDismiss = { accountOpRequest = null },
        )
    }

    // Confirmation (with field-loss report) before the bulk batch runs.
    pendingAccountOp?.let { pending ->
        AccountOpConfirmDialog(
            pending = pending,
            onConfirm = viewModel::confirmPendingAccountOp,
            onDismiss = viewModel::dismissPendingAccountOp,
        )
    }

    if (showMergePicker) {
        val selectedContacts = state.contacts.filter { it.contactId in state.selectedContactIds }
        MergeTargetSheet(
            selectedCount = state.selectedContactIds.size,
            memberRaws = selectedContacts.flatMap { contact -> contact.rawContacts.map { contact to it } },
            onPick = { contact, raw ->
                showMergePicker = false
                pendingMergeTarget = contact to raw
            },
            onDismiss = { showMergePicker = false },
        )
    }

    pendingMergeTarget?.let { (targetContact, targetRaw) ->
        MergeConfirmDialog(
            targetContact = targetContact,
            targetRaw = targetRaw,
            sources = state.contacts
                .filter { it.contactId in state.selectedContactIds }
                .flatMap { contact -> contact.rawContacts.map { contact to it } }
                .filter { (_, raw) -> raw.rawContactId != targetRaw.rawContactId },
            onConfirm = {
                pendingMergeTarget = null
                viewModel.mergeSelected(targetRaw)
            },
            onDismiss = { pendingMergeTarget = null },
        )
    }

    if (showDeleteConfirm) {
        DeleteSelectedDialog(
            selectedCount = state.selectedContactIds.size,
            onConfirm = {
                showDeleteConfirm = false
                viewModel.deleteSelected()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    pendingDeleteContact?.let { target ->
        DeleteContactDialog(
            contact = target,
            onConfirm = {
                pendingDeleteContact = null
                viewModel.deleteContact(target.contactId)
            },
            onDismiss = { pendingDeleteContact = null },
        )
    }
}
