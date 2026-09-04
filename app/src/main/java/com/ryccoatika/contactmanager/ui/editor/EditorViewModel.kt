package com.ryccoatika.contactmanager.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.data.AccountsSource
import com.ryccoatika.contactmanager.data.AppPrefs
import com.ryccoatika.contactmanager.data.ContactOpResult
import com.ryccoatika.contactmanager.data.ContactsSource
import com.ryccoatika.contactmanager.data.ContactsWriter
import com.ryccoatika.contactmanager.data.EditableContact
import com.ryccoatika.contactmanager.data.StringProvider
import com.ryccoatika.contactmanager.data.analytics.Analytics
import com.ryccoatika.contactmanager.data.analytics.AnalyticsEvent
import com.ryccoatika.contactmanager.data.sim.SimRepository
import com.ryccoatika.contactmanager.data.sim.SimRouting
import com.ryccoatika.contactmanager.data.toMessage
import com.ryccoatika.contactmanager.domain.AccountClassifier
import com.ryccoatika.contactmanager.domain.SimContactValidator
import com.ryccoatika.contactmanager.domain.SimValidation
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.domain.model.SimCapabilities
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface EditorEvent {
    data object Saved : EditorEvent

    data class ShowMessage(
        val message: String,
    ) : EditorEvent
}

/** Form fields captured after load; the dirty check compares against it. */
data class EditorFormSnapshot(
    val name: String = "",
    val phones: List<String> = listOf(""),
    val emails: List<String> = listOf(""),
    val organization: String = "",
    val jobTitle: String = "",
    val nickname: String = "",
    val websites: List<String> = listOf(""),
    val addresses: List<String> = listOf(""),
    val birthday: String = "",
    val anniversary: String = "",
    val note: String = "",
)

data class EditorUiState(
    val isEdit: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val name: String = "",
    val phones: List<String> = listOf(""),
    val emails: List<String> = listOf(""),
    val organization: String = "",
    val jobTitle: String = "",
    val nickname: String = "",
    val websites: List<String> = listOf(""),
    val addresses: List<String> = listOf(""),
    val birthday: String = "",
    val anniversary: String = "",
    val note: String = "",
    val accounts: List<ContactAccount> = emptyList(),
    val selectedAccount: ContactAccount? = null,
    val fixedAccountLabel: String? = null,
    /** SIM form: name + single phone only, name capped at [simMaxNameLength]. */
    val simMode: Boolean = false,
    val simMaxNameLength: Int = 14,
    val simError: String? = null,
    /** null until the form is loaded, so a half-loaded form is never "dirty". */
    val loadedSnapshot: EditorFormSnapshot? = null,
    /** Defaults true so the one-time phone-permission prompt never flashes before load. */
    val phonePermissionAsked: Boolean = true,
) {
    val simNameTooLong: Boolean get() = simMode && name.length > simMaxNameLength

    /** True when the user has unsaved edits worth a discard confirmation. */
    val dirty: Boolean
        get() = loadedSnapshot != null &&
            loadedSnapshot != EditorFormSnapshot(
                name,
                phones,
                emails,
                organization,
                jobTitle,
                nickname,
                websites,
                addresses,
                birthday,
                anniversary,
                note,
            )

    val canSave: Boolean
        get() = !saving && !loading && !simNameTooLong && (
            name.isNotBlank() ||
                phones.any { it.isNotBlank() } ||
                emails.any { it.isNotBlank() }
        )
}

@HiltViewModel
class EditorViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val contactsSource: ContactsSource,
        private val accountsSource: AccountsSource,
        private val writer: ContactsWriter,
        private val simRepository: SimRepository,
        private val appPrefs: AppPrefs,
        private val strings: StringProvider,
        private val analytics: Analytics,
    ) : ViewModel() {
        private val rawContactId: Long? = savedStateHandle["rawContactId"]

        private val _uiState = MutableStateFlow(EditorUiState(isEdit = rawContactId != null))
        val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

        private val _events = MutableSharedFlow<EditorEvent>()
        val events: SharedFlow<EditorEvent> = _events

        /** Probe-detected limits per SIM account key, prefetched so selection is synchronous. */
        private var simCapsByKey: Map<String, SimCapabilities> = emptyMap()

        init {
            if (rawContactId == null) loadForCreate() else loadForEdit(rawContactId)
        }

        private fun loadForCreate() {
            viewModelScope.launch {
                val writable = accountsSource.getAccounts().filter {
                    it.capability == AccountCapability.FULL_CRUD ||
                        (it.capability == AccountCapability.SIM && it.writable)
                }
                simCapsByKey = writable
                    .filter { it.capability == AccountCapability.SIM }
                    .associate { it.key to simCapsOf(it.type) }
                val phonePermissionAsked = appPrefs.phonePermissionAsked()
                _uiState.update {
                    it.copy(
                        loading = false,
                        accounts = writable,
                        loadedSnapshot = EditorFormSnapshot(),
                        phonePermissionAsked = phonePermissionAsked,
                    )
                }
                writable.firstOrNull()?.let(::selectAccount)
            }
        }

        private fun loadForEdit(rawContactId: Long) {
            viewModelScope.launch {
                val contact = contactsSource
                    .observeContacts()
                    .first { contacts -> contacts.any { it.hasRawContact(rawContactId) } }
                    .first { it.hasRawContact(rawContactId) }
                val raw = contact.rawContacts.first { it.rawContactId == rawContactId }
                val name = listOfNotNull(raw.givenName, raw.familyName)
                    .joinToString(" ")
                    .ifBlank { contact.displayName }
                val simMode = AccountClassifier.classify(raw.accountType) == AccountCapability.SIM
                val simCaps = if (simMode) simCapsOf(raw.accountType) else null
                val phones = raw.phones.map { phone -> phone.value }.ifEmpty { listOf("") }
                val emails = raw.emails.map { email -> email.value }.ifEmpty { listOf("") }
                val websites = raw.websites.map { it.value }.ifEmpty { listOf("") }
                val addresses = raw.addresses.map { it.value }.ifEmpty { listOf("") }
                _uiState.update {
                    it.copy(
                        loading = false,
                        name = name,
                        phones = phones,
                        emails = emails,
                        organization = raw.organization.orEmpty(),
                        jobTitle = raw.jobTitle.orEmpty(),
                        nickname = raw.nickname.orEmpty(),
                        websites = websites,
                        addresses = addresses,
                        birthday = raw.birthday.orEmpty(),
                        anniversary = raw.anniversary.orEmpty(),
                        note = raw.note.orEmpty(),
                        fixedAccountLabel = strings.get(
                            R.string.editor_fixed_account_label,
                            raw.accountName ?: strings.get(R.string.editor_device),
                            raw.accountType ?: strings.get(R.string.editor_account_local),
                        ),
                        simMode = simMode,
                        simMaxNameLength = simCaps?.maxNameLength ?: it.simMaxNameLength,
                        loadedSnapshot = EditorFormSnapshot(
                            name = name,
                            phones = phones,
                            emails = emails,
                            organization = raw.organization.orEmpty(),
                            jobTitle = raw.jobTitle.orEmpty(),
                            nickname = raw.nickname.orEmpty(),
                            websites = websites,
                            addresses = addresses,
                            birthday = raw.birthday.orEmpty(),
                            anniversary = raw.anniversary.orEmpty(),
                            note = raw.note.orEmpty(),
                        ),
                    )
                }
            }
        }

        /** Probe-backed caps for icc pseudo-accounts; vendor SIM accounts keep the default limit. */
        private suspend fun simCapsOf(accountType: String?): SimCapabilities =
            if (SimRouting.isSimAccount(accountType)) {
                simRepository.capabilities(SimRouting.subscriptionIdOf(accountType))
            } else {
                SimCapabilities(canRead = true, canWrite = true)
            }

        fun markPhonePermissionAsked() {
            _uiState.update { it.copy(phonePermissionAsked = true) }
            viewModelScope.launch { appPrefs.setPhonePermissionAsked() }
        }

        fun setName(value: String) = _uiState.update { it.copy(name = value, simError = null) }

        fun setOrganization(value: String) = _uiState.update { it.copy(organization = value) }

        fun setJobTitle(value: String) = _uiState.update { it.copy(jobTitle = value) }

        fun setNickname(value: String) = _uiState.update { it.copy(nickname = value) }

        fun setBirthday(value: String) = _uiState.update { it.copy(birthday = value) }

        fun setAnniversary(value: String) = _uiState.update { it.copy(anniversary = value) }

        fun setNote(value: String) = _uiState.update { it.copy(note = value) }

        fun setWebsite(index: Int, value: String) =
            _uiState.update { it.copy(websites = it.websites.replaceAt(index, value)) }

        fun addWebsite() = _uiState.update { it.copy(websites = it.websites + "") }

        fun removeWebsite(index: Int) =
            _uiState.update { it.copy(websites = it.websites.removeAt(index).ifEmpty { listOf("") }) }

        fun setAddress(index: Int, value: String) =
            _uiState.update { it.copy(addresses = it.addresses.replaceAt(index, value)) }

        fun addAddress() = _uiState.update { it.copy(addresses = it.addresses + "") }

        fun removeAddress(index: Int) =
            _uiState.update { it.copy(addresses = it.addresses.removeAt(index).ifEmpty { listOf("") }) }

        fun selectAccount(account: ContactAccount) = _uiState.update {
            it.copy(
                selectedAccount = account,
                simMode = account.capability == AccountCapability.SIM,
                simMaxNameLength = simCapsByKey[account.key]?.maxNameLength
                    ?: EditorUiState().simMaxNameLength,
                simError = null,
            )
        }

        fun setPhone(index: Int, value: String) =
            _uiState.update { it.copy(phones = it.phones.replaceAt(index, value), simError = null) }

        fun addPhone() = _uiState.update { it.copy(phones = it.phones + "") }

        fun removePhone(index: Int) =
            _uiState.update { it.copy(phones = it.phones.removeAt(index).ifEmpty { listOf("") }) }

        fun setEmail(index: Int, value: String) =
            _uiState.update { it.copy(emails = it.emails.replaceAt(index, value)) }

        fun addEmail() = _uiState.update { it.copy(emails = it.emails + "") }

        fun removeEmail(index: Int) =
            _uiState.update { it.copy(emails = it.emails.removeAt(index).ifEmpty { listOf("") }) }

        fun save() {
            val state = _uiState.value
            if (!state.canSave) return
            val editable = if (state.simMode) {
                simEditableOrNull(state) ?: return
            } else {
                EditableContact(
                    displayName = state.name.trim(),
                    phones = state.phones
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .map { it to null },
                    emails = state.emails
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .map { it to null },
                    organization = state.organization.trim().ifBlank { null },
                    note = state.note.trim().ifBlank { null },
                    jobTitle = state.jobTitle.trim().ifBlank { null },
                    nickname = state.nickname.trim().ifBlank { null },
                    websites = state.websites.map { it.trim() }.filter { it.isNotBlank() },
                    addresses = state.addresses.map { it.trim() }.filter { it.isNotBlank() },
                    birthday = state.birthday.ifBlank { null },
                    anniversary = state.anniversary.ifBlank { null },
                )
            }
            _uiState.update { it.copy(saving = true) }
            viewModelScope.launch {
                val result = if (rawContactId != null) {
                    writer.updateRawContact(rawContactId, editable)
                } else {
                    writer.createContact(
                        accountType = state.selectedAccount?.type,
                        accountName = state.selectedAccount?.name,
                        contact = editable,
                    )
                }
                when (result) {
                    is ContactOpResult.Success -> {
                        if (rawContactId != null) {
                            analytics.logEvent(AnalyticsEvent.ContactUpdate)
                        } else {
                            analytics.logEvent(
                                AnalyticsEvent.ContactCreate(
                                    AccountClassifier.classify(state.selectedAccount?.type).name,
                                ),
                            )
                        }
                        _events.emit(EditorEvent.Saved)
                    }

                    is ContactOpResult.Failure -> {
                        _uiState.update { it.copy(saving = false) }
                        _events.emit(EditorEvent.ShowMessage(result.message))
                    }
                }
            }
        }

        /**
         * Down-converts the form to a SIM entry (name + single normalized phone),
         * or sets an inline error and returns null when it does not fit the SIM.
         */
        private fun simEditableOrNull(state: EditorUiState): EditableContact? {
            val name = state.name.trim()
            val number = SimRouting.normalizeNumber(
                state.phones
                    .firstOrNull { it.isNotBlank() }
                    .orEmpty()
                    .trim(),
            )
            val caps = SimCapabilities(canRead = true, canWrite = true, maxNameLength = state.simMaxNameLength)
            when (val validation = SimContactValidator.validate(name, number, caps)) {
                is SimValidation.Error -> {
                    _uiState.update { it.copy(simError = validation.error.toMessage(strings)) }
                    return null
                }

                SimValidation.Ok -> {
                    Unit
                }
            }
            return EditableContact(
                displayName = name,
                phones = listOf(number to null),
                emails = emptyList(),
                organization = null,
                note = null,
            )
        }

        private fun Contact.hasRawContact(id: Long): Boolean =
            rawContacts.any { it.rawContactId == id }

        private fun List<String>.replaceAt(index: Int, value: String): List<String> =
            mapIndexed { i, old -> if (i == index) value else old }

        private fun List<String>.removeAt(index: Int): List<String> =
            filterIndexed { i, _ -> i != index }
    }
