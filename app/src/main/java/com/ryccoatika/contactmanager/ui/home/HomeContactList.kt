package com.ryccoatika.contactmanager.ui.home

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.ui.common.AlphabetRail
import kotlinx.coroutines.launch

/** The sectioned contact list with swipe-to-delete, drag-select and the fast-scroll rail. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun HomeContactList(
    contacts: List<Contact>,
    rowExtras: Map<Long, ContactRowExtras>,
    selectedContactIds: Set<Long>,
    selectionMode: Boolean,
    listState: LazyListState,
    // The one row whose avatar carries the shared-element modifier (see HomeScreen).
    transitionContactId: Long?,
    currentSelection: () -> Set<Long>,
    onSetSelection: (Set<Long>) -> Unit,
    onToggleSelect: (Long) -> Unit,
    onOpenContact: (Contact) -> Unit,
    onDeleteRequest: (Contact) -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // Only one row may have its delete action revealed: dragging a row
    // claims this id and every other row closes its reveal.
    var swipedContactId by remember { mutableStateOf<Long?>(null) }
    val sections by remember(contacts) { derivedStateOf { sectionsOf(contacts) } }
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
    val railShown = contacts.size > FAST_SCROLL_MIN_CONTACTS
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
                        val before = currentSelection()
                        onSetSelection(
                            if (contactId in before) before - contactId else before + contactId,
                        )
                        // The drag toggles against the pre-press selection.
                        before
                    },
                    setSelection = onSetSelection,
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
                        extras = rowExtras[contact.contactId],
                        selected = contact.contactId in selectedContactIds,
                        swipeEnabled = !selectionMode,
                        closeReveal = listState.isScrollInProgress ||
                            (swipedContactId != null && swipedContactId != contact.contactId),
                        onSwipeStart = { swipedContactId = contact.contactId },
                        sharedElementEnabled = contact.contactId == transitionContactId,
                        onClick = {
                            if (selectionMode) {
                                onToggleSelect(contact.contactId)
                            } else {
                                onOpenContact(contact)
                            }
                        },
                        onSwipeDelete = { onDeleteRequest(contact) },
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
