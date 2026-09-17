package com.ryccoatika.contactmanager.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.ui.common.AccountDot
import com.ryccoatika.contactmanager.ui.common.AccountVisuals
import com.ryccoatika.contactmanager.ui.theme.TabularNums
import kotlinx.coroutines.flow.distinctUntilChanged

// ~3x touch slop (Android's default TouchSlop is 8dp): the minimum
// accumulated gesture/fling delta before the chrome flips collapsed/expanded.
// Large enough to absorb small jitters (and the list's own settle motion once
// the field's height animation nudges it) without real intentional scrolls
// feeling unresponsive.
private val COLLAPSE_HYSTERESIS: Dp = 28.dp

/**
 * FAB visibility and search-field expansion, driven by one shared scroll
 * signal fed by [nestedScrollConnection]: both hide once accumulated
 * scroll-down gesture/fling delta clears a hysteresis threshold, return the
 * same way on scroll-up, or snap open at the top of the list.
 * [searchFieldExpanded] is mutable from outside too — the collapsed top
 * bar's search action and a non-empty query/focus force it back open
 * independent of scrolling (see [HomeScreen]'s usage).
 */
@Stable
internal class HomeScrollSignals(
    private val hysteresisPx: Float,
    private val queryEmpty: () -> Boolean,
    private val searchFieldFocused: () -> Boolean,
) {
    var fabVisible by mutableStateOf(true)
    var searchFieldExpanded by mutableStateOf(true)

    // Net gesture/fling delta since the last direction flip or reset. Only
    // real pointer/fling deltas from nestedScrollConnection feed this —
    // never derived from the list's own reported scroll offset, which the
    // chrome's own collapse/expand animation also perturbs (the feedback
    // loop that caused the old offset-direction detector to jitter).
    private var accumulated = 0f

    fun resetAccumulator() {
        accumulated = 0f
    }

    /**
     * Attach to an ancestor of the contact list via `Modifier.nestedScroll`.
     * Consumes nothing — it only observes real drag/fling deltas to detect
     * direction with hysteresis, immune to layout-shift feedback from the
     * chrome's own expand/collapse animation.
     */
    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            onScroll(available.y)
            return Offset.Zero
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            // Nothing extra to do here: onPreScroll already sees the full
            // gesture/fling delta before the list consumes any of it. Kept
            // as an explicit no-op override so both nested-scroll phases are
            // visibly considered, per the diagnosis this replaces.
            return Offset.Zero
        }

        private fun onScroll(deltaY: Float) {
            if (deltaY == 0f) return
            // Negative dy: finger dragging up / fling towards later items —
            // scrolling down the list. Positive dy: scrolling up.
            val sameDirection = (deltaY < 0f && accumulated <= 0f) || (deltaY > 0f && accumulated >= 0f)
            accumulated = if (sameDirection) accumulated + deltaY else deltaY
            when {
                accumulated <= -hysteresisPx -> {
                    fabVisible = false
                    if (queryEmpty() && !searchFieldFocused()) searchFieldExpanded = false
                    accumulated = 0f
                }

                accumulated >= hysteresisPx -> {
                    fabVisible = true
                    if (queryEmpty() && !searchFieldFocused()) searchFieldExpanded = true
                    accumulated = 0f
                }
            }
        }
    }
}

/**
 * Builds [HomeScrollSignals] wired to [listState]'s ancestor via
 * [HomeScrollSignals.nestedScrollConnection] (attach it in [HomeScreen]). The
 * search field only follows scroll while [queryEmpty] and not
 * [searchFieldFocused] — a query or focus always wins and forces it back
 * open.
 */
@Composable
internal fun rememberHomeScrollSignals(
    listState: LazyListState,
    queryEmpty: Boolean,
    searchFieldFocused: Boolean,
): HomeScrollSignals {
    // rememberUpdatedState: the nested-scroll connection and the collect
    // lambda both outlive recompositions, so they must read the latest
    // flags, not the ones captured when they were created.
    val queryEmptyState = rememberUpdatedState(queryEmpty)
    val focusedState = rememberUpdatedState(searchFieldFocused)
    val hysteresisPx = with(LocalDensity.current) { COLLAPSE_HYSTERESIS.toPx() }
    val signals = remember {
        HomeScrollSignals(
            hysteresisPx = hysteresisPx,
            queryEmpty = { queryEmptyState.value },
            searchFieldFocused = { focusedState.value },
        )
    }
    // Cheap, offset-derived and deliberately narrow: only used to snap the
    // chrome back open at rest, not to detect direction (that's the nested
    // scroll connection's job — see the class doc for why offset-derived
    // direction detection was the source of the jitter this replaces).
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
            .distinctUntilChanged()
            .collect { atTop ->
                if (atTop) {
                    signals.fabVisible = true
                    if (queryEmptyState.value && !focusedState.value) signals.searchFieldExpanded = true
                    signals.resetAccumulator()
                }
            }
    }
    // A query or focus can arrive between scroll events (e.g. right after a
    // tap on the collapsed top bar's search action) — force the field open
    // immediately rather than waiting for the next scroll tick.
    LaunchedEffect(queryEmpty, searchFieldFocused) {
        if (!queryEmpty || searchFieldFocused) signals.searchFieldExpanded = true
    }
    return signals
}

/** Selection-mode top bar: count + clear. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectionTopBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(pluralStringResource(R.plurals.home_selected_count, selectedCount, selectedCount))
        },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
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
}

/** Default top bar: title + search (only while the field is collapsed) + duplicates badge + settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeTopBar(
    duplicateCount: Int,
    searchActionVisible: Boolean,
    onSearchClick: () -> Unit,
    onDuplicatesClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.home_title)) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
        actions = {
            // Mirrors the HomeFab scale/fade idiom: no size animation (no
            // expand/shrink), so the neighboring actions never shift.
            AnimatedVisibility(
                visible = searchActionVisible,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                IconButton(onClick = onSearchClick) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(R.string.home_search),
                    )
                }
            }
            IconButton(onClick = onDuplicatesClick) {
                BadgedBox(
                    badge = {
                        if (duplicateCount > 0) {
                            Badge { Text("$duplicateCount") }
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

/** Selection-mode bottom action bar: move / copy / export / delete / merge. */
@Composable
internal fun SelectionBottomBar(
    mergeEnabled: Boolean,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onMerge: () -> Unit,
) {
    BottomAppBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onMove) { Text(stringResource(R.string.home_move_to)) }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onCopy) { Text(stringResource(R.string.home_copy_to)) }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onExport) { Text(stringResource(R.string.home_export)) }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onDelete) { Text(stringResource(R.string.home_delete)) }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onMerge, enabled = mergeEnabled) {
                Text(stringResource(R.string.home_merge))
            }
        }
    }
}

/** New-contact FAB: hides on scroll-down, shifts clear of the fast-scroll rail. */
@Composable
internal fun HomeFab(
    visible: Boolean,
    railShown: Boolean,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
    ) {
        FloatingActionButton(
            onClick = onClick,
            shape = MaterialTheme.shapes.medium,
            modifier = if (railShown) Modifier.padding(end = 28.dp) else Modifier,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.home_new_contact),
            )
        }
    }
}

/** Running batch banner: label, x/y count, progress bar and a cancel button. */
@Composable
internal fun BatchProgressBar(
    label: String,
    done: Int,
    total: Int,
    onCancel: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "$done/$total",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else done.toFloat() / total },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(4.dp))
            // IconButton keeps the 48dp minimum touch target.
            IconButton(onClick = onCancel) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.home_cancel_batch_operation),
                )
            }
        }
    }
}

/** "All" + per-account filter chips, with the long-press hide/move-all menu. */
@Composable
internal fun AccountChipsRow(
    accounts: List<ContactAccount>,
    selectedAccountKey: String?,
    onSelect: (String?) -> Unit,
    onMoveAll: (ContactAccount) -> Unit,
    onCopyAll: (ContactAccount) -> Unit,
    onHide: (ContactAccount) -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    // Account-chip long-press menu anchor (the chip's account key).
    var accountMenuFor by remember { mutableStateOf<String?>(null) }
    val chipsState = rememberLazyListState()
    // Chip index of the active account ("All" is index 0).
    val selectedChipIndex = if (selectedAccountKey == null) {
        0
    } else {
        accounts
            .indexOfFirst { it.key == selectedAccountKey }
            .let { if (it >= 0) it + 1 else 0 }
    }
    // Keep the active account centered (or at least on screen).
    LaunchedEffect(selectedChipIndex, accounts.size) {
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
    val accountCount = accounts.size
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
                selected = selectedAccountKey == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.home_filter_all)) },
            )
        }
        items(accounts, key = { it.key }) { account ->
            Box {
                FilterChip(
                    selected = selectedAccountKey == account.key,
                    onClick = { onSelect(account.key) },
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
                            onMoveAll(account)
                        },
                    )
                    // Copy never touches the source, so it works on read-only accounts too.
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.accounts_copy_all_contacts_to)) },
                        onClick = {
                            accountMenuFor = null
                            onCopyAll(account)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.accounts_hide_from_selector)) },
                        onClick = {
                            accountMenuFor = null
                            onHide(account)
                        },
                    )
                }
            }
        }
    }
}
