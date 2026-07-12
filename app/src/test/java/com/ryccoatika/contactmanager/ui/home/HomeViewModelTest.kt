package com.ryccoatika.contactmanager.ui.home

import com.ryccoatika.contactmanager.data.AccountsSource
import com.ryccoatika.contactmanager.data.BatchOperationManager
import com.ryccoatika.contactmanager.data.ContactOpResult
import com.ryccoatika.contactmanager.data.ContactsSource
import com.ryccoatika.contactmanager.data.ContactsWriter
import com.ryccoatika.contactmanager.data.EditableContact
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.domain.model.LabeledValue
import com.ryccoatika.contactmanager.domain.model.RawContact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
        rawContactId = id, accountType = accType, accountName = "acc",
        phones = phone?.let { listOf(LabeledValue(1, it, null)) } ?: emptyList(),
    )

    private fun contact(id: Long, name: String, vararg raws: RawContact) = Contact(
        contactId = id, displayName = name, rawContacts = raws.toList(),
    )

    private val contactsFlow = MutableStateFlow(listOf(
        contact(1, "Andi Wijaya", raw(10, "com.google", "+62812111")),
        contact(2, "Budi Santoso", raw(20, "com.whatsapp")),
        contact(3, "Citra Lestari", raw(30, "com.google", "+62899000"), raw(31, "com.whatsapp")),
    ))

    private val fakeContacts = object : ContactsSource {
        override fun observeContacts(): Flow<List<Contact>> = contactsFlow
    }
    private val fakeAccounts = object : AccountsSource {
        override suspend fun getAccounts() = listOf(
            ContactAccount("acc", "com.google", AccountCapability.FULL_CRUD, 2),
            ContactAccount("acc", "com.whatsapp", AccountCapability.READ_ONLY, 2),
        )
    }

    private class FakeWriter : ContactsWriter {
        val deletedIds = mutableListOf<List<Long>>()
        val movedIds = mutableListOf<Long>()
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
    }

    private val googleTarget = ContactAccount("b@gmail.com", "com.google", AccountCapability.FULL_CRUD, 0)

    private fun vm(writer: FakeWriter = FakeWriter()) = HomeViewModel(
        contactsSource = fakeContacts,
        accountsSource = fakeAccounts,
        writer = writer,
        batchManager = BatchOperationManager(writer, CoroutineScope(SupervisorJob() + dispatcher)),
        defaultDispatcher = dispatcher,
    )

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

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
            vm.uiState.value.contacts.map { it.displayName },
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
        assertEquals(listOf("Andi Wijaya"), vm.uiState.value.contacts.map { it.displayName })
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

    @Test fun `moveSelectedTo with only read-only raws emits message and moves nothing`() = runTest(dispatcher) {
        val writer = FakeWriter()
        val vm = vm(writer)
        val job = launch { vm.uiState.collect {} }
        val messages = mutableListOf<String>()
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
}
