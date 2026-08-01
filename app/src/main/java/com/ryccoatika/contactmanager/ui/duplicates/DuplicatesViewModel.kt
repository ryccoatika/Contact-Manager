package com.ryccoatika.contactmanager.ui.duplicates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.data.ContactOpResult
import com.ryccoatika.contactmanager.data.ContactsSource
import com.ryccoatika.contactmanager.data.ContactsWriter
import com.ryccoatika.contactmanager.data.DuplicatePrefs
import com.ryccoatika.contactmanager.data.StringProvider
import com.ryccoatika.contactmanager.data.analytics.Analytics
import com.ryccoatika.contactmanager.data.analytics.AnalyticsEvent
import com.ryccoatika.contactmanager.di.DefaultDispatcher
import com.ryccoatika.contactmanager.domain.DuplicateFinder
import com.ryccoatika.contactmanager.domain.DuplicateGroup
import com.ryccoatika.contactmanager.domain.model.RawContact
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DuplicatesUiState(
    val groups: List<DuplicateGroup> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class DuplicatesViewModel @Inject constructor(
    contactsSource: ContactsSource,
    private val writer: ContactsWriter,
    private val prefs: DuplicatePrefs,
    private val strings: StringProvider,
    private val analytics: Analytics,
    @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events

    /** Fires after a merge completes — the screen asks Play for an in-app review. */
    private val _requestReview = MutableSharedFlow<Unit>()
    val requestReview: SharedFlow<Unit> = _requestReview

    /** Finder already sorts groups HIGH first; dismissals re-emit through the prefs flow. */
    val uiState: StateFlow<DuplicatesUiState> = combine(
        contactsSource.observeContacts(), prefs.observeDismissedKeys(),
    ) { contacts, dismissed ->
        DuplicatesUiState(groups = DuplicateFinder.find(contacts, dismissed), loading = false)
    }.flowOn(defaultDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DuplicatesUiState())

    /** Reversible aggregation of every raw contact in the group. */
    fun link(group: DuplicateGroup) {
        viewModelScope.launch {
            when (val result = writer.linkContacts(group.rawContactIds())) {
                is ContactOpResult.Success -> {
                    analytics.logEvent(AnalyticsEvent.ContactsLink(group.contacts.size))
                    _events.emit(strings.getQuantity(R.plurals.duplicates_msg_linked, group.contacts.size, group.contacts.size))
                }
                is ContactOpResult.Failure -> _events.emit(result.message)
            }
        }
    }

    /** Physical merge of all other raw contacts of the group into [target]. */
    fun merge(group: DuplicateGroup, target: RawContact) {
        viewModelScope.launch {
            val sources = group.contacts.flatMap { it.rawContacts }
                .filter { it.rawContactId != target.rawContactId }
            when (val result = writer.mergeContacts(target, sources)) {
                is ContactOpResult.Success -> {
                    analytics.logEvent(AnalyticsEvent.ContactsMerge(sources.size))
                    _events.emit(strings.getQuantity(R.plurals.duplicates_msg_merged, sources.size, sources.size))
                    _requestReview.emit(Unit)
                }
                is ContactOpResult.Failure -> _events.emit(result.message)
            }
        }
    }

    /** Persists the dismissal; the provider KEEP_SEPARATE is best-effort only. */
    fun dismiss(group: DuplicateGroup) {
        viewModelScope.launch {
            analytics.logEvent(AnalyticsEvent.DuplicateDismiss)
            prefs.dismiss(DuplicateFinder.groupKey(group))
            writer.keepSeparate(group.rawContactIds()) // failure ignored: prefs already hide the group
        }
    }

    private fun DuplicateGroup.rawContactIds(): List<Long> =
        contacts.flatMap { it.rawContacts }.map { it.rawContactId }
}
