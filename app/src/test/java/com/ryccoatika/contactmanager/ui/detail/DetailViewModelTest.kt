package com.ryccoatika.contactmanager.ui.detail

import androidx.lifecycle.SavedStateHandle
import com.ryccoatika.contactmanager.data.ContactOpResult
import com.ryccoatika.contactmanager.data.ContactsSource
import com.ryccoatika.contactmanager.data.ContactsWriter
import com.ryccoatika.contactmanager.data.EditableContact
import com.ryccoatika.contactmanager.domain.model.Contact
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private fun contact(id: Long, vararg rawIds: Long) = Contact(
        contactId = id,
        displayName = "Contact $id",
        rawContacts = rawIds.map { rawId ->
            RawContact(rawContactId = rawId, accountType = "com.google", accountName = "acc")
        },
    )

    private val contactsFlow = MutableStateFlow(listOf(contact(1, 10), contact(2, 20, 21)))

    private val fakeContacts = object : ContactsSource {
        override fun observeContacts(): Flow<List<Contact>> = contactsFlow

        override suspend fun snapshot(): List<Contact> = contactsFlow.value
    }

    private class FakeWriter(
        var deleteResult: ContactOpResult = ContactOpResult.Success,
    ) : ContactsWriter {
        val deletedIds = mutableListOf<List<Long>>()

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
            return deleteResult
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
        ): ContactOpResult = ContactOpResult.Success

        override suspend fun linkContacts(rawContactIds: List<Long>): ContactOpResult =
            ContactOpResult.Success

        override suspend fun keepSeparate(rawContactIds: List<Long>): ContactOpResult =
            ContactOpResult.Success

        override suspend fun mergeContacts(
            target: RawContact,
            sources: List<RawContact>,
        ): ContactOpResult = ContactOpResult.Success
    }

    private fun vm(contactId: Long, writer: ContactsWriter = FakeWriter()) = DetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("contactId" to contactId)),
        contactsSource = fakeContacts,
        writer = writer,
    )

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun `uiState resolves contact matching route id`() = runTest(dispatcher) {
        val vm = vm(contactId = 2)
        val job = launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            2L,
            vm.uiState.value.contact
                ?.contactId,
        )
        assertEquals(
            2,
            vm.uiState.value.contact
                ?.rawContacts
                ?.size,
        )
        job.cancel()
    }

    @Test fun `uiState reports missing contact after load for unknown id`() = runTest(dispatcher) {
        val vm = vm(contactId = 99)
        val job = launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.loading)
        assertNull(vm.uiState.value.contact)
        job.cancel()
    }

    @Test fun `deleting last raw contact emits NavigateBack`() = runTest(dispatcher) {
        val writer = FakeWriter()
        val vm = vm(contactId = 1, writer = writer)
        val events = mutableListOf<DetailEvent>()
        val stateJob = launch { vm.uiState.collect {} }
        val eventJob = launch { vm.events.collect { events += it } }
        dispatcher.scheduler.advanceUntilIdle()
        vm.deleteRawContact(10)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(listOf(10L)), writer.deletedIds)
        assertEquals(listOf<DetailEvent>(DetailEvent.NavigateBack), events)
        stateJob.cancel()
        eventJob.cancel()
    }

    @Test fun `deleting one of several raw contacts emits no navigation`() = runTest(dispatcher) {
        val writer = FakeWriter()
        val vm = vm(contactId = 2, writer = writer)
        val events = mutableListOf<DetailEvent>()
        val stateJob = launch { vm.uiState.collect {} }
        val eventJob = launch { vm.events.collect { events += it } }
        dispatcher.scheduler.advanceUntilIdle()
        vm.deleteRawContact(20)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(emptyList<DetailEvent>(), events)
        stateJob.cancel()
        eventJob.cancel()
    }

    @Test fun `delete failure emits ShowMessage`() = runTest(dispatcher) {
        val writer = FakeWriter(deleteResult = ContactOpResult.Failure("boom"))
        val vm = vm(contactId = 1, writer = writer)
        val events = mutableListOf<DetailEvent>()
        val stateJob = launch { vm.uiState.collect {} }
        val eventJob = launch { vm.events.collect { events += it } }
        dispatcher.scheduler.advanceUntilIdle()
        vm.deleteRawContact(10)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf<DetailEvent>(DetailEvent.ShowMessage("boom")), events)
        stateJob.cancel()
        eventJob.cancel()
    }
}
