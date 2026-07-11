package com.ryccoatika.contactmanager.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryccoatika.contactmanager.data.AccountsSource
import com.ryccoatika.contactmanager.data.ContactsSource
import com.ryccoatika.contactmanager.di.DefaultDispatcher
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val contacts: List<Contact> = emptyList(),
    val accounts: List<ContactAccount> = emptyList(),
    val selectedAccountKey: String? = null,
    val query: String = "",
    val loading: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val contactsSource: ContactsSource,
    private val accountsSource: AccountsSource,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedAccountKey = MutableStateFlow<String?>(null)
    private val accounts = MutableStateFlow<List<ContactAccount>>(emptyList())

    init {
        viewModelScope.launch { accounts.value = accountsSource.getAccounts() }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        contactsSource.observeContacts(), accounts, query, selectedAccountKey,
    ) { contacts, accounts, query, accountKey ->
        HomeUiState(
            contacts = contacts.filtered(query, accountKey),
            accounts = accounts,
            selectedAccountKey = accountKey,
            query = query,
            loading = false,
        )
    }.flowOn(defaultDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setQuery(q: String) { query.value = q }

    fun selectAccount(key: String?) { selectedAccountKey.value = key }

    private fun List<Contact>.filtered(query: String, accountKey: String?): List<Contact> {
        var result = this
        if (accountKey != null) {
            result = result.filter { contact ->
                contact.rawContacts.any { "${it.accountType}/${it.accountName}" == accountKey }
            }
        }
        if (query.isNotBlank()) {
            val q = query.trim()
            val qDigits = q.filter { it.isDigit() }
            result = result.filter { contact ->
                contact.displayName.contains(q, ignoreCase = true) ||
                    (qDigits.isNotEmpty() && contact.rawContacts.any { raw ->
                        raw.phones.any { it.value.filter(Char::isDigit).contains(qDigits) }
                    }) ||
                    contact.rawContacts.any { raw -> raw.emails.any { it.value.contains(q, true) } }
            }
        }
        return result
    }
}
