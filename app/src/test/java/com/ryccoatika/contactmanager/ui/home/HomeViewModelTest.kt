package com.ryccoatika.contactmanager.ui.home

import com.ryccoatika.contactmanager.data.AccountsSource
import com.ryccoatika.contactmanager.data.AppPrefs
import com.ryccoatika.contactmanager.data.BatchOperationManager
import com.ryccoatika.contactmanager.data.ContactOpResult
import com.ryccoatika.contactmanager.data.ContactsSource
import com.ryccoatika.contactmanager.data.ContactsWriter
import com.ryccoatika.contactmanager.data.DuplicatePrefs
import com.ryccoatika.contactmanager.data.EditableContact
import com.ryccoatika.contactmanager.data.FakeAppPrefs
import com.ryccoatika.contactmanager.data.FakeStringProvider
import com.ryccoatika.contactmanager.data.ops.DefaultAccountBulkOps
import com.ryccoatika.contactmanager.domain.analytics.AnalyticsEvent
import com.ryccoatika.contactmanager.domain.analytics.FakeAnalytics
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.domain.model.LabeledValue
import com.ryccoatika.contactmanager.domain.model.RawContact
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private fun raw(id: Long, accType: String?, phone: String? = null) = RawContact(
        rawContactId = id,
        accountType = accType,
        accountName = "acc",
        phones = phone?.let { listOf(LabeledValue(1, it, null)) } ?: emptyList(),
    )

    private fun contact(id: Long, name: String, vararg raws: RawContact) = Contact(
        contactId = id,
        displayName = name,
        rawContacts = raws.toList(),
    )

    private val contactsFlow = MutableStateFlow(
        listOf(
            contact(1, "Andi Wijaya", raw(10, "com.google", "+62812111")),
            contact(2, "Budi Santoso", raw(20, "com.whatsapp")),
            contact(3, "Citra Lestari", raw(30, "com.google", "+62899000"), raw(31, "com.whatsapp")),
        ),
    )

    private val fakeContacts = object : ContactsSource {
        override fun observeContacts(): Flow<List<Contact>> = contactsFlow

        override suspend fun snapshot(): List<Contact> = contactsFlow.value
    }
    private val fakeAccounts = object : AccountsSource {
        override suspend fun getAccounts() = listOf(
            ContactAccount("acc", "com.google", AccountCapability.FULL_CRUD, 2),
            ContactAccount("acc", "com.whatsapp", AccountCapability.READ_ONLY, 2),
        )

        override fun observeAccounts() = flow { emit(getAccounts()) }
    }

    private class FakeWriter : ContactsWriter {
        val deletedIds = mutableListOf<List<Long>>()
        val movedIds = mutableListOf<Long>()
        val copiedIds = mutableListOf<Long>()
        var copyTarget: Pair<String?, String?>? = null
        var moveTarget: Pair<String?, String?>? = null
        var merged: Pair<RawContact, List<RawContact>>? = null

        override suspend fun createContact(
            accountType: String?,
            accountName: String?,
            contact: EditableContact,
        ): ContactOpResult = ContactOpResult.Success

        override suspend fun updateRawContact(
            rawContactId: Long,
            contact: EditableContact,
        ): ContactOpResult = ContactOpResult.Success

        override suspend fun deleteRawContacts(rawContactIds: List<Long>): ContactOpResult {
            deletedIds += rawContactIds
            return ContactOpResult.Success
        }

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
        ): ContactOpResult {
            merged = target to sources
            return ContactOpResult.Success
        }
    }

    private val googleTarget = ContactAccount("b@gmail.com", "com.google", AccountCapability.FULL_CRUD, 0)

    private val dismissedKeys = MutableStateFlow<Set<String>>(emptySet())

    private val fakePrefs = object : DuplicatePrefs {
        override fun observeDismissedKeys(): Flow<Set<String>> = dismissedKeys

        override suspend fun dismissedKeys(): Set<String> = dismissedKeys.value

        override suspend fun dismiss(key: String) {
            dismissedKeys.value = dismissedKeys.value + key
        }
    }

    private fun vm(
        writer: FakeWriter = FakeWriter(),
        appPrefs: AppPrefs = FakeAppPrefs(),
        analytics: FakeAnalytics = FakeAnalytics(),
    ): HomeViewModel {
        val batchManager = BatchOperationManager(writer, CoroutineScope(SupervisorJob() + dispatcher))
        val strings = FakeStringProvider()
        return HomeViewModel(
            contactsSource = fakeContacts,
            accountsSource = fakeAccounts,
            writer = writer,
            batchManager = batchManager,
            bulkOps = DefaultAccountBulkOps(fakeContacts, batchManager, analytics, strings),
            duplicatePrefs = fakePrefs,
            appPrefs = appPrefs,
            strings = strings,
            analytics = analytics,
            defaultDispatcher = dispatcher,
        )
    }

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun `hidden account drops its chip and its only-contacts from All`() = runTest(dispatcher) {
        val vm = vm(appPrefs = FakeAppPrefs(hiddenAccountKeys = setOf("com.whatsapp/acc")))
        val job = launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        val state = vm.uiState.value
        // WhatsApp chip is gone.
        assertEquals(listOf("com.google"), state.accounts.map { it.type })
        // Budi (WhatsApp-only) is hidden; Citra stays (also in Google).
        assertEquals(listOf("Andi Wijaya", "Citra Lestari"), state.contacts.map { it.displayName })
        job.cancel()
    }

    @Test fun `emits all contacts and accounts`() = runTest(dispatcher) {
        val vm = vm()
        val job = launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(3, vm.uiState.value.contacts.size)
        assertEquals(2, vm.uiState.value.accounts.size)
        job.cancel()
    }

    @Test fun `account filter keeps only contacts having raw contact in account`() = runTest(dispatcher) {
        val vm = vm()
        val job = launch { vm.uiState.collect {} }
        vm.selectAccount("com.whatsapp/acc")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            listOf("Budi Santoso", "Citra Lestari"),
            vm.uiState.value.contacts
                .map { it.displayName },
        )
        job.cancel()
    }

    @Test fun `query matches name case-insensitive and phone substring`() = runTest(dispatcher) {
        val vm = vm()
        val job = launch { vm.uiState.collect {} }
        vm.setQuery("andi")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.uiState.value.contacts.size)
        vm.setQuery("812111")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            listOf("Andi Wijaya"),
            vm.uiState.value.contacts
                .map { it.displayName },
        )
        job.cancel()
    }

    @Test fun `toggleSelect adds then removes and drives selection mode`() = runTest(dispatcher) {
        val vm = vm()
        val job = launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        vm.toggleSelect(1)
        vm.toggleSelect(3)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(setOf(1L, 3L), vm.uiState.value.selectedContactIds)
        assertTrue(vm.uiState.value.selectionMode)
        vm.toggleSelect(1)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(setOf(3L), vm.uiState.value.selectedContactIds)
        vm.clearSelection()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(emptySet<Long>(), vm.uiState.value.selectedContactIds)
        job.cancel()
    }

    @Test fun `planMove covers only non read-only raw contacts of the selection`() = runTest(dispatcher) {
        val vm = vm()
        val job = launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        vm.toggleSelect(2)
        vm.toggleSelect(3)
        dispatcher.scheduler.advanceUntilIdle()
        val plan = vm.planMove(googleTarget)
        assertEquals(listOf(30L), plan.sources.map { it.rawContactId })
        assertTrue(plan.losses.isEmpty())
        job.cancel()
    }

    @Test fun `moveSelectedTo moves non read-only raw ids and clears selection`() = runTest(dispatcher) {
        val writer = FakeWriter()
        val vm = vm(writer)
        val job = launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        vm.toggleSelect(1)
        vm.toggleSelect(3)
        dispatcher.scheduler.advanceUntilIdle()
        vm.moveSelectedTo(googleTarget)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(10L, 30L), writer.movedIds)
        assertEquals("com.google" to "b@gmail.com", writer.moveTarget)
        assertEquals(emptySet<Long>(), vm.uiState.value.selectedContactIds)
        job.cancel()
    }

    @Test fun `moveSelectedTo logs contact_move with count and capability`() = runTest(dispatcher) {
        val analytics = FakeAnalytics()
        val vm = vm(analytics = analytics)
        val job = launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        vm.toggleSelect(1)
        vm.toggleSelect(3)
        dispatcher.scheduler.advanceUntilIdle()
        vm.moveSelectedTo(googleTarget)
        dispatcher.scheduler.advanceUntilIdle()
        val move = analytics.events.filterIsInstance<AnalyticsEvent.ContactMove>().single()
        assertEquals(2, move.count)
        job.cancel()
    }

    @Test fun `copySelectedTo copies the whole selection including read-only raws`() = runTest(dispatcher) {
        val writer = FakeWriter()
        val analytics = FakeAnalytics()
        val vm = vm(writer, analytics = analytics)
        val job = launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        vm.toggleSelect(2)
        dispatcher.scheduler.advanceUntilIdle()
        vm.copySelectedTo(googleTarget)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(writer.copiedIds.isNotEmpty())
        assertTrue(writer.movedIds.isEmpty())
        val copy = analytics.events.filterIsInstance<AnalyticsEvent.ContactCopy>().single()
        assertEquals(writer.copiedIds.size, copy.count)
        job.cancel()
    }

    @Test fun `setQuery logs search with query length`() = runTest(dispatcher) {
        val analytics = FakeAnalytics()
        val vm = vm(analytics = analytics)
        vm.setQuery("andi")
        assertEquals(
            AnalyticsEvent.Search(4),
            analytics.events.filterIsInstance<AnalyticsEvent.Search>().single(),
        )
    }

    @Test fun `moveSelectedTo with only read-only raws emits message and moves nothing`() = runTest(dispatcher) {
        val writer = FakeWriter()
        val vm = vm(writer)
        val job = launch { vm.uiState.collect {} }
        val messages = mutableListOf<UiEvent>()
        val eventsJob = launch { vm.events.collect { messages += it } }
        dispatcher.scheduler.advanceUntilIdle()
        vm.toggleSelect(2)
        dispatcher.scheduler.advanceUntilIdle()
        vm.moveSelectedTo(googleTarget)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(writer.movedIds.isEmpty())
        assertNull(vm.batchProgress.value)
        assertEquals(1, messages.size)
        job.cancel()
        eventsJob.cancel()
    }

    @Test fun `duplicateCount counts groups and honors dismissed keys`() = runTest(dispatcher) {
        val vm = vm()
        val job = launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, vm.uiState.value.duplicateCount)
        contactsFlow.value = contactsFlow.value + contact(4, "Andi Second", raw(40, "com.google", "+62812111"))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.uiState.value.duplicateCount)
        dismissedKeys.value = setOf("1-4")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, vm.uiState.value.duplicateCount)
        job.cancel()
    }

    @Test fun `mergeSelected passes target and every other selected raw as source`() = runTest(dispatcher) {
        val writer = FakeWriter()
        val vm = vm(writer)
        val job = launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        vm.toggleSelect(1)
        vm.toggleSelect(3)
        dispatcher.scheduler.advanceUntilIdle()
        val target = contactsFlow.value
            .first { it.contactId == 1L }
            .rawContacts
            .single()
        vm.mergeSelected(target)
        dispatcher.scheduler.advanceUntilIdle()
        val (mergedTarget, sources) = writer.merged!!
        assertEquals(10L, mergedTarget.rawContactId)
        // Read-only raws stay in the sources: the writer links instead of deleting them.
        assertEquals(listOf(30L, 31L), sources.map { it.rawContactId })
        assertEquals(emptySet<Long>(), vm.uiState.value.selectedContactIds)
        job.cancel()
    }

    @Test fun `deleteSelected deletes non read-only raw ids`() = runTest(dispatcher) {
        val writer = FakeWriter()
        val vm = vm(writer)
        val job = launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        vm.toggleSelect(1)
        vm.toggleSelect(2)
        vm.toggleSelect(3)
        dispatcher.scheduler.advanceUntilIdle()
        vm.deleteSelected()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(listOf(10L, 30L)), writer.deletedIds)
        assertEquals(emptySet<Long>(), vm.uiState.value.selectedContactIds)
        job.cancel()
    }

    @Test fun `selectionAccountBreakdown groups selection raws by account, deletable first`() = runTest(dispatcher) {
        val vm = vm()
        val job = launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        vm.toggleSelect(1)
        vm.toggleSelect(3)
        dispatcher.scheduler.advanceUntilIdle()
        val breakdown = vm.selectionAccountBreakdown()
        assertEquals(
            listOf(
                SelectionAccountEntry("com.google", "acc", AccountCapability.FULL_CRUD, 2),
                SelectionAccountEntry("com.whatsapp", "acc", AccountCapability.READ_ONLY, 1),
            ),
            breakdown,
        )
        job.cancel()
    }

    @Test fun `deleteSelectedFrom deletes only raws of the chosen accounts and clears selection`() = runTest(dispatcher) {
        val writer = FakeWriter()
        val vm = vm(writer)
        val job = launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        vm.toggleSelect(1)
        vm.toggleSelect(2)
        vm.toggleSelect(3)
        dispatcher.scheduler.advanceUntilIdle()
        vm.deleteSelectedFrom(setOf("com.google/acc"))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(listOf(10L, 30L)), writer.deletedIds)
        assertEquals(emptySet<Long>(), vm.uiState.value.selectedContactIds)
        job.cancel()
    }

    @Test fun `deleteSelectedFrom never deletes read-only raws even when their account is chosen`() = runTest(dispatcher) {
        val writer = FakeWriter()
        val vm = vm(writer)
        val job = launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        vm.toggleSelect(3)
        dispatcher.scheduler.advanceUntilIdle()
        vm.deleteSelectedFrom(setOf("com.whatsapp/acc"))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(emptyList<List<Long>>(), writer.deletedIds)
        job.cancel()
    }
}
