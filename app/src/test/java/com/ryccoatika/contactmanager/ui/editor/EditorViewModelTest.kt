package com.ryccoatika.contactmanager.ui.editor

import androidx.lifecycle.SavedStateHandle
import com.ryccoatika.contactmanager.data.AccountsSource
import com.ryccoatika.contactmanager.data.ContactOpResult
import com.ryccoatika.contactmanager.data.ContactsSource
import com.ryccoatika.contactmanager.data.ContactsWriter
import com.ryccoatika.contactmanager.data.EditableContact
import com.ryccoatika.contactmanager.data.sim.FakeSimContactSource
import com.ryccoatika.contactmanager.data.sim.InMemorySimCapabilityCache
import com.ryccoatika.contactmanager.data.sim.SimRepository
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.domain.model.LabeledValue
import com.ryccoatika.contactmanager.domain.model.RawContact
import com.ryccoatika.contactmanager.domain.model.SimCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val googleAccount = ContactAccount("a@gmail.com", "com.google", AccountCapability.FULL_CRUD, 5)
    private val whatsappAccount = ContactAccount("WhatsApp", "com.whatsapp", AccountCapability.READ_ONLY, 3, writable = false)
    private val simAccount = ContactAccount("SIM 1 · Telkomsel", "icc/1", AccountCapability.SIM, 2)
    private val readOnlySimAccount = ContactAccount("SIM 2 · XL", "icc/2", AccountCapability.SIM, 1, writable = false)

    private val contactsFlow = MutableStateFlow(listOf(
        Contact(
            contactId = 1, displayName = "Budi Santoso",
            rawContacts = listOf(RawContact(
                rawContactId = 10, accountType = "com.google", accountName = "a@gmail.com",
                givenName = "Budi", familyName = "Santoso",
                phones = listOf(LabeledValue(1, "+62812111", "Mobile")),
                emails = listOf(LabeledValue(2, "budi@x.com", "Home")),
                organization = "PT Maju",
                note = "VIP",
            )),
        ),
        Contact(
            contactId = -1_000_000, displayName = "Sim Budi",
            rawContacts = listOf(RawContact(
                rawContactId = -1_000_000, accountType = "icc/1", accountName = "SIM 1 · Telkomsel",
                phones = listOf(LabeledValue(-1, "0812", "SIM")),
            )),
        ),
    ))

    private val fakeContacts = object : ContactsSource {
        override fun observeContacts(): Flow<List<Contact>> = contactsFlow
    }
    private var accountsList = listOf(googleAccount, whatsappAccount)
    private val fakeAccounts = object : AccountsSource {
        override suspend fun getAccounts() = accountsList
    }

    /** Probe reports max name length 12 for subscription 1. */
    private val simRepository = SimRepository(
        FakeSimContactSource(
            capsBySub = mapOf(
                1 to SimCapabilities(canRead = true, canWrite = true, maxNameLength = 12),
                2 to SimCapabilities(canRead = true, canWrite = false),
            ),
        ),
        InMemorySimCapabilityCache(),
    )

    private class FakeWriter(
        var result: ContactOpResult = ContactOpResult.Success,
    ) : ContactsWriter {
        var created: Triple<String?, String?, EditableContact>? = null
        var updated: Pair<Long, EditableContact>? = null

        override suspend fun createContact(
            accountType: String?,
            accountName: String?,
            contact: EditableContact,
        ): ContactOpResult {
            created = Triple(accountType, accountName, contact)
            return result
        }

        override suspend fun updateRawContact(
            rawContactId: Long,
            contact: EditableContact,
        ): ContactOpResult {
            updated = rawContactId to contact
            return result
        }

        override suspend fun deleteRawContacts(rawContactIds: List<Long>): ContactOpResult = result

        override suspend fun copyRawContact(
            rawContactId: Long,
            targetType: String?,
            targetName: String?,
        ): ContactOpResult = result

        override suspend fun moveRawContacts(
            rawContactIds: List<Long>,
            targetType: String?,
            targetName: String?,
            onProgress: (done: Int, total: Int) -> Unit,
        ): ContactOpResult = result
    }

    private fun newVm(writer: ContactsWriter = FakeWriter()) = EditorViewModel(
        savedStateHandle = SavedStateHandle(),
        contactsSource = fakeContacts,
        accountsSource = fakeAccounts,
        writer = writer,
        simRepository = simRepository,
    )

    private fun editVm(rawContactId: Long = 10, writer: ContactsWriter = FakeWriter()) = EditorViewModel(
        savedStateHandle = SavedStateHandle(mapOf("rawContactId" to rawContactId)),
        contactsSource = fakeContacts,
        accountsSource = fakeAccounts,
        writer = writer,
        simRepository = simRepository,
    )

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `create mode offers only writable accounts and defaults to first`() = runTest(dispatcher) {
        val vm = newVm()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(googleAccount), vm.uiState.value.accounts)
        assertEquals(googleAccount, vm.uiState.value.selectedAccount)
        assertFalse(vm.uiState.value.isEdit)
    }

    @Test fun `edit mode prefills fields from raw contact and fixes account`() = runTest(dispatcher) {
        val vm = editVm()
        dispatcher.scheduler.advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state.isEdit)
        assertEquals("Budi Santoso", state.name)
        assertEquals(listOf("+62812111"), state.phones)
        assertEquals(listOf("budi@x.com"), state.emails)
        assertEquals("PT Maju", state.organization)
        assertEquals("VIP", state.note)
        assertEquals("a@gmail.com (com.google)", state.fixedAccountLabel)
    }

    @Test fun `save disabled with blank name and no phones or emails`() = runTest(dispatcher) {
        val vm = newVm()
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.canSave)
        vm.setName("  ")
        assertFalse(vm.uiState.value.canSave)
        vm.setPhone(0, "0812")
        assertTrue(vm.uiState.value.canSave)
        vm.setPhone(0, "")
        vm.setEmail(0, "a@b.c")
        assertTrue(vm.uiState.value.canSave)
        vm.setEmail(0, "")
        vm.setName("Budi")
        assertTrue(vm.uiState.value.canSave)
    }

    @Test fun `save does nothing when invalid`() = runTest(dispatcher) {
        val writer = FakeWriter()
        val vm = newVm(writer)
        dispatcher.scheduler.advanceUntilIdle()
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(writer.created)
    }

    @Test fun `save in create mode passes selected account and trimmed non-blank fields`() = runTest(dispatcher) {
        val writer = FakeWriter()
        val vm = newVm(writer)
        dispatcher.scheduler.advanceUntilIdle()
        vm.setName(" Budi ")
        vm.setPhone(0, "0812")
        vm.addPhone()
        vm.setPhone(1, "  ")
        vm.setOrganization("  ")
        vm.setNote(" catatan ")
        val events = mutableListOf<EditorEvent>()
        val job = launch { vm.events.collect { events += it } }
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()
        val (accountType, accountName, contact) = writer.created!!
        assertEquals("com.google", accountType)
        assertEquals("a@gmail.com", accountName)
        assertEquals("Budi", contact.displayName)
        assertEquals(listOf("0812" to null), contact.phones)
        assertEquals(emptyList<Pair<String, String?>>(), contact.emails)
        assertNull(contact.organization)
        assertEquals("catatan", contact.note)
        assertEquals(listOf<EditorEvent>(EditorEvent.Saved), events)
        job.cancel()
    }

    @Test fun `save in edit mode updates the raw contact`() = runTest(dispatcher) {
        val writer = FakeWriter()
        val vm = editVm(writer = writer)
        dispatcher.scheduler.advanceUntilIdle()
        vm.setName("Budi S")
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(10L, writer.updated!!.first)
        assertEquals("Budi S", writer.updated!!.second.displayName)
        assertNull(writer.created)
    }

    @Test fun `failed save clears saving flag and emits message`() = runTest(dispatcher) {
        val writer = FakeWriter(result = ContactOpResult.Failure("nope"))
        val vm = newVm(writer)
        dispatcher.scheduler.advanceUntilIdle()
        vm.setName("Budi")
        val events = mutableListOf<EditorEvent>()
        val job = launch { vm.events.collect { events += it } }
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.saving)
        assertEquals(listOf<EditorEvent>(EditorEvent.ShowMessage("nope")), events)
        job.cancel()
    }

    @Test fun `add and remove phone rows keep at least one row`() = runTest(dispatcher) {
        val vm = newVm()
        dispatcher.scheduler.advanceUntilIdle()
        vm.addPhone()
        assertEquals(2, vm.uiState.value.phones.size)
        vm.removePhone(1)
        vm.removePhone(0)
        assertEquals(listOf(""), vm.uiState.value.phones)
    }

    // --- SIM mode ---------------------------------------------------------

    @Test fun `create mode offers writable sim accounts but not read-only ones`() = runTest(dispatcher) {
        accountsList = listOf(googleAccount, whatsappAccount, simAccount, readOnlySimAccount)
        val vm = newVm()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(googleAccount, simAccount), vm.uiState.value.accounts)
        assertFalse(vm.uiState.value.simMode)
    }

    @Test fun `selecting sim account enters sim mode with probed max name length`() = runTest(dispatcher) {
        accountsList = listOf(googleAccount, simAccount)
        val vm = newVm()
        dispatcher.scheduler.advanceUntilIdle()
        vm.selectAccount(simAccount)
        assertTrue(vm.uiState.value.simMode)
        assertEquals(12, vm.uiState.value.simMaxNameLength)
        vm.selectAccount(googleAccount)
        assertFalse(vm.uiState.value.simMode)
    }

    @Test fun `sim mode blocks save while name exceeds max length`() = runTest(dispatcher) {
        accountsList = listOf(simAccount)
        val vm = newVm()
        dispatcher.scheduler.advanceUntilIdle()
        vm.setName("A".repeat(13))
        vm.setPhone(0, "0812")
        assertTrue(vm.uiState.value.simNameTooLong)
        assertFalse(vm.uiState.value.canSave)
        vm.setName("A".repeat(12))
        assertTrue(vm.uiState.value.canSave)
    }

    @Test fun `sim mode save shows inline error for invalid number and does not write`() = runTest(dispatcher) {
        accountsList = listOf(simAccount)
        val writer = FakeWriter()
        val vm = newVm(writer)
        dispatcher.scheduler.advanceUntilIdle()
        vm.setName("Budi")
        vm.setPhone(0, "not a number")
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(writer.created)
        assertNotNull(vm.uiState.value.simError)
        assertFalse(vm.uiState.value.saving)
        vm.setPhone(0, "0812")
        assertNull(vm.uiState.value.simError)
    }

    @Test fun `sim mode save down-converts to name and single normalized phone`() = runTest(dispatcher) {
        accountsList = listOf(simAccount)
        val writer = FakeWriter()
        val vm = newVm(writer)
        dispatcher.scheduler.advanceUntilIdle()
        vm.setName("Budi")
        vm.setPhone(0, "+62 812-111")
        vm.setOrganization("ignored")
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()
        val (accountType, accountName, contact) = writer.created!!
        assertEquals("icc/1", accountType)
        assertEquals("SIM 1 · Telkomsel", accountName)
        assertEquals("Budi", contact.displayName)
        assertEquals(listOf("+62812111" to null), contact.phones)
        assertEquals(emptyList<Pair<String, String?>>(), contact.emails)
        assertNull(contact.organization)
        assertNull(contact.note)
    }

    @Test fun `edit mode for sim raw contact enters sim mode`() = runTest(dispatcher) {
        val vm = editVm(rawContactId = -1_000_000)
        dispatcher.scheduler.advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state.isEdit)
        assertTrue(state.simMode)
        assertEquals(12, state.simMaxNameLength)
        assertEquals("Sim Budi", state.name)
        assertEquals(listOf("0812"), state.phones)
    }
}
