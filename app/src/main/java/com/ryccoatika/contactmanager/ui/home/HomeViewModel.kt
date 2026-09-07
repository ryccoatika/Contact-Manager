package com.ryccoatika.contactmanager.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.data.AccountsSource
import com.ryccoatika.contactmanager.data.AppPrefs
import com.ryccoatika.contactmanager.data.BatchProgress
import com.ryccoatika.contactmanager.data.BatchRunner
import com.ryccoatika.contactmanager.data.ContactOpResult
import com.ryccoatika.contactmanager.data.ContactsSource
import com.ryccoatika.contactmanager.data.ContactsWriter
import com.ryccoatika.contactmanager.data.DuplicatePrefs
import com.ryccoatika.contactmanager.data.StringProvider
import com.ryccoatika.contactmanager.data.analytics.Analytics
import com.ryccoatika.contactmanager.data.analytics.AnalyticsEvent
import com.ryccoatika.contactmanager.data.ops.MoveAllContacts
import com.ryccoatika.contactmanager.di.DefaultDispatcher
import com.ryccoatika.contactmanager.domain.AccountClassifier
import com.ryccoatika.contactmanager.domain.DuplicateFinder
import com.ryccoatika.contactmanager.domain.MovePlan
import com.ryccoatika.contactmanager.domain.MovePlanner
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.domain.model.PendingMoveAll
import com.ryccoatika.contactmanager.domain.model.RawContact
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Row bits derived from a [Contact] once per data load instead of per-row in
 *  composition: the subtitle line and the distinct (type, name) provenance dots. */
data class ContactRowExtras(
    val subtitle: String?,
    val accountDots: List<Pair<String?, String?>>,
)

data class HomeUiState(
    val contacts: List<Contact> = emptyList(),
    val rowExtras: Map<Long, ContactRowExtras> = emptyMap(),
    val accounts: List<ContactAccount> = emptyList(),
    val selectedAccountKey: String? = null,
    val query: String = "",
    val loading: Boolean = true,
    val selectedContactIds: Set<Long> = emptySet(),
    val duplicateCount: Int = 0,
) {
    val selectionMode: Boolean get() = selectedContactIds.isNotEmpty()
}

/** First non-blank of: organization, a phone, an email — the hint under the name. */
private fun subtitleOf(contact: Contact): String? =
    contact.rawContacts.firstNotNullOfOrNull { it.organization?.takeIf(String::isNotBlank) }
        ?: contact.rawContacts
            .flatMap { it.phones }
            .firstOrNull()
            ?.value
        ?: contact.rawContacts
            .flatMap { it.emails }
            .firstOrNull()
            ?.value

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val contactsSource: ContactsSource,
        private val accountsSource: AccountsSource,
        private val writer: ContactsWriter,
        private val batchManager: BatchRunner,
        private val moveAll: MoveAllContacts,
        duplicatePrefs: DuplicatePrefs,
        private val appPrefs: AppPrefs,
        private val strings: StringProvider,
        private val analytics: Analytics,
        @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val query = MutableStateFlow("")
        private val selectedAccountKey = MutableStateFlow<String?>(null)
        private val selectedContactIds = MutableStateFlow<Set<Long>>(emptySet())

        /** Accounts the user hid from the selector, paired with the live account list
         *  (re-queried on every provider change so the per-account counts stay current). */
        private val accountsAndHidden = combine(
            accountsSource.observeAccounts(),
            appPrefs.observeHiddenAccountKeys(),
        ) { accounts, hidden -> accounts to hidden }

        private val _events = MutableSharedFlow<String>()
        val events: SharedFlow<String> = _events

        /** Fires after a merge completes — the screen asks Play for an in-app review. */
        private val _requestReview = MutableSharedFlow<Unit>()
        val requestReview: SharedFlow<Unit> = _requestReview

        val batchProgress: StateFlow<BatchProgress?> = batchManager.progress

        /** Contacts paired with their duplicate-group count (dismissed groups excluded). */
        private val contactsWithDuplicateCount = combine(
            contactsSource.observeContacts(),
            duplicatePrefs.observeDismissedKeys(),
        ) { contacts, dismissed ->
            contacts to DuplicateFinder.find(contacts, dismissed).size
        }

        val uiState: StateFlow<HomeUiState> = combine(
            contactsWithDuplicateCount,
            accountsAndHidden,
            query,
            selectedAccountKey,
            selectedContactIds,
        ) { (contacts, duplicateCount), (allAccounts, hidden), query, accountKey, selected ->
            // A hidden account can't stay selected: its chip is gone, so fall back to All.
            val effectiveKey = accountKey?.takeIf { it !in hidden }
            val filtered = contacts.filtered(query, effectiveKey, hidden)
            HomeUiState(
                contacts = filtered,
                rowExtras = filtered.associate { contact ->
                    contact.contactId to ContactRowExtras(
                        subtitle = subtitleOf(contact),
                        accountDots = contact.rawContacts
                            .map { it.accountType to it.accountName }
                            .distinct(),
                    )
                },
                accounts = allAccounts.filter { it.key !in hidden },
                selectedAccountKey = effectiveKey,
                query = query,
                loading = false,
                selectedContactIds = selected,
                duplicateCount = duplicateCount,
            )
        }.flowOn(defaultDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

        fun setQuery(q: String) {
            query.value = q
            if (q.isNotBlank()) analytics.logEvent(AnalyticsEvent.Search(q.trim().length))
        }

        fun selectAccount(key: String?) {
            selectedAccountKey.value = key
        }

        fun toggleSelect(contactId: Long) {
            selectedContactIds.update { if (contactId in it) it - contactId else it + contactId }
        }

        fun clearSelection() {
            selectedContactIds.value = emptySet()
        }

        /** Replace the whole selection — used by long-press + drag range select. */
        fun setSelection(ids: Set<Long>) {
            selectedContactIds.value = ids
        }

        // --- Account chip long-press actions (mirrors the Accounts screen) --------

        /** Move-all parked for confirmation, with its field-loss report. */
        private val _pendingMoveAll = MutableStateFlow<PendingMoveAll?>(null)
        val pendingMoveAll: StateFlow<PendingMoveAll?> = _pendingMoveAll.asStateFlow()

        /** Hide [account] from the selector; it stays manageable in Settings › Accounts. */
        fun hideAccount(account: ContactAccount) {
            viewModelScope.launch { appPrefs.setAccountHidden(account.key, true) }
        }

        /** Plans moving every contact of [source] into [target]; parked for confirmation. */
        fun requestMoveAll(source: ContactAccount, target: ContactAccount) {
            viewModelScope.launch { _pendingMoveAll.value = moveAll.plan(source, target) }
        }

        fun dismissPendingMoveAll() {
            _pendingMoveAll.value = null
        }

        fun confirmPendingMoveAll() {
            val pending = _pendingMoveAll.value ?: return
            _pendingMoveAll.value = null
            viewModelScope.launch { _events.emit(moveAll.execute(pending.source, pending.target)) }
        }

        /** Plan for moving the movable part of the selection into [target]. */
        fun planMove(target: ContactAccount): MovePlan =
            MovePlanner.plan(movableSelectedRawContacts(), target.type, target.name)

        fun moveSelectedTo(target: ContactAccount) {
            val movable = movableSelectedRawContacts()
            clearSelection()
            if (movable.isEmpty()) {
                viewModelScope.launch {
                    _events.emit(strings.get(R.string.home_msg_readonly_move))
                }
                return
            }
            val targetName = target.name ?: strings.get(R.string.home_msg_this_device)
            val started = batchManager.moveContacts(
                rawContactIds = movable.map { it.rawContactId },
                targetType = target.type,
                targetName = target.name,
                label = strings.get(R.string.home_msg_moving_label, movable.size, targetName),
            )
            if (started) {
                analytics.logEvent(
                    AnalyticsEvent.ContactMove(movable.size, AccountClassifier.classify(target.type).name),
                )
            } else {
                viewModelScope.launch {
                    _events.emit(strings.get(R.string.home_msg_busy))
                }
            }
        }

        fun deleteSelected() {
            val ids = movableSelectedRawContacts().map { it.rawContactId }
            clearSelection()
            viewModelScope.launch {
                if (ids.isEmpty()) {
                    _events.emit(strings.get(R.string.home_msg_readonly_delete))
                    return@launch
                }
                when (val result = writer.deleteRawContacts(ids)) {
                    is ContactOpResult.Success -> {
                        analytics.logEvent(AnalyticsEvent.ContactDelete(ids.size))
                        _events.emit(strings.getQuantity(R.plurals.home_msg_deleted_entries, ids.size, ids.size))
                    }

                    is ContactOpResult.Failure -> {
                        _events.emit(result.message)
                    }
                }
            }
        }

        /**
         * Merges every raw contact of the selection into [target]; read-only members
         * are copied + linked by the writer instead of deleted.
         */
        fun mergeSelected(target: RawContact) {
            val sources = selectedRawContacts().filter { it.rawContactId != target.rawContactId }
            clearSelection()
            viewModelScope.launch {
                when (val result = writer.mergeContacts(target, sources)) {
                    is ContactOpResult.Success -> {
                        analytics.logEvent(AnalyticsEvent.ContactsMerge(sources.size))
                        _events.emit(strings.getQuantity(R.plurals.home_msg_merged, sources.size, sources.size))
                        _requestReview.emit(Unit)
                    }

                    is ContactOpResult.Failure -> {
                        _events.emit(result.message)
                    }
                }
            }
        }

        /** Deletes one contact (its writable raw contacts) — used by swipe-to-delete. */
        fun deleteContact(contactId: Long) {
            val contact = uiState.value.contacts.firstOrNull { it.contactId == contactId } ?: return
            val ids = contact.rawContacts
                .filter { AccountClassifier.classify(it.accountType) != AccountCapability.READ_ONLY }
                .map { it.rawContactId }
            viewModelScope.launch {
                if (ids.isEmpty()) {
                    _events.emit(strings.get(R.string.home_msg_delete_readonly_named, contact.displayName))
                    return@launch
                }
                when (val result = writer.deleteRawContacts(ids)) {
                    is ContactOpResult.Success -> {
                        analytics.logEvent(AnalyticsEvent.ContactDelete(ids.size))
                        _events.emit(strings.get(R.string.home_msg_deleted_named, contact.displayName))
                    }

                    is ContactOpResult.Failure -> {
                        _events.emit(result.message)
                    }
                }
            }
        }

        fun cancelBatch() = batchManager.cancel()

        fun onBatchFinishedShown() = batchManager.clearFinished()

        /** Every raw contact of the current selection, read-only included. */
        private fun selectedRawContacts(): List<RawContact> =
            uiState.value.contacts
                .filter { it.contactId in selectedContactIds.value }
                .flatMap { it.rawContacts }

        /** Raw contacts of the selection minus read-only ones (app-managed, undeletable). */
        private fun movableSelectedRawContacts(): List<RawContact> =
            selectedRawContacts()
                .filter { AccountClassifier.classify(it.accountType) != AccountCapability.READ_ONLY }

        private fun List<Contact>.filtered(
            query: String,
            accountKey: String?,
            hidden: Set<String>,
        ): List<Contact> {
            var result = this
            if (accountKey != null) {
                result = result.filter { contact ->
                    contact.rawContacts.any { "${it.accountType}/${it.accountName}" == accountKey }
                }
            } else if (hidden.isNotEmpty()) {
                // "All" view: drop contacts that live only in hidden accounts.
                result = result.filter { contact ->
                    contact.rawContacts.any { "${it.accountType}/${it.accountName}" !in hidden }
                }
            }
            if (query.isNotBlank()) {
                val q = query.trim()
                val qDigits = q.filter { it.isDigit() }
                result = result.filter { contact ->
                    contact.displayName.contains(q, ignoreCase = true) ||
                        (
                            qDigits.isNotEmpty() && contact.rawContacts.any { raw ->
                                raw.phones.any { it.value.filter(Char::isDigit).contains(qDigits) }
                            }
                        ) ||
                        contact.rawContacts.any { raw -> raw.emails.any { it.value.contains(q, true) } }
                }
            }
            return result
        }
    }
