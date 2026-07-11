package com.ryccoatika.contactmanager.ui.home

import com.ryccoatika.contactmanager.data.AccountsSource
import com.ryccoatika.contactmanager.data.ContactsSource
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private fun contact(id: Long, name: String, accType: String?, phone: String? = null) = Contact(
        contactId = id, displayName = name,
        rawContacts = listOf(RawContact(
            rawContactId = id * 10, accountType = accType, accountName = "acc",
            phones = phone?.let { listOf(LabeledValue(1, it, null)) } ?: emptyList(),
        )),
    )

    private val contactsFlow = MutableStateFlow(listOf(
        contact(1, "Andi Wijaya", "com.google", "+62812111"),
        contact(2, "Budi Santoso", "com.whatsapp"),
    ))

    private val fakeContacts = object : ContactsSource {
        override fun observeContacts(): Flow<List<Contact>> = contactsFlow
    }
    private val fakeAccounts = object : AccountsSource {
        override suspend fun getAccounts() = listOf(
            ContactAccount("acc", "com.google", AccountCapability.FULL_CRUD, 1),
            ContactAccount("acc", "com.whatsapp", AccountCapability.READ_ONLY, 1),
        )
    }

    private fun vm() = HomeViewModel(fakeContacts, fakeAccounts, dispatcher)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `emits all contacts and accounts`() = runTest(dispatcher) {
        val vm = vm()
        val job = launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, vm.uiState.value.contacts.size)
        assertEquals(2, vm.uiState.value.accounts.size)
        job.cancel()
    }

    @Test fun `account filter keeps only contacts having raw contact in account`() = runTest(dispatcher) {
        val vm = vm()
        val job = launch { vm.uiState.collect {} }
        vm.selectAccount("com.whatsapp/acc")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("Budi Santoso"), vm.uiState.value.contacts.map { it.displayName })
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
}
