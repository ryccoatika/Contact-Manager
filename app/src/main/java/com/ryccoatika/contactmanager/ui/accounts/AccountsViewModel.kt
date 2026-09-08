package com.ryccoatika.contactmanager.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryccoatika.contactmanager.data.AccountsSource
import com.ryccoatika.contactmanager.data.AppPrefs
import com.ryccoatika.contactmanager.data.ops.AccountBulkOps
import com.ryccoatika.contactmanager.data.sim.SimStore
import com.ryccoatika.contactmanager.data.sim.SimSubscriptionsSource
import com.ryccoatika.contactmanager.domain.analytics.Analytics
import com.ryccoatika.contactmanager.domain.analytics.AnalyticsEvent
import com.ryccoatika.contactmanager.domain.model.AccountOpMode
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.domain.model.PendingAccountOp
import com.ryccoatika.contactmanager.ui.common.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountsUiState(
    val accounts: List<ContactAccount> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** Defaults true so the one-time phone-permission prompt never flashes before load. */
    val phonePermissionAsked: Boolean = true,
    /** Account keys hidden from the Home selector. */
    val hiddenAccountKeys: Set<String> = emptySet(),
)

@HiltViewModel
class AccountsViewModel
    @Inject
    constructor(
        private val accountsSource: AccountsSource,
        private val bulkOps: AccountBulkOps,
        private val simRepository: SimStore,
        private val simSubscriptionsSource: SimSubscriptionsSource,
        private val appPrefs: AppPrefs,
        private val analytics: Analytics,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(AccountsUiState())
        val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()

        private val _events = MutableSharedFlow<UiEvent>()
        val events: SharedFlow<UiEvent> = _events

        private val _pendingOp = MutableStateFlow<PendingAccountOp?>(null)
        val pendingOp: StateFlow<PendingAccountOp?> = _pendingOp.asStateFlow()

        init {
            viewModelScope.launch {
                val accounts = accountsSource.getAccounts()
                val asked = appPrefs.phonePermissionAsked()
                _uiState.update {
                    it.copy(accounts = accounts, loading = false, phonePermissionAsked = asked)
                }
            }
            viewModelScope.launch {
                appPrefs.observeHiddenAccountKeys().collect { keys ->
                    _uiState.update { it.copy(hiddenAccountKeys = keys) }
                }
            }
        }

        /** Hide/show an account in the Home selector. */
        fun setAccountHidden(account: ContactAccount, hidden: Boolean) {
            analytics.logEvent(AnalyticsEvent.AccountVisibility(hidden))
            viewModelScope.launch { appPrefs.setAccountHidden(account.key, hidden) }
        }

        fun markPhonePermissionAsked() {
            _uiState.update { it.copy(phonePermissionAsked = true) }
            viewModelScope.launch { appPrefs.setPhonePermissionAsked() }
        }

        /**
         * Pull-to-refresh: re-probes every active SIM subscription (fixing stale
         * capability caches after a SIM swap) and re-fetches the account list.
         */
        fun refresh() {
            if (_uiState.value.refreshing) return
            _uiState.update { it.copy(refreshing = true) }
            viewModelScope.launch {
                simSubscriptionsSource.activeSubscriptions().forEach { subscription ->
                    simRepository.refreshCapabilities(subscription.subscriptionId)
                }
                _uiState.update {
                    it.copy(accounts = accountsSource.getAccounts(), loading = false, refreshing = false)
                }
            }
        }

        /** Plans the op and parks it for confirmation (with SIM loss report when relevant). */
        fun requestAccountOp(mode: AccountOpMode, source: ContactAccount, target: ContactAccount) {
            viewModelScope.launch { _pendingOp.value = bulkOps.plan(mode, source, target) }
        }

        fun dismissPendingOp() {
            _pendingOp.value = null
        }

        fun confirmPendingOp() {
            val pending = _pendingOp.value ?: return
            _pendingOp.value = null
            executeAccountOp(pending.mode, pending.source, pending.target)
        }

        /** Moves/copies every raw contact of [source] into [target] as a background batch. */
        fun executeAccountOp(mode: AccountOpMode, source: ContactAccount, target: ContactAccount) {
            viewModelScope.launch { _events.emit(UiEvent.ShowSnackbar(bulkOps.execute(mode, source, target))) }
        }
    }
