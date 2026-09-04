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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.domain.AccountClassifier
import com.ryccoatika.contactmanager.domain.MovePlan
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.domain.model.LabeledValue
import com.ryccoatika.contactmanager.domain.model.RawContact
import com.ryccoatika.contactmanager.ui.analytics.TrackScreenView
import com.ryccoatika.contactmanager.ui.common.AccountDot
import com.ryccoatika.contactmanager.ui.common.AccountVisuals
import com.ryccoatika.contactmanager.ui.common.ContactAvatar
import com.ryccoatika.contactmanager.ui.common.ContactListSkeleton
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
    var showMovePicker by remember { mutableStateOf(false) }
    // Account-chip long-press menu + its "move all contacts" flow.
    var accountMenuFor by remember { mutableStateOf<String?>(null) }
    var moveAllSource by remember { mutableStateOf<ContactAccount?>(null) }
    val pendingMoveAll by viewModel.pendingMoveAll.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pendingDeleteContact by remember { mutableStateOf<Contact?>(null) }
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
                    progress.error ?: context.resources.getQuantityString(
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
                TopAppBar(
                    title = {
                        Text(
                            pluralStringResource(
                                R.plurals.home_selected_count,
                                state.selectedContactIds.size,
                                state.selectedContactIds.size,
                            ),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.home_clear_selection),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            } else if (!embedded) {
                TopAppBar(
                    title = { Text(stringResource(R.string.home_title)) },
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
                                Icon(
                                    Icons.Default.Difference,
                                    contentDescription = stringResource(R.string.home_duplicates),
                                )
                            }
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(R.string.home_settings),
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (state.selectionMode) {
                BottomAppBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showMovePicker = true }) {
                        Text(stringResource(R.string.home_move_to))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text(stringResource(R.string.home_delete))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = { showMergePicker = true },
                        enabled = state.selectedContactIds.size >= 2,
                    ) { Text(stringResource(R.string.home_merge)) }
                }
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
                                    if (progress.total == 0) {
                                        0f
                                    } else {
                                        progress.done.toFloat() / progress.total
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        // IconButton keeps the 48dp minimum touch target.
                        IconButton(onClick = viewModel::cancelBatch) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.home_cancel_batch_operation),
                            )
                        }
                    }
                }
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
            val chipsState = rememberLazyListState()
            // Chip index of the active account ("All" is index 0).
            val selectedChipIndex = if (state.selectedAccountKey == null) {
                0
            } else {
                state.accounts
                    .indexOfFirst { it.key == state.selectedAccountKey }
                    .let { if (it >= 0) it + 1 else 0 }
            }
            // Keep the active account centered (or at least on screen).
            LaunchedEffect(selectedChipIndex, state.accounts.size) {
                val info = chipsState.layoutInfo
                val viewport = info.viewportEndOffset - info.viewportStartOffset
                val itemSize = info.visibleItemsInfo.firstOrNull { it.index == selectedChipIndex }?.size ?: 0
                val centerOffset = -((viewport - itemSize) / 2).coerceAtLeast(0)
                chipsState.animateScrollToItem(selectedChipIndex, centerOffset)
            }
            // Fill the row when only a few accounts fit; stay compact (and let the
            // row scroll) when there are many. Each account chip gets an equal share
            // of the width left after "All", padding and spacing, clamped 120..280dp.
            val screenWidth = LocalConfiguration.current.screenWidthDp.dp
            val accountCount = state.accounts.size
            // A lone account (e.g. just "Phone") shouldn't be stretched to fill the
            // row — that looks like a broken full-width button. Let it wrap its
            // content; only share the width when there are several to line up.
            val singleAccount = accountCount == 1
            val chipWidth = if (accountCount > 0) {
                ((screenWidth - 104.dp - 8.dp * accountCount) / accountCount).coerceIn(120.dp, 280.dp)
            } else {
                120.dp
            }
            LazyRow(
                state = chipsState,
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.selectedAccountKey == null,
                        onClick = { viewModel.selectAccount(null) },
                        label = { Text(stringResource(R.string.home_filter_all)) },
                    )
                }
                items(state.accounts, key = { it.key }) { account ->
                    Box {
                        FilterChip(
                            selected = state.selectedAccountKey == account.key,
                            onClick = { viewModel.selectAccount(account.key) },
                            modifier = (if (singleAccount) Modifier else Modifier.width(chipWidth))
                                .chipLongPress(account.key) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    accountMenuFor = account.key
                                },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Name scrolls if long; count pinned right. When it's the
                                    // only chip, cap the width instead of filling so the pill
                                    // hugs its content; otherwise it takes an equal share.
                                    Text(
                                        AccountVisuals.label(context, account.type, account.name),
                                        maxLines = 1,
                                        // Loop forever with no pause, even while the row scrolls.
                                        modifier = (if (singleAccount) Modifier.widthIn(max = 220.dp) else Modifier.weight(1f))
                                            .basicMarquee(
                                                iterations = Int.MAX_VALUE,
                                                repeatDelayMillis = 0,
                                                initialDelayMillis = 0,
                                            ),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        stringResource(R.string.home_account_chip_count, account.contactCount),
                                        maxLines = 1,
                                        style = TabularNums.merge(MaterialTheme.typography.labelLarge),
                                    )
                                }
                            },
                            leadingIcon = { AccountDot(account.type, account.name, size = 10.dp) },
                        )
                        // Long-press menu: same actions as Settings › Accounts.
                        DropdownMenu(
                            expanded = accountMenuFor == account.key,
                            onDismissRequest = { accountMenuFor = null },
                        ) {
                            val readOnly = account.capability == AccountCapability.READ_ONLY
                            DropdownMenuItem(
                                enabled = !readOnly,
                                text = {
                                    Column {
                                        Text(stringResource(R.string.accounts_move_all_contacts_to))
                                        if (readOnly) {
                                            Text(
                                                stringResource(R.string.accounts_managed_by_app),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    accountMenuFor = null
                                    moveAllSource = account
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.accounts_hide_from_selector)) },
                                onClick = {
                                    accountMenuFor = null
                                    viewModel.hideAccount(account)
                                },
                            )
                        }
                    }
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
                val scope = rememberCoroutineScope()
                // Only one row may have its delete action revealed: dragging a row
                // claims this id and every other row closes its reveal.
                var swipedContactId by remember { mutableStateOf<Long?>(null) }
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
                // Flat contact order for long-press + drag range selection.
                val orderedIds = remember(sections) {
                    sections.flatMap { section -> section.contacts.map { it.contactId } }
                }
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .dragToSelect(
                                listState = listState,
                                orderedIds = orderedIds,
                                onSelectAnchor = { contactId ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val before = viewModel.uiState.value.selectedContactIds
                                    viewModel.setSelection(
                                        if (contactId in before) before - contactId else before + contactId,
                                    )
                                    // The drag toggles against the pre-press selection.
                                    before
                                },
                                setSelection = viewModel::setSelection,
                                scrollBy = { delta -> scope.launch { listState.scrollBy(delta) } },
                            ),
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
                                    extras = state.rowExtras[contact.contactId],
                                    selected = contact.contactId in state.selectedContactIds,
                                    swipeEnabled = !state.selectionMode,
                                    closeReveal = listState.isScrollInProgress ||
                                        (swipedContactId != null && swipedContactId != contact.contactId),
                                    onSwipeStart = { swipedContactId = contact.contactId },
                                    sharedElementEnabled = contact.contactId == transitionContactId,
                                    onClick = {
                                        if (state.selectionMode) {
                                            viewModel.toggleSelect(contact.contactId)
                                        } else {
                                            // Mark before navigating so this row's avatar carries
                                            // the shared-element modifier when the transition
                                            // snapshots the outgoing screen.
                                            transitionContactId = contact.contactId
                                            onContactClick(contact)
                                        }
                                    },
                                    onSwipeDelete = { pendingDeleteContact = contact },
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    modifier = Modifier.animateItem(),
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
        TrackScreenView("move_account_picker")
        ModalBottomSheet(
            onDismissRequest = { showMovePicker = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Text(
                pluralStringResource(
                    R.plurals.home_move_selected_to,
                    state.selectedContactIds.size,
                    state.selectedContactIds.size,
                ),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            state.accounts
                .filter {
                    // Full-CRUD accounts plus SIMs that passed the write probe.
                    it.capability == AccountCapability.FULL_CRUD ||
                        (it.capability == AccountCapability.SIM && it.writable)
                }.forEach { account ->
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
                        headlineContent = { Text(AccountVisuals.label(context, account.type, account.name)) },
                        supportingContent = {
                            Text(account.name ?: stringResource(R.string.home_on_this_device))
                        },
                        leadingContent = { AccountDot(account.type, account.name, size = 12.dp) },
                    )
                }
            Spacer(Modifier.height(24.dp))
        }
    }

    pendingMove?.let { (account, plan) ->
        TrackScreenView("move_fields_lost")
        AlertDialog(
            onDismissRequest = { pendingMove = null },
            title = { Text(stringResource(R.string.home_fields_lost_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.home_fields_lost_text,
                        AccountVisuals.label(context, account.type, account.name),
                        plan.losses
                            .flatMap { it.lostFields }
                            .distinct()
                            .joinToString(),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingMove = null
                    viewModel.moveSelectedTo(account)
                }) { Text(stringResource(R.string.home_move_anyway)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingMove = null }) {
                    Text(stringResource(R.string.home_cancel))
                }
            },
        )
    }

    // Target picker for the account chip's "Move all contacts to…" action.
    moveAllSource?.let { source ->
        TrackScreenView("account_move_target_picker")
        ModalBottomSheet(
            onDismissRequest = { moveAllSource = null },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Text(
                stringResource(
                    R.string.accounts_move_all_source_contacts_to,
                    AccountVisuals.label(context, source.type, source.name),
                ),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            state.accounts
                .filter {
                    it.key != source.key &&
                        (
                            it.capability == AccountCapability.FULL_CRUD ||
                                (it.capability == AccountCapability.SIM && it.writable)
                        )
                }.forEach { target ->
                    ListItem(
                        modifier = Modifier.clickable {
                            moveAllSource = null
                            viewModel.requestMoveAll(source, target)
                        },
                        headlineContent = { Text(AccountVisuals.label(context, target.type, target.name)) },
                        supportingContent = {
                            Text(target.name ?: stringResource(R.string.home_on_this_device))
                        },
                        leadingContent = { AccountDot(target.type, target.name, size = 12.dp) },
                    )
                }
            Spacer(Modifier.height(24.dp))
        }
    }

    // Confirmation (with field-loss report) before the move-all batch runs.
    pendingMoveAll?.let { pending ->
        val lostFields = pending.losses.flatMap { it.lostFields }.distinct()
        TrackScreenView("account_move_confirm")
        AlertDialog(
            onDismissRequest = viewModel::dismissPendingMoveAll,
            title = {
                Text(
                    if (lostFields.isEmpty()) {
                        pluralStringResource(
                            R.plurals.accounts_move_contacts_title,
                            pending.source.contactCount,
                            pending.source.contactCount,
                        )
                    } else {
                        stringResource(R.string.accounts_some_fields_lost)
                    },
                )
            },
            text = {
                val moveBody = stringResource(
                    R.string.accounts_move_dialog_body,
                    AccountVisuals.label(context, pending.source.type, pending.source.name),
                    AccountVisuals.label(context, pending.target.type, pending.target.name),
                )
                val lossesText = if (lostFields.isNotEmpty()) {
                    pluralStringResource(
                        R.plurals.accounts_move_dialog_losses,
                        pending.losses.size,
                        pending.losses.size,
                        lostFields.joinToString(),
                    )
                } else {
                    null
                }
                Text(
                    buildString {
                        append(moveBody)
                        if (lossesText != null) {
                            append("\n\n")
                            append(lossesText)
                        }
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmPendingMoveAll) {
                    Text(
                        stringResource(
                            if (lostFields.isEmpty()) R.string.accounts_move else R.string.accounts_move_anyway,
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPendingMoveAll) {
                    Text(stringResource(R.string.accounts_cancel))
                }
            },
        )
    }

    if (showMergePicker) {
        TrackScreenView("merge_target_picker")
        val selectedContacts = state.contacts.filter { it.contactId in state.selectedContactIds }
        val memberRaws = selectedContacts.flatMap { contact -> contact.rawContacts.map { contact to it } }
        ModalBottomSheet(
            onDismissRequest = { showMergePicker = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Text(
                pluralStringResource(
                    R.plurals.home_merge_selected_into,
                    state.selectedContactIds.size,
                    state.selectedContactIds.size,
                ),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            memberRaws
                // Read-only raw contacts can't receive data, so they can't be targets.
                .filter { (_, raw) ->
                    AccountClassifier.classify(raw.accountType) != AccountCapability.READ_ONLY
                }.forEach { (contact, raw) ->
                    ListItem(
                        modifier = Modifier.clickable {
                            showMergePicker = false
                            pendingMergeTarget = contact to raw
                        },
                        headlineContent = { Text(contact.displayName) },
                        supportingContent = {
                            Text(AccountVisuals.label(context, raw.accountType, raw.accountName))
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
        val mergeSummary = pluralStringResource(
            R.plurals.home_merge_summary,
            sources.size,
            sources.size,
            targetContact.displayName,
            AccountVisuals.label(context, targetRaw.accountType, targetRaw.accountName),
        )
        val memberTemplate = stringResource(R.string.home_merge_member)
        val linkedSuffix = stringResource(R.string.home_merge_linked_suffix)
        AlertDialog(
            onDismissRequest = { pendingMergeTarget = null },
            title = {
                Text(
                    pluralStringResource(
                        R.plurals.home_merge_entries_title,
                        sources.size + 1,
                        sources.size + 1,
                    ),
                )
            },
            text = {
                Text(
                    buildString {
                        append(mergeSummary)
                        if (readOnly.isNotEmpty()) {
                            append("\n\n")
                            append(
                                readOnly.joinToString { (contact, raw) ->
                                    memberTemplate.format(
                                        contact.displayName,
                                        AccountVisuals.label(context, raw.accountType, raw.accountName),
                                    )
                                },
                            )
                            append(linkedSuffix)
                        }
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingMergeTarget = null
                    viewModel.mergeSelected(targetRaw)
                }) { Text(stringResource(R.string.home_merge)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingMergeTarget = null }) {
                    Text(stringResource(R.string.home_cancel))
                }
            },
        )
    }

    if (showDeleteConfirm) {
        TrackScreenView("delete_selected_confirm")
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    pluralStringResource(
                        R.plurals.home_delete_contacts_title,
                        state.selectedContactIds.size,
                        state.selectedContactIds.size,
                    ),
                )
            },
            text = {
                Text(stringResource(R.string.home_delete_disclaimer))
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteSelected()
                }) { Text(stringResource(R.string.home_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.home_cancel))
                }
            },
        )
    }

    pendingDeleteContact?.let { target ->
        TrackScreenView("delete_contact_confirm")
        AlertDialog(
            onDismissRequest = { pendingDeleteContact = null },
            title = { Text(stringResource(R.string.home_delete_contact_title, target.displayName)) },
            text = { Text(stringResource(R.string.home_delete_disclaimer)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteContact = null
                    viewModel.deleteContact(target.contactId)
                }) { Text(stringResource(R.string.home_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteContact = null }) {
                    Text(stringResource(R.string.home_cancel))
                }
            },
        )
    }
}

/** Fast-scroll only earns its screen space on long lists. */
private const val FAST_SCROLL_MIN_CONTACTS = 30

private val RAIL_LETTERS = ('A'..'Z').toList() + '#'

private data class ContactSection(
    val letter: Char,
    val contacts: List<Contact>,
)

/** First-letter sections in list order: A–Z first, non-letter names under "#" at the end. */
private fun sectionsOf(contacts: List<Contact>): List<ContactSection> {
    val grouped = contacts.groupBy { contact ->
        val first = contact.displayName
            .trim()
            .firstOrNull()
            ?.uppercaseChar()
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
                        letterAt(down.position.y)?.let {
                            active = it
                            onLetterSelected(it)
                        }
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
        query.isNotBlank() -> stringResource(R.string.home_empty_search_title, query.trim()) to
            stringResource(R.string.home_empty_search_hint)

        accountFiltered -> stringResource(R.string.home_empty_account_title) to
            stringResource(R.string.home_empty_account_hint)

        else -> stringResource(R.string.home_empty_none_title) to
            stringResource(R.string.home_empty_none_hint)
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun ContactRow(
    contact: Contact,
    extras: ContactRowExtras?,
    selected: Boolean,
    swipeEnabled: Boolean,
    closeReveal: Boolean = false,
    onSwipeStart: () -> Unit = {},
    onClick: () -> Unit,
    onSwipeDelete: () -> Unit,
    sharedElementEnabled: Boolean = false,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // Horizontal drag offset of the row: 0 = settled, -actionWidth = delete
    // button revealed. Driven manually so a partial swipe can *hold* the reveal
    // (SwipeToDismissBox only knows settled/dismissed). A plain state written
    // synchronously from the drag callback — launching per-delta coroutines
    // raced the release animation and left the row ajar.
    var offsetPx by remember { mutableFloatStateOf(0f) }
    val settleBack: () -> Unit = {
        scope.launch { animate(offsetPx, 0f) { value, _ -> offsetPx = value } }
    }
    // List scrolling dismisses an open reveal — a stale delete button shouldn't
    // ride along while the user browses.
    LaunchedEffect(closeReveal) {
        if (closeReveal && offsetPx < 0f) {
            animate(offsetPx, 0f) { value, _ -> offsetPx = value }
        }
    }
    val rowClick: () -> Unit = {
        // A tap while the delete button is revealed closes it instead of opening.
        if (offsetPx < -1f) settleBack() else onClick()
    }
    val row = @Composable {
        ListItem(
            // Long-press is handled by the list-level dragToSelect detector (a
            // long-press here would consume the events and kill drag-select).
            modifier = Modifier
                .heightIn(min = 64.dp)
                .clickable(onClick = rowClick),
            // Opaque so it covers the red delete background until swiped.
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            headlineContent = {
                Text(contact.displayName, style = MaterialTheme.typography.titleMedium)
            },
            supportingContent = {
                extras?.subtitle?.let {
                    Text(
                        it,
                        style = TabularNums.merge(MaterialTheme.typography.bodyMedium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            },
            leadingContent = {
                if (selected) {
                    SelectedAvatar()
                } else {
                    // Only the last-tapped row carries the shared element (set before
                    // navigating), so scrolling pays zero match bookkeeping per row
                    // while the open/back morphs still find their source.
                    val avatarModifier =
                        if (sharedElementEnabled &&
                            sharedTransitionScope != null && animatedVisibilityScope != null
                        ) {
                            with(sharedTransitionScope) {
                                Modifier.sharedElement(
                                    rememberSharedContentState(key = "contact-avatar-${contact.contactId}"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                )
                            }
                        } else {
                            Modifier
                        }
                    ContactAvatar(contact.displayName, contact.photoThumbnailUri, modifier = avatarModifier)
                }
            },
            trailingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    extras?.accountDots?.forEach { (type, name) -> AccountDot(type, name) }
                }
            },
        )
    }

    if (!swipeEnabled) {
        Box(modifier) { row() }
        return
    }

    var rowWidthPx by remember { mutableFloatStateOf(0f) }
    val actionWidthPx = with(LocalDensity.current) { DELETE_ACTION_WIDTH.toPx() }
    // Crossing this while dragging buzzes; releasing past it confirms right away.
    val deleteThresholdPx = rowWidthPx * DELETE_SWIPE_THRESHOLD
    var pastThreshold by remember { mutableStateOf(false) }

    // Pinned at the trailing edge normally; once the drag crosses the threshold
    // the trash button springs over to hug the row's trailing edge and follows
    // the finger — signalling "release to delete".
    val buttonLeftTarget = if (pastThreshold) {
        rowWidthPx + offsetPx
    } else {
        rowWidthPx - actionWidthPx
    }
    val buttonLeft by animateFloatAsState(buttonLeftTarget, label = "deleteButton")

    Box(modifier.onSizeChanged { rowWidthPx = it.width.toFloat() }) {
        // Revealed layer: the tappable delete action under the row.
        Box(
            Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.error),
        ) {
            IconButton(
                onClick = {
                    settleBack()
                    onSwipeDelete()
                },
                modifier = Modifier
                    .offset { IntOffset(buttonLeft.roundToInt(), 0) }
                    .width(DELETE_ACTION_WIDTH)
                    .fillMaxHeight(),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.home_delete),
                    tint = MaterialTheme.colorScheme.onError,
                )
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offsetPx.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    onDragStarted = { onSwipeStart() },
                    state = rememberDraggableState { delta ->
                        val target = (offsetPx + delta).coerceIn(-rowWidthPx, 0f)
                        val crossed = deleteThresholdPx > 0f && target <= -deleteThresholdPx
                        if (crossed && !pastThreshold) {
                            // Reject = the strongest buzz Compose exposes — this is the
                            // "you're about to delete" moment, make it unmissable.
                            haptic.performHapticFeedback(HapticFeedbackType.Reject)
                        }
                        pastThreshold = crossed
                        offsetPx = target
                    },
                    onDragStopped = {
                        val settleTo = when {
                            // Released past the threshold → ask to confirm right away.
                            pastThreshold -> {
                                onSwipeDelete()
                                0f
                            }

                            // Button fully uncovered but under the threshold → hold it revealed.
                            offsetPx <= -actionWidthPx -> {
                                -actionWidthPx
                            }

                            // Still (partly) covering the button → snap closed.
                            else -> {
                                0f
                            }
                        }
                        pastThreshold = false
                        animate(offsetPx, settleTo) { value, _ -> offsetPx = value }
                    },
                ),
        ) {
            row()
        }
    }
}

/**
 * Long-press + drag range selection (Google-Photos style). The row's own
 * long-press enters selection mode and selects the anchor; dragging without
 * lifting then extends the selection from the anchor to whatever row the finger
 * is over — moving back shrinks it — while pre-existing selections are kept.
 * Near the viewport's top/bottom edge the list auto-scrolls so the drag can
 * keep selecting beyond the screen.
 */
private fun Modifier.dragToSelect(
    listState: LazyListState,
    orderedIds: List<Long>,
    // Called at long-press with the anchor contact: toggles it (haptic included)
    // and returns the selection as it was BEFORE the press — the drag range then
    // toggles every row it covers against that baseline.
    onSelectAnchor: (Long) -> Set<Long>,
    setSelection: (Set<Long>) -> Unit,
    scrollBy: (Float) -> Unit,
): Modifier = pointerInput(orderedIds) {
    var anchorId: Long? = null
    var base: Set<Long> = emptySet()
    detectDragGesturesAfterLongPress(
        onDragStart = { position ->
            anchorId = listState.contactIdAt(position.y)
            base = anchorId?.let(onSelectAnchor) ?: emptySet()
        },
        onDragEnd = { anchorId = null },
        onDragCancel = { anchorId = null },
        onDrag = { change, _ ->
            val anchor = anchorId ?: return@detectDragGesturesAfterLongPress
            val y = change.position.y
            // Auto-scroll when dragging near the edges.
            val edge = DRAG_SELECT_EDGE_PX
            when {
                y < edge -> scrollBy(y - edge)
                y > size.height - edge -> scrollBy(y - (size.height - edge))
            }
            val overId = listState.contactIdAt(y) ?: return@detectDragGesturesAfterLongPress
            val anchorIndex = orderedIds.indexOf(anchor)
            val overIndex = orderedIds.indexOf(overId)
            if (anchorIndex == -1 || overIndex == -1) return@detectDragGesturesAfterLongPress
            val range = if (anchorIndex <= overIndex) {
                orderedIds.subList(anchorIndex, overIndex + 1)
            } else {
                orderedIds.subList(overIndex, anchorIndex + 1)
            }.toSet()
            // Toggle the covered rows against the pre-press baseline: rows that were
            // selected turn off, unselected ones turn on; dragging back reverts.
            setSelection((base - range) + (range - base))
        },
    )
}

/**
 * Long-press detector that coexists with a component's own click handling (e.g.
 * FilterChip): it watches the Initial pass, and only when the press outlasts the
 * long-press timeout does it fire and swallow the rest of the gesture so the
 * component's tap doesn't also land.
 */
private fun Modifier.chipLongPress(key: Any?, onLongPress: () -> Unit): Modifier =
    pointerInput(key) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // null = timed out (long press); true = lifted (tap); false = cancelled
            // (e.g. the chips row started scrolling).
            val upBeforeTimeout = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                waitForUpOrCancellation(PointerEventPass.Initial) != null
            }
            if (upBeforeTimeout == null) {
                onLongPress()
                var event: PointerEvent
                do {
                    event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        }
    }

/** The contact row (Long key) under viewport-relative [y]; headers yield null. */
private fun LazyListState.contactIdAt(y: Float): Long? =
    layoutInfo.visibleItemsInfo
        .firstOrNull { y.toInt() in it.offset..(it.offset + it.size) }
        ?.key as? Long

/** Distance from the viewport edge within which drag-select auto-scrolls. */
private const val DRAG_SELECT_EDGE_PX = 140f

/** Width of the revealed swipe-to-delete action button. */
private val DELETE_ACTION_WIDTH = 88.dp

/** Fraction of the row width past which releasing confirms deletion directly. */
private const val DELETE_SWIPE_THRESHOLD = 0.4f

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
            ContactRow(
                previewContact(),
                extras = ContactRowExtras(
                    subtitle = "+44 7700 900312",
                    accountDots = listOf("com.google" to "amelia@gmail.com"),
                ),
                selected = false,
                swipeEnabled = true,
                onClick = {},
                onSwipeDelete = {},
            )
        }
    }
}
