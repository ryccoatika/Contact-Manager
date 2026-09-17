package com.ryccoatika.contactmanager.ui.home

import android.net.Uri
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
import com.ryccoatika.contactmanager.data.ops.AccountBulkOps
import com.ryccoatika.contactmanager.data.transfer.ContactTransfer
import com.ryccoatika.contactmanager.data.transfer.TransferResult
import com.ryccoatika.contactmanager.di.DefaultDispatcher
import com.ryccoatika.contactmanager.domain.AccountClassifier
import com.ryccoatika.contactmanager.domain.DuplicateFinder
import com.ryccoatika.contactmanager.domain.MovePlan
import com.ryccoatika.contactmanager.domain.MovePlanner
import com.ryccoatika.contactmanager.domain.analytics.Analytics
import com.ryccoatika.contactmanager.domain.analytics.AnalyticsEvent
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.AccountOpMode
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.domain.model.PendingAccountOp
import com.ryccoatika.contactmanager.domain.model.RawContact
import com.ryccoatika.contactmanager.ui.common.UiEvent
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

/** One account of the current selection, with its entry count — feeds the delete sheet. */
data class SelectionAccountEntry(
    val type: String?,
    val name: String?,
    val capability: AccountCapability,
    val entryCount: Int,
) {
    val key: String get() = "$type/$name"
    val deletable: Boolean get() = capability != AccountCapability.READ_ONLY
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
        private val bulkOps: AccountBulkOps,
        duplicatePrefs: DuplicatePrefs,
        private val appPrefs: AppPrefs,
        private val strings: StringProvider,
        private val analytics: Analytics,
        private val transfer: ContactTransfer,
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

        private val _events = MutableSharedFlow<UiEvent>()
        val events: SharedFlow<UiEvent> = _events

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

        /** Bulk move/copy parked for confirmation, with its field-loss report. */
        private val _pendingAccountOp = MutableStateFlow<PendingAccountOp?>(null)
        val pendingAccountOp: StateFlow<PendingAccountOp?> = _pendingAccountOp.asStateFlow()

        /** Hide [account] from the selector; it stays manageable in Settings › Accounts. */
        fun hideAccount(account: ContactAccount) {
            viewModelScope.launch { appPrefs.setAccountHidden(account.key, true) }
        }

        /** Plans moving/copying every contact of [source] into [target]; parked for confirmation. */
        fun requestAccountOp(mode: AccountOpMode, source: ContactAccount, target: ContactAccount) {
            viewModelScope.launch { _pendingAccountOp.value = bulkOps.plan(mode, source, target) }
        }

        fun dismissPendingAccountOp() {
            _pendingAccountOp.value = null
        }

        fun confirmPendingAccountOp() {
            val pending = _pendingAccountOp.value ?: return
            _pendingAccountOp.value = null
            viewModelScope.launch {
                _events.emit(UiEvent.ShowSnackbar(bulkOps.execute(pending.mode, pending.source, pending.target)))
            }
        }

        /** Plan for moving the movable part of the selection into [target]. */
        fun planMove(target: ContactAccount): MovePlan =
            MovePlanner.plan(movableSelectedRawContacts(), target.type, target.name)

        /** Plan for copying the whole selection (read-only included) into [target]. */
        fun planCopy(target: ContactAccount): MovePlan =
            MovePlanner.plan(selectedRawContacts(), target.type, target.name)

        /** Dispatches the selection op picked in the target sheet. */
        fun runSelectionOp(mode: AccountOpMode, target: ContactAccount) = when (mode) {
            AccountOpMode.MOVE -> moveSelectedTo(target)
            AccountOpMode.COPY -> copySelectedTo(target)
        }

        fun moveSelectedTo(target: ContactAccount) {
            val movable = movableSelectedRawContacts()
            clearSelection()
            if (movable.isEmpty()) {
                viewModelScope.launch {
                    _events.emit(UiEvent.ShowSnackbar(strings.get(R.string.home_msg_readonly_move)))
                }
                return
            }
            val targetName = target.name ?: strings.get(R.string.home_msg_this_device)
            val started = batchManager.moveContacts(
                rawContactIds = movable.map { it.rawContactId },
                targetType = target.type,
                targetName = target.name,
                label = strings.get(R.string.home_msg_moving_label, movable.size, targetName),
                finishedMessage = strings.getQuantity(R.plurals.home_moved_contacts, movable.size, movable.size),
            )
            if (started) {
                analytics.logEvent(
                    AnalyticsEvent.ContactMove(movable.size, AccountClassifier.classify(target.type).name),
                )
            } else {
                viewModelScope.launch {
                    _events.emit(UiEvent.ShowSnackbar(strings.get(R.string.home_msg_busy)))
                }
            }
        }

        /** Copies the whole selection into [target]; sources (read-only included) stay put. */
        fun copySelectedTo(target: ContactAccount) {
            val raws = selectedRawContacts()
            clearSelection()
            if (raws.isEmpty()) return
            val targetName = target.name ?: strings.get(R.string.home_msg_this_device)
            val started = batchManager.copyContacts(
                rawContactIds = raws.map { it.rawContactId },
                targetType = target.type,
                targetName = target.name,
                label = strings.get(R.string.home_msg_copying_label, raws.size, targetName),
                finishedMessage = strings.getQuantity(R.plurals.home_copied_contacts, raws.size, raws.size),
            )
            if (started) {
                analytics.logEvent(
                    AnalyticsEvent.ContactCopy(raws.size, AccountClassifier.classify(target.type).name),
                )
            } else {
                viewModelScope.launch {
                    _events.emit(UiEvent.ShowSnackbar(strings.get(R.string.home_msg_busy)))
                }
            }
        }

        /** Exports the whole selection (read-only included) into a single vCard file at [uri]. */
        fun exportSelected(uri: Uri) {
            val raws = selectedRawContacts()
            val accountCount = selectionAccountBreakdown().size
            clearSelection()
            if (raws.isEmpty()) return
            viewModelScope.launch {
                when (val result = transfer.exportRawContacts(raws.map { it.rawContactId }, uri)) {
                    is TransferResult.Success -> {
                        analytics.logEvent(
                            AnalyticsEvent.ContactsExport(
                                count = result.contactCount,
                                accountCount = accountCount,
                                perAccountFiles = false,
                            ),
                        )
                        _events.emit(
                            UiEvent.ShowSnackbar(
                                strings.getQuantity(
                                    R.plurals.home_msg_exported,
                                    result.contactCount,
                                    result.contactCount,
                                ),
                            ),
                        )
                    }

                    is TransferResult.Failure -> {
                        _events.emit(UiEvent.ShowSnackbar(result.message))
                    }
                }
            }
        }

        /** Accounts the selection spans, deletable first then by entry count. */
        fun selectionAccountBreakdown(): List<SelectionAccountEntry> =
            selectedRawContacts()
                .groupBy { it.accountType to it.accountName }
                .map { (account, raws) ->
                    SelectionAccountEntry(
                        type = account.first,
                        name = account.second,
                        capability = AccountClassifier.classify(account.first),
                        entryCount = raws.size,
                    )
                }.sortedWith(compareBy({ !it.deletable }, { -it.entryCount }))

        /** Deletes the selection's raw contacts living in [accountKeys] (read-only ones never). */
        fun deleteSelectedFrom(accountKeys: Set<String>) {
            deleteRawSelection(
                movableSelectedRawContacts()
                    .filter { "${it.accountType}/${it.accountName}" in accountKeys }
                    .map { it.rawContactId },
            )
        }

        fun deleteSelected() = deleteRawSelection(movableSelectedRawContacts().map { it.rawContactId })

        private fun deleteRawSelection(ids: List<Long>) {
            clearSelection()
            viewModelScope.launch {
                if (ids.isEmpty()) {
                    _events.emit(UiEvent.ShowSnackbar(strings.get(R.string.home_msg_readonly_delete)))
                    return@launch
                }
                when (val result = writer.deleteRawContacts(ids)) {
                    is ContactOpResult.Success -> {
                        analytics.logEvent(AnalyticsEvent.ContactDelete(ids.size))
                        _events.emit(UiEvent.ShowSnackbar(strings.getQuantity(R.plurals.home_msg_deleted_entries, ids.size, ids.size)))
                    }

                    is ContactOpResult.Failure -> {
                        _events.emit(UiEvent.ShowSnackbar(result.message))
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
                        _events.emit(UiEvent.ShowSnackbar(strings.getQuantity(R.plurals.home_msg_merged, sources.size, sources.size)))
                        _requestReview.emit(Unit)
                    }

                    is ContactOpResult.Failure -> {
                        _events.emit(UiEvent.ShowSnackbar(result.message))
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
                    _events.emit(UiEvent.ShowSnackbar(strings.get(R.string.home_msg_delete_readonly_named, contact.displayName)))
                    return@launch
                }
                when (val result = writer.deleteRawContacts(ids)) {
                    is ContactOpResult.Success -> {
                        analytics.logEvent(AnalyticsEvent.ContactDelete(ids.size))
                        _events.emit(UiEvent.ShowSnackbar(strings.get(R.string.home_msg_deleted_named, contact.displayName)))
                    }

                    is ContactOpResult.Failure -> {
                        _events.emit(UiEvent.ShowSnackbar(result.message))
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
