package com.ryccoatika.contactmanager.ui.accounts

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.ryccoatika.contactmanager.domain.model.AccountOpMode
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.domain.sim.SimRouting
import com.ryccoatika.contactmanager.ui.analytics.TrackScreenView
import com.ryccoatika.contactmanager.ui.common.AccountDot
import com.ryccoatika.contactmanager.ui.common.AccountOpConfirmDialog
import com.ryccoatika.contactmanager.ui.common.AccountOpTargetSheet
import com.ryccoatika.contactmanager.ui.common.AccountVisuals
import com.ryccoatika.contactmanager.ui.common.CapabilityTag
import com.ryccoatika.contactmanager.ui.common.CardsSkeleton
import com.ryccoatika.contactmanager.ui.common.CollectUiEvents
import com.ryccoatika.contactmanager.ui.common.PhonePermissionPrompt
import com.ryccoatika.contactmanager.ui.common.SectionCard
import com.ryccoatika.contactmanager.ui.common.isMoveTarget
import com.ryccoatika.contactmanager.ui.theme.ContactManagerTheme
import com.ryccoatika.contactmanager.ui.theme.TabularNums
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val pendingOp by viewModel.pendingOp.collectAsStateWithLifecycle()
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var opRequest by remember { mutableStateOf<Pair<AccountOpMode, ContactAccount>?>(null) }
    // Menu is transient chrome — fine to lose across process death, unlike the SAF hand-offs below.
    var transferMenuOpen by remember { mutableStateOf(false) }
    var sheetMode by rememberSaveable(stateSaver = TransferModeSaver) { mutableStateOf<TransferMode?>(null) }
    // Transient chrome, same rationale as transferMenuOpen above — a stale flag is reset
    // wherever ImportConfirmDialog itself is dismissed/confirmed so it can't leak into the
    // next parsed import.
    var showImportPicker by remember { mutableStateOf(false) }
    // ContactAccount isn't Parcelable/Serializable, so SAF round-trips (which can outlive the
    // process on low memory) persist lightweight account keys instead and re-resolve against
    // state.accounts once it has (re)loaded — see the LaunchedEffects below. Resolution is never
    // done inline in a launcher callback or in composition: after a process-death restore, both
    // can run before AccountsViewModel's init fetch has repopulated state.accounts.
    var exportRequestKeys by rememberSaveable(stateSaver = AccountKeysSaver) {
        mutableStateOf<List<String>?>(null)
    }
    var importTargetKeys by rememberSaveable(stateSaver = AccountKeysSaver) {
        mutableStateOf<List<String>?>(null)
    }
    // The picked SAF result itself; Uri is Parcelable so rememberSaveable handles it natively.
    var pendingExportFileUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingExportFolderUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingImportFileUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val importNoneMessage = stringResource(R.string.accounts_msg_import_none)

    val exportFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/x-vcard"),
    ) { uri ->
        // A null uri means the user backed out of the picker — safe to clear right away here,
        // this is an event handler, not composition.
        if (uri == null) exportRequestKeys = null else pendingExportFileUri = uri
    }
    val exportFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri == null) exportRequestKeys = null else pendingExportFolderUri = treeUri
    }
    val importFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) importTargetKeys = null else pendingImportFileUri = uri
    }

    // Waits for state.loading to settle before resolving keys → accounts and calling the
    // ViewModel, so a SAF result delivered right after a process-death restore isn't silently
    // dropped because the account list hadn't reloaded yet.
    LaunchedEffect(pendingExportFileUri, state.loading) {
        val uri = pendingExportFileUri ?: return@LaunchedEffect
        if (state.loading) return@LaunchedEffect
        val accounts = exportRequestKeys?.let { keys -> state.accounts.filter { it.key in keys } }
        pendingExportFileUri = null
        exportRequestKeys = null
        if (!accounts.isNullOrEmpty()) viewModel.exportToFile(accounts, uri)
    }
    LaunchedEffect(pendingExportFolderUri, state.loading) {
        val treeUri = pendingExportFolderUri ?: return@LaunchedEffect
        if (state.loading) return@LaunchedEffect
        val accounts = exportRequestKeys?.let { keys -> state.accounts.filter { it.key in keys } }
        pendingExportFolderUri = null
        exportRequestKeys = null
        if (!accounts.isNullOrEmpty()) viewModel.exportToFolder(accounts, treeUri)
    }
    LaunchedEffect(pendingImportFileUri, state.loading) {
        val uri = pendingImportFileUri ?: return@LaunchedEffect
        if (state.loading) return@LaunchedEffect
        val targets = importTargetKeys?.let { keys -> state.accounts.filter { it.key in keys } }
        pendingImportFileUri = null
        importTargetKeys = null
        if (!targets.isNullOrEmpty()) viewModel.requestImport(targets, uri)
    }
    // No SAF result yet (still on the ExportModeDialog step) but the saved keys no longer
    // resolve to a current account once loading has settled — clear instead of leaving the
    // dialog stuck on a dead selection. An effect, not inline in composition, since it's a
    // state write.
    LaunchedEffect(exportRequestKeys, state.loading) {
        val keys = exportRequestKeys ?: return@LaunchedEffect
        if (state.loading) return@LaunchedEffect
        if (state.accounts.none { it.key in keys }) exportRequestKeys = null
    }

    fun startExport(accounts: List<ContactAccount>) {
        exportRequestKeys = accounts.map { it.key }
        if (accounts.size == 1) {
            exportFileLauncher.launch(suggestedExportFileName(context, accounts))
        }
        // Otherwise exportRequestKeys?.let { … } below shows ExportModeDialog to pick
        // one-file/per-account.
    }

    fun startImport(targets: List<ContactAccount>) {
        importTargetKeys = targets.map { it.key }
        importFileLauncher.launch(IMPORT_MIME_TYPES)
    }

    CollectUiEvents(viewModel.events, snackbarHostState)

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
                    actions = {
                        Box {
                            IconButton(onClick = { transferMenuOpen = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.accounts_transfer_menu),
                                )
                            }
                            DropdownMenu(
                                expanded = transferMenuOpen,
                                onDismissRequest = { transferMenuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.accounts_export_contacts)) },
                                    onClick = {
                                        transferMenuOpen = false
                                        sheetMode = TransferMode.EXPORT
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.accounts_import_contacts)) },
                                    onClick = {
                                        transferMenuOpen = false
                                        sheetMode = TransferMode.IMPORT
                                    },
                                )
                            }
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
                            onMoveAll = { opRequest = AccountOpMode.MOVE to account },
                            onCopyAll = { opRequest = AccountOpMode.COPY to account },
                            onExport = { startExport(listOf(account)) },
                            onImport = { startImport(listOf(account)) },
                            onToggleHidden = { hidden -> viewModel.setAccountHidden(account, hidden) },
                        )
                    }
                }
            }
        }
    }

    opRequest?.let { (mode, source) ->
        AccountOpTargetSheet(
            mode = mode,
            source = source,
            accounts = state.accounts,
            onPick = { target ->
                opRequest = null
                viewModel.requestAccountOp(mode, source, target)
            },
            onDismiss = { opRequest = null },
        )
    }

    pendingOp?.let { pending ->
        AccountOpConfirmDialog(
            pending = pending,
            onConfirm = viewModel::confirmPendingOp,
            onDismiss = viewModel::dismissPendingOp,
        )
    }

    sheetMode?.let { mode ->
        TransferAccountsSheet(
            mode = mode,
            accounts = state.accounts,
            onConfirm = { selected ->
                sheetMode = null
                if (mode == TransferMode.EXPORT) startExport(selected) else startImport(selected)
            },
            onDismiss = { sheetMode = null },
        )
    }

    exportRequestKeys?.let { keys ->
        // Empty resolution (account removed, or restore before the list finished loading) is
        // handled by the LaunchedEffect above — nothing to show here either way, and no state
        // write belongs in composition.
        val accounts = state.accounts.filter { it.key in keys }
        if (accounts.size > 1) {
            ExportModeDialog(
                onOneFile = { exportFileLauncher.launch(suggestedExportFileName(context, accounts)) },
                onPerAccount = { exportFolderLauncher.launch(null) },
                onDismiss = { exportRequestKeys = null },
            )
        }
    }

    pendingImport?.let { pending ->
        if (pending.contacts.isEmpty()) {
            // Defensive: ContactTransfer.parseFile returns ParseOutcome.Failure (not Parsed)
            // when zero contacts were read, so PendingImport.contacts is never actually empty
            // today — this guards the cross-layer invariant rather than a reachable state.
            LaunchedEffect(pending) {
                viewModel.dismissPendingImport()
                snackbarHostState.showSnackbar(importNoneMessage)
            }
        } else if (showImportPicker) {
            ImportContactsPickerSheet(
                pending = pending,
                onConfirm = {
                    showImportPicker = false
                    viewModel.confirmPendingImportOf(it)
                },
                // Falls back to the confirm dialog rather than dismissing the whole import —
                // pending is kept, only the picker step is backed out of.
                onDismiss = { showImportPicker = false },
            )
        } else {
            ImportConfirmDialog(
                pending = pending,
                onConfirm = {
                    showImportPicker = false
                    viewModel.confirmPendingImport()
                },
                onChoose = { showImportPicker = true },
                onDismiss = {
                    showImportPicker = false
                    viewModel.dismissPendingImport()
                },
            )
        }
    }
}

// MIME types offered when picking a vCard file to import; the wildcard entry covers OEM pickers
// that mislabel .vcf files with a generic MIME type.
private val IMPORT_MIME_TYPES = arrayOf("text/x-vcard", "text/vcard", "text/directory", "*/*")

/** Saves [TransferMode] as its enum name so `sheetMode` survives process death. */
private val TransferModeSaver: Saver<TransferMode?, String> = Saver(
    save = { it?.name ?: "" },
    restore = { if (it.isEmpty()) null else TransferMode.valueOf(it) },
)

/**
 * Saves the [ContactAccount.key]s standing in for a pending export/import selection —
 * `ContactAccount` itself isn't Parcelable/Serializable, and SAF round-trips can outlive the
 * process on low memory, so the raw accounts can't be stashed directly.
 */
private val AccountKeysSaver: Saver<List<String>?, Any> = listSaver(
    save = { it ?: emptyList() },
    restore = { if (it.isEmpty()) null else it },
)

private fun sanitizedFileStem(label: String): String =
    label
        .trim()
        .replace(Regex("[^A-Za-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "account" }

private fun suggestedExportFileName(context: Context, accounts: List<ContactAccount>): String {
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    return if (accounts.size == 1) {
        val account = accounts.first()
        val label = account.displayLabel ?: AccountVisuals.label(context, account.type, account.name)
        "${sanitizedFileStem(label)}-$date.vcf"
    } else {
        "contacts-$date.vcf"
    }
}

@Composable
private fun AccountRow(
    account: ContactAccount,
    hidden: Boolean = false,
    onClick: () -> Unit,
    onMoveAll: () -> Unit,
    onCopyAll: () -> Unit = {},
    onExport: () -> Unit = {},
    onImport: () -> Unit = {},
    onToggleHidden: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val readOnly = account.capability == AccountCapability.READ_ONLY
    val notImportTarget = !isMoveTarget(account)
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
                    // Copy never touches the source, so it works on read-only accounts too.
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.accounts_copy_all_contacts_to)) },
                        onClick = {
                            menuOpen = false
                            onCopyAll()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.accounts_export_contacts)) },
                        onClick = {
                            menuOpen = false
                            onExport()
                        },
                    )
                    DropdownMenuItem(
                        enabled = !notImportTarget,
                        text = {
                            Column {
                                Text(stringResource(R.string.accounts_import_contacts))
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
                            onImport()
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
