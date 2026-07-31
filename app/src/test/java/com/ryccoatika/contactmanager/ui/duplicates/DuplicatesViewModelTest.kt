package com.ryccoatika.contactmanager.ui.duplicates

import com.ryccoatika.contactmanager.data.ContactOpResult
import com.ryccoatika.contactmanager.data.ContactsSource
import com.ryccoatika.contactmanager.data.ContactsWriter
import com.ryccoatika.contactmanager.data.DuplicatePrefs
import com.ryccoatika.contactmanager.data.EditableContact
import com.ryccoatika.contactmanager.data.FakeStringProvider
import com.ryccoatika.contactmanager.domain.DuplicateFinder
import com.ryccoatika.contactmanager.domain.MatchConfidence
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.LabeledValue
import com.ryccoatika.contactmanager.domain.model.RawContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DuplicatesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private fun contact(id: Long, name: String, phone: String? = null, accType: String? = "com.google") = Contact(
        contactId = id, displayName = name,
        rawContacts = listOf(
            RawContact(
                rawContactId = id * 10, accountType = accType, accountName = "acc",
                phones = phone?.let { listOf(LabeledValue(id, it, null)) } ?: emptyList(),
            ),
        ),
    )

    // 1+2 share a phone (HIGH), 3+4 match by name only (MEDIUM).
    private val contactsFlow = MutableStateFlow(listOf(
        contact(1, "Andi Wijaya", "+62812345678"),
        contact(2, "Andi W", "0812345678", accType = "com.whatsapp"),
        contact(3, "Budi Santoso"),
        contact(4, "Santoso Budi"),
    ))

    private val fakeContacts = object : ContactsSource {
        override fun observeContacts(): Flow<List<Contact>> = contactsFlow
    }

    private class FakePrefs : DuplicatePrefs {
        val dismissed = MutableStateFlow<Set<String>>(emptySet())
        override fun observeDismissedKeys(): Flow<Set<String>> = dismissed
        override suspend fun dismissedKeys(): Set<String> = dismissed.value
        override suspend fun dismiss(key: String) {
            dismissed.update { it + key }
        }
    }

    private class FakeWriter : ContactsWriter {
        var linked: List<Long>? = null
        var separated: List<Long>? = null
        var merged: Pair<RawContact, List<RawContact>>? = null
        var separateResult: ContactOpResult = ContactOpResult.Success

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
        ): ContactOpResult = ContactOpResult.Success

        override suspend fun linkContacts(rawContactIds: List<Long>): ContactOpResult {
            linked = rawContactIds
            return ContactOpResult.Success
        }

        override suspend fun keepSeparate(rawContactIds: List<Long>): ContactOpResult {
            separated = rawContactIds
            return separateResult
        }

        override suspend fun mergeContacts(
            target: RawContact,
            sources: List<RawContact>,
        ): ContactOpResult {
            merged = target to sources
            return ContactOpResult.Success
        }
    }

    private fun vm(writer: FakeWriter = FakeWriter(), prefs: FakePrefs = FakePrefs()) =
        DuplicatesViewModel(
            contactsSource = fakeContacts,
            writer = writer,
            prefs = prefs,
            strings = FakeStringProvider(),
            defaultDispatcher = dispatcher,
        )

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `groups flow through sorted HIGH first`() = runTest(dispatcher) {
        val vm = vm()
        val job = launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        val groups = vm.uiState.value.groups
        assertEquals(2, groups.size)
        assertEquals(MatchConfidence.HIGH, groups[0].confidence)
        assertEquals(listOf(1L, 2L), groups[0].contacts.map { it.contactId })
        assertEquals(MatchConfidence.MEDIUM, groups[1].confidence)
        assertEquals(listOf(3L, 4L), groups[1].contacts.map { it.contactId })
        job.cancel()
    }

    @Test fun `link passes every raw id of the group`() = runTest(dispatcher) {
        val writer = FakeWriter()
        val vm = vm(writer)
        val job = launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        vm.link(vm.uiState.value.groups[0])
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(10L, 20L), writer.linked)
        job.cancel()
    }

    @Test fun `merge passes the chosen target and all other raws as sources`() = runTest(dispatcher) {
        val writer = FakeWriter()
        val vm = vm(writer)
        val job = launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        val group = vm.uiState.value.groups[0]
        val target = group.contacts[0].rawContacts.single() // raw 10
        vm.merge(group, target)
        dispatcher.scheduler.advanceUntilIdle()
        val (mergedTarget, sources) = writer.merged!!
        assertEquals(10L, mergedTarget.rawContactId)
        assertEquals(listOf(20L), sources.map { it.rawContactId })
        job.cancel()
    }

    @Test fun `dismiss persists the key, removes the group and keeps others`() = runTest(dispatcher) {
        val writer = FakeWriter()
        val prefs = FakePrefs()
        val vm = vm(writer, prefs)
        val job = launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        val group = vm.uiState.value.groups[0]
        vm.dismiss(group)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(setOf(DuplicateFinder.groupKey(group)), prefs.dismissed.value)
        assertEquals(listOf(listOf(3L, 4L)), vm.uiState.value.groups.map { g -> g.contacts.map { it.contactId } })
        assertEquals(listOf(10L, 20L), writer.separated)
        job.cancel()
    }

    @Test fun `dismiss still hides the group when keepSeparate fails`() = runTest(dispatcher) {
        val writer = FakeWriter().apply { separateResult = ContactOpResult.Failure("nope") }
        val prefs = FakePrefs()
        val vm = vm(writer, prefs)
        val job = launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        vm.dismiss(vm.uiState.value.groups[0])
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.uiState.value.groups.size)
        assertNotNull(prefs.dismissed.value.firstOrNull())
        assertTrue(vm.uiState.value.groups.none { it.confidence == MatchConfidence.HIGH })
        job.cancel()
    }
}
