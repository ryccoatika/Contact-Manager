package com.ryccoatika.contactmanager.ui.editor

import androidx.lifecycle.SavedStateHandle
import com.ryccoatika.contactmanager.data.AccountsSource
import com.ryccoatika.contactmanager.data.ContactOpResult
import com.ryccoatika.contactmanager.data.ContactsSource
import com.ryccoatika.contactmanager.data.ContactsWriter
import com.ryccoatika.contactmanager.data.EditableContact
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.domain.model.LabeledValue
import com.ryccoatika.contactmanager.domain.model.RawContact
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val googleAccount = ContactAccount("a@gmail.com", "com.google", AccountCapability.FULL_CRUD, 5)
    private val whatsappAccount = ContactAccount("WhatsApp", "com.whatsapp", AccountCapability.READ_ONLY, 3)

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
    ))

    private val fakeContacts = object : ContactsSource {
        override fun observeContacts(): Flow<List<Contact>> = contactsFlow
    }
    private val fakeAccounts = object : AccountsSource {
        override suspend fun getAccounts() = listOf(googleAccount, whatsappAccount)
    }

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
    }

    private fun newVm(writer: ContactsWriter = FakeWriter()) = EditorViewModel(
        savedStateHandle = SavedStateHandle(),
        contactsSource = fakeContacts,
        accountsSource = fakeAccounts,
        writer = writer,
    )

    private fun editVm(rawContactId: Long = 10, writer: ContactsWriter = FakeWriter()) = EditorViewModel(
        savedStateHandle = SavedStateHandle(mapOf("rawContactId" to rawContactId)),
        contactsSource = fakeContacts,
        accountsSource = fakeAccounts,
        writer = writer,
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
}
