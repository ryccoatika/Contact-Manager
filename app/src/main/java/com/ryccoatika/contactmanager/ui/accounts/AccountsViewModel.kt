package com.ryccoatika.contactmanager.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryccoatika.contactmanager.data.AccountsSource
import com.ryccoatika.contactmanager.data.BatchOperationManager
import com.ryccoatika.contactmanager.data.ContactsSource
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class AccountsUiState(
    val accounts: List<ContactAccount> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountsSource: AccountsSource,
    private val contactsSource: ContactsSource,
    private val batchManager: BatchOperationManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountsUiState())
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events

    init {
        viewModelScope.launch {
            _uiState.value = AccountsUiState(accounts = accountsSource.getAccounts(), loading = false)
        }
    }

    /** Moves every raw contact of [source] into [target] as a background batch. */
    fun moveAllContacts(source: ContactAccount, target: ContactAccount) {
        viewModelScope.launch {
            val rawIds = contactsSource.observeContacts().first()
                .flatMap { it.rawContacts }
                .filter { it.accountType == source.type && it.accountName == source.name }
                .map { it.rawContactId }
            if (rawIds.isEmpty()) {
                _events.emit("No contacts to move.")
                return@launch
            }
            batchManager.moveContacts(
                rawContactIds = rawIds,
                targetType = target.type,
                targetName = target.name,
                label = "Moving ${rawIds.size} to ${target.name ?: "this device"}",
            )
            _events.emit("Move started — progress is shown on the contacts screen.")
        }
    }
}
