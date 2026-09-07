package com.ryccoatika.contactmanager.ui.accounts

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.domain.sim.SimRouting
import com.ryccoatika.contactmanager.ui.analytics.TrackScreenView
import com.ryccoatika.contactmanager.ui.common.AccountDot
import com.ryccoatika.contactmanager.ui.common.AccountVisuals
import com.ryccoatika.contactmanager.ui.common.CapabilityTag
import com.ryccoatika.contactmanager.ui.common.CardsSkeleton
import com.ryccoatika.contactmanager.ui.common.MoveAllConfirmDialog
import com.ryccoatika.contactmanager.ui.common.MoveAllTargetSheet
import com.ryccoatika.contactmanager.ui.common.PhonePermissionPrompt
import com.ryccoatika.contactmanager.ui.common.SectionCard
import com.ryccoatika.contactmanager.ui.common.isMoveTarget
import com.ryccoatika.contactmanager.ui.theme.ContactManagerTheme
import com.ryccoatika.contactmanager.ui.theme.TabularNums

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    onAccountClick: (String) -> Unit,
    embedded: Boolean = false,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    TrackScreenView("accounts")
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingMove by viewModel.pendingMove.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var moveSource by remember { mutableStateOf<ContactAccount?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            if (!embedded) {
                TopAppBar(
                    title = { Text(stringResource(R.string.accounts_title)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.accounts_back),
                            )
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.loading) {
            CardsSkeleton(Modifier.padding(padding), count = 4, height = 76.dp)
        } else {
            // Pull down to re-probe SIM capabilities (stale after a SIM swap)
            // and re-fetch the account list.
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val simPseudoPresent = state.accounts.any { SimRouting.isSimAccount(it.type) }
                    if (simPseudoPresent && !state.phonePermissionAsked) {
                        item(key = "phone-permission-prompt") {
                            PhonePermissionPrompt(
                                onAsked = viewModel::markPhonePermissionAsked,
                                // Granting unlocks carrier labels; re-probe right away.
                                onGranted = viewModel::refresh,
                            )
                        }
                    }
                    items(state.accounts, key = { it.key }) { account ->
                        AccountRow(
                            account = account,
                            hidden = account.key in state.hiddenAccountKeys,
                            onClick = { onAccountClick(account.key) },
                            onMoveAll = { moveSource = account },
                            onToggleHidden = { hidden -> viewModel.setAccountHidden(account, hidden) },
                        )
                    }
                }
            }
        }
    }

    moveSource?.let { source ->
        MoveAllTargetSheet(
            source = source,
            accounts = state.accounts,
            onPick = { target ->
                moveSource = null
                viewModel.requestMoveAll(source, target)
            },
            onDismiss = { moveSource = null },
        )
    }

    pendingMove?.let { pending ->
        MoveAllConfirmDialog(
            pending = pending,
            onConfirm = viewModel::confirmPendingMove,
            onDismiss = viewModel::dismissPendingMove,
        )
    }
}

@Composable
private fun AccountRow(
    account: ContactAccount,
    hidden: Boolean = false,
    onClick: () -> Unit,
    onMoveAll: () -> Unit,
    onToggleHidden: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val readOnly = account.capability == AccountCapability.READ_ONLY
    val label = account.displayLabel ?: AccountVisuals.label(context, account.type, account.name)
    // Prefer the SIM number as the subtitle; hide opaque native SIM account names.
    val subtitle = if (hidden) {
        stringResource(R.string.accounts_hidden_from_selector)
    } else {
        account.phoneNumber ?: account.name?.takeUnless { account.capability == AccountCapability.SIM }
    }

    SectionCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(AccountVisuals.color(account.type, account.name)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${account.contactCount}",
                    style = TabularNums.merge(MaterialTheme.typography.titleMedium),
                )
                Spacer(Modifier.height(4.dp))
                CapabilityTag(account.capability, account.writable)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.accounts_account_actions),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        enabled = !readOnly,
                        text = {
                            Column {
                                Text(stringResource(R.string.accounts_move_all_contacts_to))
                                if (readOnly) {
                                    Text(
                                        stringResource(R.string.accounts_managed_by_app),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        onClick = {
                            menuOpen = false
                            onMoveAll()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (hidden) {
                                        R.string.accounts_show_in_selector
                                    } else {
                                        R.string.accounts_hide_from_selector
                                    },
                                ),
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (hidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onToggleHidden(!hidden)
                        },
                    )
                }
            }
        }
    }
}

@Preview(name = "Account row · light")
@Preview(name = "Account row · dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AccountRowPreview() {
    ContactManagerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AccountRow(
                    ContactAccount("rycco@gmail.com", "com.google", AccountCapability.FULL_CRUD, 201),
                    onClick = {},
                    onMoveAll = {},
                )
                AccountRow(
                    ContactAccount("WhatsApp", "com.whatsapp", AccountCapability.READ_ONLY, 41, writable = false),
                    onClick = {},
                    onMoveAll = {},
                )
            }
        }
    }
}
