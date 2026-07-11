package com.ryccoatika.contactmanager.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryccoatika.contactmanager.data.AccountsSource
import com.ryccoatika.contactmanager.data.ContactOpResult
import com.ryccoatika.contactmanager.data.ContactsSource
import com.ryccoatika.contactmanager.data.ContactsWriter
import com.ryccoatika.contactmanager.data.EditableContact
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface EditorEvent {
    data object Saved : EditorEvent
    data class ShowMessage(val message: String) : EditorEvent
}

data class EditorUiState(
    val isEdit: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val name: String = "",
    val phones: List<String> = listOf(""),
    val emails: List<String> = listOf(""),
    val organization: String = "",
    val note: String = "",
    val accounts: List<ContactAccount> = emptyList(),
    val selectedAccount: ContactAccount? = null,
    val fixedAccountLabel: String? = null,
) {
    val canSave: Boolean
        get() = !saving && !loading && (
            name.isNotBlank() ||
                phones.any { it.isNotBlank() } ||
                emails.any { it.isNotBlank() }
            )
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contactsSource: ContactsSource,
    private val accountsSource: AccountsSource,
    private val writer: ContactsWriter,
) : ViewModel() {

    private val rawContactId: Long? = savedStateHandle["rawContactId"]

    private val _uiState = MutableStateFlow(EditorUiState(isEdit = rawContactId != null))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<EditorEvent>()
    val events: SharedFlow<EditorEvent> = _events

    init {
        if (rawContactId == null) loadForCreate() else loadForEdit(rawContactId)
    }

    private fun loadForCreate() {
        viewModelScope.launch {
            val writable = accountsSource.getAccounts()
                .filter { it.capability == AccountCapability.FULL_CRUD }
            _uiState.update {
                it.copy(loading = false, accounts = writable, selectedAccount = writable.firstOrNull())
            }
        }
    }

    private fun loadForEdit(rawContactId: Long) {
        viewModelScope.launch {
            val contact = contactsSource.observeContacts()
                .first { contacts -> contacts.any { it.hasRawContact(rawContactId) } }
                .first { it.hasRawContact(rawContactId) }
            val raw = contact.rawContacts.first { it.rawContactId == rawContactId }
            val name = listOfNotNull(raw.givenName, raw.familyName)
                .joinToString(" ")
                .ifBlank { contact.displayName }
            _uiState.update {
                it.copy(
                    loading = false,
                    name = name,
                    phones = raw.phones.map { phone -> phone.value }.ifEmpty { listOf("") },
                    emails = raw.emails.map { email -> email.value }.ifEmpty { listOf("") },
                    organization = raw.organization.orEmpty(),
                    note = raw.note.orEmpty(),
                    fixedAccountLabel = "${raw.accountName ?: "Device"} (${raw.accountType ?: "local"})",
                )
            }
        }
    }

    fun setName(value: String) = _uiState.update { it.copy(name = value) }

    fun setOrganization(value: String) = _uiState.update { it.copy(organization = value) }

    fun setNote(value: String) = _uiState.update { it.copy(note = value) }

    fun selectAccount(account: ContactAccount) = _uiState.update { it.copy(selectedAccount = account) }

    fun setPhone(index: Int, value: String) =
        _uiState.update { it.copy(phones = it.phones.replaceAt(index, value)) }

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
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            val editable = EditableContact(
                displayName = state.name.trim(),
                phones = state.phones.map { it.trim() }.filter { it.isNotBlank() }.map { it to null },
                emails = state.emails.map { it.trim() }.filter { it.isNotBlank() }.map { it to null },
                organization = state.organization.trim().ifBlank { null },
                note = state.note.trim().ifBlank { null },
            )
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
                is ContactOpResult.Success -> _events.emit(EditorEvent.Saved)
                is ContactOpResult.Failure -> {
                    _uiState.update { it.copy(saving = false) }
                    _events.emit(EditorEvent.ShowMessage(result.message))
                }
            }
        }
    }

    private fun Contact.hasRawContact(id: Long): Boolean =
        rawContacts.any { it.rawContactId == id }

    private fun List<String>.replaceAt(index: Int, value: String): List<String> =
        mapIndexed { i, old -> if (i == index) value else old }

    private fun List<String>.removeAt(index: Int): List<String> =
        filterIndexed { i, _ -> i != index }
}
