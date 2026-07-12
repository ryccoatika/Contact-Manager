package com.ryccoatika.contactmanager.ui.accounts

import com.ryccoatika.contactmanager.data.AccountsSource
import com.ryccoatika.contactmanager.data.BatchOperationManager
import com.ryccoatika.contactmanager.data.ContactOpResult
import com.ryccoatika.contactmanager.data.ContactsSource
import com.ryccoatika.contactmanager.data.ContactsWriter
import com.ryccoatika.contactmanager.data.EditableContact
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.domain.model.RawContact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        rawContactId = id, accountType = accType, accountName = accName,
    )

    private val contactsFlow = MutableStateFlow(listOf(
        Contact(1, "Andi", rawContacts = listOf(raw(10, "com.google", "a@gmail.com"))),
        Contact(2, "Budi", rawContacts = listOf(
            raw(20, "com.whatsapp", "WhatsApp"),
            raw(21, "com.google", "a@gmail.com"),
        )),
    ))

    private val fakeContacts = object : ContactsSource {
        override fun observeContacts(): Flow<List<Contact>> = contactsFlow
    }
    private val fakeAccounts = object : AccountsSource {
        override suspend fun getAccounts() = listOf(googleAccount, whatsappAccount, deviceAccount)
    }

    private class FakeWriter : ContactsWriter {
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
    }

    private fun vm(writer: FakeWriter = FakeWriter()) = AccountsViewModel(
        accountsSource = fakeAccounts,
        contactsSource = fakeContacts,
        batchManager = BatchOperationManager(writer, CoroutineScope(SupervisorJob() + dispatcher)),
    )

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `loads accounts with capabilities`() = runTest(dispatcher) {
        val vm = vm()
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.loading)
        assertEquals(
            listOf(googleAccount, whatsappAccount, deviceAccount),
            vm.uiState.value.accounts,
        )
    }

    @Test fun `moveAllContacts moves every raw contact of the source account only`() = runTest(dispatcher) {
        val writer = FakeWriter()
        val vm = vm(writer)
        dispatcher.scheduler.advanceUntilIdle()
        vm.moveAllContacts(source = googleAccount, target = deviceAccount)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(10L, 21L), writer.movedIds)
        assertEquals(null to null, writer.moveTarget)
    }

    @Test fun `moveAllContacts with empty source moves nothing`() = runTest(dispatcher) {
        val writer = FakeWriter()
        val vm = vm(writer)
        dispatcher.scheduler.advanceUntilIdle()
        vm.moveAllContacts(source = deviceAccount, target = googleAccount)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(writer.movedIds.isEmpty())
    }
}
