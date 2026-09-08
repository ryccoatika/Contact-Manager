package com.ryccoatika.contactmanager.ui.home

import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.ui.common.AccountDot
import com.ryccoatika.contactmanager.ui.common.AccountVisuals
import com.ryccoatika.contactmanager.ui.theme.TabularNums

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

/** Default top bar: title + duplicates badge + settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeTopBar(
    duplicateCount: Int,
    onDuplicatesClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.home_title)) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
        actions = {
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

/** Selection-mode bottom action bar: move / copy / delete / merge. */
@Composable
internal fun SelectionBottomBar(
    mergeEnabled: Boolean,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onMerge: () -> Unit,
) {
    BottomAppBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Spacer(Modifier.width(4.dp))
        TextButton(onClick = onMove) { Text(stringResource(R.string.home_move_to)) }
        Spacer(Modifier.width(4.dp))
        TextButton(onClick = onCopy) { Text(stringResource(R.string.home_copy_to)) }
        Spacer(Modifier.width(4.dp))
        TextButton(onClick = onDelete) { Text(stringResource(R.string.home_delete)) }
        Spacer(Modifier.width(4.dp))
        TextButton(onClick = onMerge, enabled = mergeEnabled) {
            Text(stringResource(R.string.home_merge))
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
