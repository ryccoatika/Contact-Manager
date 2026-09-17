package com.ryccoatika.contactmanager.ui.accounts

import android.net.FakeUri
import android.net.Uri
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.data.AccountsSource
import com.ryccoatika.contactmanager.data.BatchOperationManager
import com.ryccoatika.contactmanager.data.ContactOpResult
import com.ryccoatika.contactmanager.data.ContactsSource
import com.ryccoatika.contactmanager.data.ContactsWriter
import com.ryccoatika.contactmanager.data.EditableContact
import com.ryccoatika.contactmanager.data.FakeAppPrefs
import com.ryccoatika.contactmanager.data.FakeStringProvider
import com.ryccoatika.contactmanager.data.ops.DefaultAccountBulkOps
import com.ryccoatika.contactmanager.data.sim.FakeSimContactSource
import com.ryccoatika.contactmanager.data.sim.FakeSimSubscriptionsSource
import com.ryccoatika.contactmanager.data.sim.InMemorySimCapabilityCache
import com.ryccoatika.contactmanager.data.sim.SimRepository
import com.ryccoatika.contactmanager.data.sim.SimSubscription
import com.ryccoatika.contactmanager.data.transfer.ContactTransfer
import com.ryccoatika.contactmanager.data.transfer.ParseOutcome
import com.ryccoatika.contactmanager.data.transfer.TransferResult
import com.ryccoatika.contactmanager.domain.analytics.AnalyticsEvent
import com.ryccoatika.contactmanager.domain.analytics.FakeAnalytics
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.AccountOpMode
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.domain.model.RawContact
import com.ryccoatika.contactmanager.domain.model.SimCapabilities
import com.ryccoatika.contactmanager.domain.vcard.VCardContact
import com.ryccoatika.contactmanager.ui.common.UiEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private val googleAccount = ContactAccount("a@gmail.com", "com.google", AccountCapability.FULL_CRUD, 2)
    private val whatsappAccount = ContactAccount("WhatsApp", "com.whatsapp", AccountCapability.READ_ONLY, 1)
    private val deviceAccount = ContactAccount(null, null, AccountCapability.FULL_CRUD, 0)

    private fun raw(id: Long, accType: String?, accName: String?) = RawContact(
        rawContactId = id,
        accountType = accType,
        accountName = accName,
    )

    private val contactsFlow = MutableStateFlow(
        listOf(
            Contact(1, "Andi", rawContacts = listOf(raw(10, "com.google", "a@gmail.com"))),
            Contact(
                2,
                "Budi",
                rawContacts = listOf(
                    raw(20, "com.whatsapp", "WhatsApp"),
                    raw(21, "com.google", "a@gmail.com"),
                ),
            ),
        ),
    )

    private val fakeContacts = object : ContactsSource {
        override fun observeContacts(): Flow<List<Contact>> = contactsFlow

        override suspend fun snapshot(): List<Contact> = contactsFlow.value
    }
    private val fakeAccounts = object : AccountsSource {
        override suspend fun getAccounts() = listOf(googleAccount, whatsappAccount, deviceAccount)

        override fun observeAccounts() = flow { emit(getAccounts()) }
    }

    private class FakeWriter : ContactsWriter {
        val movedIds = mutableListOf<Long>()
        val copiedIds = mutableListOf<Long>()
        var copyTarget: Pair<String?, String?>? = null
        var moveTarget: Pair<String?, String?>? = null

        override suspend fun createContact(
            accountType: String?,
            accountName: String?,
            contact: EditableContact,
        ): ContactOpResult = ContactOpResult.Success

        override suspend fun updateRawContact(
            rawContactId: Long,
            contact: EditableContact,
        ): ContactOpResult = ContactOpResult.Success

        override suspend fun deleteRawContacts(rawContactIds: List<Long>): ContactOpResult =
            ContactOpResult.Success

        override suspend fun copyRawContact(
            rawContactId: Long,
            targetType: String?,
            targetName: String?,
        ): ContactOpResult = ContactOpResult.Success

        override suspend fun moveRawContacts(
            rawContactIds: List<Long>,
            targetType: String?,
            targetName: String?,
            onProgress: (done: Int, total: Int) -> Unit,
        ): ContactOpResult {
            movedIds += rawContactIds
            moveTarget = targetType to targetName
            return ContactOpResult.Success
        }

        override suspend fun copyRawContacts(
            rawContactIds: List<Long>,
            targetType: String?,
            targetName: String?,
            onProgress: (done: Int, total: Int) -> Unit,
        ): ContactOpResult {
            copiedIds += rawContactIds
            copyTarget = targetType to targetName
            return ContactOpResult.Success
        }

        override suspend fun linkContacts(rawContactIds: List<Long>): ContactOpResult =
            ContactOpResult.Success

        override suspend fun keepSeparate(rawContactIds: List<Long>): ContactOpResult =
            ContactOpResult.Success

        override suspend fun mergeContacts(
            target: RawContact,
            sources: List<RawContact>,
        ): ContactOpResult = ContactOpResult.Success
    }

    private val simSource = FakeSimContactSource(
        capsBySub = mapOf<Int?, SimCapabilities>(1 to SimCapabilities(canRead = true, canWrite = true)),
    )

    private class FakeContactTransfer : ContactTransfer {
        var exportAccountsResult: TransferResult = TransferResult.Success(contactCount = 0, fileCount = 1)
        var exportAccountsToFolderResult: TransferResult = TransferResult.Success(contactCount = 0, fileCount = 1)
        var parseFileResult: ParseOutcome = ParseOutcome.Parsed(contacts = emptyList(), skippedCards = 0)
        var startImportResult: Boolean = true

        val exportAccountsCalls = mutableListOf<Pair<List<ContactAccount>, Uri>>()
        val exportAccountsToFolderCalls = mutableListOf<Pair<List<ContactAccount>, Uri>>()
        val parseFileCalls = mutableListOf<Uri>()
        val startImportCalls = mutableListOf<Pair<List<VCardContact>, List<ContactAccount>>>()

        override suspend fun exportAccounts(accounts: List<ContactAccount>, uri: Uri): TransferResult {
            exportAccountsCalls += accounts to uri
            return exportAccountsResult
        }

        override suspend fun exportAccountsToFolder(accounts: List<ContactAccount>, treeUri: Uri): TransferResult {
            exportAccountsToFolderCalls += accounts to treeUri
            return exportAccountsToFolderResult
        }

        override suspend fun exportRawContacts(rawContactIds: List<Long>, uri: Uri): TransferResult =
            TransferResult.Success(contactCount = rawContactIds.size, fileCount = 1)

        override suspend fun parseFile(uri: Uri): ParseOutcome {
            parseFileCalls += uri
            return parseFileResult
        }

        override fun startImport(contacts: List<VCardContact>, targets: List<ContactAccount>): Boolean {
            startImportCalls += contacts to targets
            return startImportResult
        }
    }

    private fun vm(
        writer: FakeWriter = FakeWriter(),
        analytics: FakeAnalytics = FakeAnalytics(),
        transfer: ContactTransfer = FakeContactTransfer(),
    ) = AccountsViewModel(
        accountsSource = fakeAccounts,
        bulkOps = DefaultAccountBulkOps(
            fakeContacts,
            BatchOperationManager(writer, CoroutineScope(SupervisorJob() + dispatcher)),
            analytics,
            FakeStringProvider(),
        ),
        simRepository = SimRepository(simSource, InMemorySimCapabilityCache()),
        simSubscriptionsSource = FakeSimSubscriptionsSource(listOf(SimSubscription(1, "SIM 1"))),
        appPrefs = FakeAppPrefs(),
        analytics = analytics,
        transfer = transfer,
        strings = FakeStringProvider(),
    )

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun `loads accounts with capabilities`() = runTest(dispatcher) {
        val vm = vm()
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.loading)
        assertEquals(
            listOf(googleAccount, whatsappAccount, deviceAccount),
            vm.uiState.value.accounts,
        )
    }

    @Test fun `setAccountHidden toggles the account in hiddenAccountKeys`() = runTest(dispatcher) {
        val vm = vm()
        dispatcher.scheduler.advanceUntilIdle()
        vm.setAccountHidden(googleAccount, true)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(googleAccount.key in vm.uiState.value.hiddenAccountKeys)
        vm.setAccountHidden(googleAccount, false)
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(googleAccount.key in vm.uiState.value.hiddenAccountKeys)
    }

    @Test fun `moveAllContacts moves every raw contact of the source account only`() = runTest(dispatcher) {
        val writer = FakeWriter()
        val vm = vm(writer)
        dispatcher.scheduler.advanceUntilIdle()
        vm.executeAccountOp(AccountOpMode.MOVE, source = googleAccount, target = deviceAccount)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(10L, 21L), writer.movedIds)
        assertEquals(null to null, writer.moveTarget)
    }

    @Test fun `moveAllContacts and setAccountHidden log analytics events`() = runTest(dispatcher) {
        val analytics = FakeAnalytics()
        val vm = vm(analytics = analytics)
        dispatcher.scheduler.advanceUntilIdle()
        vm.setAccountHidden(googleAccount, true)
        vm.executeAccountOp(AccountOpMode.MOVE, source = googleAccount, target = deviceAccount)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(AnalyticsEvent.AccountVisibility(hidden = true), analytics.events.first())
        assertTrue(analytics.events.any { it is AnalyticsEvent.AccountMoveAll })
    }

    @Test fun `copy all copies every raw contact of the source, moving nothing`() = runTest(dispatcher) {
        val writer = FakeWriter()
        val analytics = FakeAnalytics()
        val vm = vm(writer, analytics)
        dispatcher.scheduler.advanceUntilIdle()
        vm.executeAccountOp(AccountOpMode.COPY, source = googleAccount, target = deviceAccount)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(10L, 21L), writer.copiedIds)
        assertEquals(null to null, writer.copyTarget)
        assertTrue(writer.movedIds.isEmpty())
        assertTrue(analytics.events.any { it is AnalyticsEvent.AccountCopyAll })
    }

    @Test fun `copy all works on a read-only source account`() = runTest(dispatcher) {
        val writer = FakeWriter()
        val vm = vm(writer)
        dispatcher.scheduler.advanceUntilIdle()
        vm.executeAccountOp(AccountOpMode.COPY, source = whatsappAccount, target = googleAccount)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(20L), writer.copiedIds)
        assertTrue(writer.movedIds.isEmpty())
    }

    @Test fun `moveAllContacts with empty source moves nothing`() = runTest(dispatcher) {
        val writer = FakeWriter()
        val vm = vm(writer)
        dispatcher.scheduler.advanceUntilIdle()
        vm.executeAccountOp(AccountOpMode.MOVE, source = deviceAccount, target = googleAccount)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(writer.movedIds.isEmpty())
    }

    @Test fun `refresh re-probes every active sim subscription and reloads accounts`() = runTest(dispatcher) {
        val vm = vm()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, simSource.probes)
        vm.refresh()
        assertTrue(vm.uiState.value.refreshing)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, simSource.probes)
        assertFalse(vm.uiState.value.refreshing)
        assertEquals(
            listOf(googleAccount, whatsappAccount, deviceAccount),
            vm.uiState.value.accounts,
        )
    }

    @Test fun `exportToFile emits count snackbar on success and logs contacts_export`() = runTest(dispatcher) {
        val transfer = FakeContactTransfer().apply {
            exportAccountsResult = TransferResult.Success(contactCount = 5, fileCount = 1)
        }
        val analytics = FakeAnalytics()
        val vm = vm(transfer = transfer, analytics = analytics)
        dispatcher.scheduler.advanceUntilIdle()
        val events = mutableListOf<UiEvent>()
        val job = launch { vm.events.collect { events += it } }
        val uri = FakeUri("content://export")

        vm.exportToFile(listOf(googleAccount), uri)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(listOf(googleAccount) to (uri as Uri)), transfer.exportAccountsCalls)
        assertEquals(
            AnalyticsEvent.ContactsExport(count = 5, accountCount = 1, perAccountFiles = false),
            analytics.events.single(),
        )
        assertEquals(
            listOf(UiEvent.ShowSnackbar(FakeStringProvider().getQuantity(R.plurals.accounts_msg_exported, 5, 5))),
            events,
        )
        job.cancel()
    }

    @Test fun `exportToFile emits failure message on Failure`() = runTest(dispatcher) {
        val transfer = FakeContactTransfer().apply {
            exportAccountsResult = TransferResult.Failure("boom")
        }
        val analytics = FakeAnalytics()
        val vm = vm(transfer = transfer, analytics = analytics)
        dispatcher.scheduler.advanceUntilIdle()
        val events = mutableListOf<UiEvent>()
        val job = launch { vm.events.collect { events += it } }

        vm.exportToFile(listOf(googleAccount), FakeUri("content://export"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf<UiEvent>(UiEvent.ShowSnackbar("boom")), events)
        assertTrue(analytics.events.isEmpty())
        job.cancel()
    }

    @Test fun `exportToFolder logs perAccountFiles true`() = runTest(dispatcher) {
        val transfer = FakeContactTransfer().apply {
            exportAccountsToFolderResult = TransferResult.Success(contactCount = 3, fileCount = 2)
        }
        val analytics = FakeAnalytics()
        val vm = vm(transfer = transfer, analytics = analytics)
        dispatcher.scheduler.advanceUntilIdle()

        vm.exportToFolder(listOf(googleAccount, whatsappAccount), FakeUri("content://tree"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, transfer.exportAccountsToFolderCalls.size)
        assertEquals(
            AnalyticsEvent.ContactsExport(count = 3, accountCount = 2, perAccountFiles = true),
            analytics.events.single(),
        )
    }

    @Test fun `requestImport parks PendingImport on successful parse`() = runTest(dispatcher) {
        val parsedContacts = listOf(VCardContact(displayName = "Andi"))
        val transfer = FakeContactTransfer().apply {
            parseFileResult = ParseOutcome.Parsed(contacts = parsedContacts, skippedCards = 1)
        }
        val vm = vm(transfer = transfer)
        dispatcher.scheduler.advanceUntilIdle()
        val uri = FakeUri("content://import")

        vm.requestImport(listOf(googleAccount), uri)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            PendingImport(targets = listOf(googleAccount), contacts = parsedContacts, skippedCards = 1),
            vm.pendingImport.value,
        )
        assertEquals(listOf(uri as Uri), transfer.parseFileCalls)
    }

    @Test fun `requestImport emits error snackbar and parks nothing on parse failure`() = runTest(dispatcher) {
        val transfer = FakeContactTransfer().apply {
            parseFileResult = ParseOutcome.Failure("bad file")
        }
        val vm = vm(transfer = transfer)
        dispatcher.scheduler.advanceUntilIdle()
        val events = mutableListOf<UiEvent>()
        val job = launch { vm.events.collect { events += it } }

        vm.requestImport(listOf(googleAccount), FakeUri("content://import"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf<UiEvent>(UiEvent.ShowSnackbar("bad file")), events)
        assertNull(vm.pendingImport.value)
        job.cancel()
    }

    @Test fun `confirmPendingImport starts import, clears pending, logs contacts_import with simTarget flag`() =
        runTest(dispatcher) {
            val simAccount = ContactAccount("SIM 1", "sim", AccountCapability.SIM, 0)
            val parsedContacts = listOf(VCardContact(displayName = "Andi"))
            val transfer = FakeContactTransfer().apply {
                parseFileResult = ParseOutcome.Parsed(contacts = parsedContacts, skippedCards = 0)
                startImportResult = true
            }
            val analytics = FakeAnalytics()
            val vm = vm(transfer = transfer, analytics = analytics)
            dispatcher.scheduler.advanceUntilIdle()
            val events = mutableListOf<UiEvent>()
            val job = launch { vm.events.collect { events += it } }

            vm.requestImport(listOf(googleAccount, simAccount), FakeUri("content://import"))
            dispatcher.scheduler.advanceUntilIdle()
            vm.confirmPendingImport()

            assertNull(vm.pendingImport.value)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf(parsedContacts to listOf(googleAccount, simAccount)), transfer.startImportCalls)
            assertEquals(
                AnalyticsEvent.ContactsImport(count = 1, accountCount = 2, simTarget = true),
                analytics.events.single(),
            )
            assertEquals(
                listOf(UiEvent.ShowSnackbar(FakeStringProvider().get(R.string.accounts_msg_import_started))),
                events,
            )
            job.cancel()
        }

    @Test fun `confirmPendingImport emits busy snackbar when batch already running`() = runTest(dispatcher) {
        val parsedContacts = listOf(VCardContact(displayName = "Andi"))
        val transfer = FakeContactTransfer().apply {
            parseFileResult = ParseOutcome.Parsed(contacts = parsedContacts, skippedCards = 0)
            startImportResult = false
        }
        val analytics = FakeAnalytics()
        val vm = vm(transfer = transfer, analytics = analytics)
        dispatcher.scheduler.advanceUntilIdle()
        val events = mutableListOf<UiEvent>()
        val job = launch { vm.events.collect { events += it } }

        vm.requestImport(listOf(googleAccount), FakeUri("content://import"))
        dispatcher.scheduler.advanceUntilIdle()
        vm.confirmPendingImport()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(analytics.events.isEmpty())
        assertEquals(
            listOf(UiEvent.ShowSnackbar(FakeStringProvider().get(R.string.accounts_msg_busy))),
            events,
        )
        job.cancel()
    }
}
