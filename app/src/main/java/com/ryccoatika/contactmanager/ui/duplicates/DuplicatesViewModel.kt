package com.ryccoatika.contactmanager.ui.duplicates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.data.ContactOpResult
import com.ryccoatika.contactmanager.data.ContactsSource
import com.ryccoatika.contactmanager.data.ContactsWriter
import com.ryccoatika.contactmanager.data.DuplicatePrefs
import com.ryccoatika.contactmanager.data.StringProvider
import com.ryccoatika.contactmanager.di.DefaultDispatcher
import com.ryccoatika.contactmanager.domain.DuplicateAction
import com.ryccoatika.contactmanager.domain.DuplicateFinder
import com.ryccoatika.contactmanager.domain.DuplicateGroup
import com.ryccoatika.contactmanager.domain.analytics.Analytics
import com.ryccoatika.contactmanager.domain.analytics.AnalyticsEvent
import com.ryccoatika.contactmanager.domain.model.RawContact
import com.ryccoatika.contactmanager.ui.common.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A Link / Not duplicate tap waiting for the user to confirm the first-use explanation. */
data class PendingExplain(
    val action: DuplicateAction,
    val group: DuplicateGroup,
)

data class DuplicatesUiState(
    val groups: List<DuplicateGroup> = emptyList(),
    val loading: Boolean = true,
    val pendingExplain: PendingExplain? = null,
)

@HiltViewModel
class DuplicatesViewModel
    @Inject
    constructor(
        contactsSource: ContactsSource,
        private val writer: ContactsWriter,
        private val prefs: DuplicatePrefs,
        private val strings: StringProvider,
        private val analytics: Analytics,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _events = MutableSharedFlow<UiEvent>()
        val events: SharedFlow<UiEvent> = _events

        /** Fires after a merge completes — the screen asks Play for an in-app review. */
        private val _requestReview = MutableSharedFlow<Unit>()
        val requestReview: SharedFlow<Unit> = _requestReview

        /** Finder already sorts groups HIGH first; dismissals re-emit through the prefs flow. */
        private val pendingExplain = MutableStateFlow<PendingExplain?>(null)

        /** True from a tap until its first-use lookup returns; further taps are ignored. */
        private var lookingUp = false

        val uiState: StateFlow<DuplicatesUiState> = combine(
            contactsSource.observeContacts(),
            prefs.observeDismissedKeys(),
            pendingExplain,
        ) { contacts, dismissed, pending ->
            DuplicatesUiState(
                groups = DuplicateFinder.find(contacts, dismissed),
                loading = false,
                pendingExplain = pending,
            )
        }.flowOn(defaultDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DuplicatesUiState())

        /** Reversible aggregation of every raw contact in the group; explained on first use. */
        fun link(group: DuplicateGroup) = requestAction(DuplicateAction.LINK, group)

        /** Hides the group and keeps its contacts apart; explained on first use. */
        fun dismiss(group: DuplicateGroup) = requestAction(DuplicateAction.DISMISS, group)

        /** The user confirmed the first-use explanation: remember it and run the action. */
        fun confirmExplained() {
            val pending = pendingExplain.value ?: return
            pendingExplain.value = null
            viewModelScope.launch {
                prefs.markExplained(pending.action)
                perform(pending.action, pending.group)
            }
        }

        /** The user cancelled the explanation; it is not remembered. */
        fun cancelExplain() {
            pendingExplain.value = null
        }

        private fun requestAction(action: DuplicateAction, group: DuplicateGroup) {
            if (pendingExplain.value != null || lookingUp) return
            lookingUp = true
            viewModelScope.launch {
                val explained = try {
                    prefs.isExplained(action)
                } finally {
                    lookingUp = false
                }
                if (explained) perform(action, group) else pendingExplain.value = PendingExplain(action, group)
            }
        }

        private suspend fun perform(action: DuplicateAction, group: DuplicateGroup) {
            when (action) {
                DuplicateAction.LINK -> performLink(group)
                DuplicateAction.DISMISS -> performDismiss(group)
            }
        }

        private suspend fun performLink(group: DuplicateGroup) {
            when (val result = writer.linkContacts(group.rawContactIds())) {
                is ContactOpResult.Success -> {
                    analytics.logEvent(AnalyticsEvent.ContactsLink(group.contacts.size))
                    _events.emit(
                        UiEvent.ShowSnackbar(
                            message = strings.getQuantity(R.plurals.duplicates_msg_linked, group.contacts.size, group.contacts.size),
                            actionLabel = strings.get(R.string.duplicates_undo),
                            onAction = { undoLink(group) },
                        ),
                    )
                }

                is ContactOpResult.Failure -> {
                    _events.emit(UiEvent.ShowSnackbar(result.message))
                }
            }
        }

        /** Persists the dismissal; the provider KEEP_SEPARATE is best-effort only. */
        private suspend fun performDismiss(group: DuplicateGroup) {
            analytics.logEvent(AnalyticsEvent.DuplicateDismiss)
            prefs.dismiss(DuplicateFinder.groupKey(group))
            writer.keepSeparate(group.groupedRawContactIds()) // failure ignored: prefs already hide the group
            _events.emit(
                UiEvent.ShowSnackbar(
                    message = strings.get(R.string.duplicates_msg_dismissed),
                    actionLabel = strings.get(R.string.duplicates_undo),
                    onAction = { undoDismiss(group) },
                ),
            )
        }

        /** Best-effort: contacts may have changed since; a failed reset just leaves the data as is. */
        private fun undoLink(group: DuplicateGroup) {
            viewModelScope.launch {
                writer.resetAggregation(group.groupedRawContactIds())
                analytics.logEvent(AnalyticsEvent.DuplicateUndo("link", group.contacts.size))
            }
        }

        private fun undoDismiss(group: DuplicateGroup) {
            viewModelScope.launch {
                prefs.restore(DuplicateFinder.groupKey(group))
                writer.resetAggregation(group.groupedRawContactIds())
                analytics.logEvent(AnalyticsEvent.DuplicateUndo("dismiss", group.contacts.size))
            }
        }

        /** Physical merge of all other raw contacts of the group into [target]. */
        fun merge(group: DuplicateGroup, target: RawContact) {
            viewModelScope.launch {
                val sources = group.contacts
                    .flatMap { it.rawContacts }
                    .filter { it.rawContactId != target.rawContactId }
                when (val result = writer.mergeContacts(target, sources)) {
                    is ContactOpResult.Success -> {
                        analytics.logEvent(AnalyticsEvent.ContactsMerge(sources.size))
                        _events.emit(UiEvent.ShowSnackbar(strings.getQuantity(R.plurals.duplicates_msg_merged, sources.size, sources.size)))
                        _requestReview.emit(Unit)
                    }

                    is ContactOpResult.Failure -> {
                        _events.emit(UiEvent.ShowSnackbar(result.message))
                    }
                }
            }
        }

        private fun DuplicateGroup.groupedRawContactIds(): List<List<Long>> =
            contacts.map { c -> c.rawContacts.map { it.rawContactId } }

        private fun DuplicateGroup.rawContactIds(): List<Long> =
            contacts.flatMap { it.rawContacts }.map { it.rawContactId }
    }
