package com.ryccoatika.contactmanager.ui.duplicates

import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.data.ContactOpResult
import com.ryccoatika.contactmanager.data.ContactsSource
import com.ryccoatika.contactmanager.data.ContactsWriter
import com.ryccoatika.contactmanager.data.DuplicatePrefs
import com.ryccoatika.contactmanager.data.EditableContact
import com.ryccoatika.contactmanager.data.FakeStringProvider
import com.ryccoatika.contactmanager.domain.DuplicateAction
import com.ryccoatika.contactmanager.domain.DuplicateFinder
import com.ryccoatika.contactmanager.domain.MatchConfidence
import com.ryccoatika.contactmanager.domain.analytics.AnalyticsEvent
import com.ryccoatika.contactmanager.domain.analytics.FakeAnalytics
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.LabeledValue
import com.ryccoatika.contactmanager.domain.model.RawContact
import com.ryccoatika.contactmanager.ui.common.UiEvent
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DuplicatesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private fun contact(id: Long, name: String, phone: String? = null, accType: String? = "com.google") = Contact(
        contactId = id,
        displayName = name,
        rawContacts = listOf(
            RawContact(
                rawContactId = id * 10,
                accountType = accType,
                accountName = "acc",
                phones = phone?.let { listOf(LabeledValue(id, it, null)) } ?: emptyList(),
            ),
        ),
    )

    // 1+2 share a phone (HIGH), 3+4 match by name only (MEDIUM).
    private val contactsFlow = MutableStateFlow(
        listOf(
            contact(1, "Andi Wijaya", "+62812345678"),
            contact(2, "Andi W", "0812345678", accType = "com.whatsapp"),
            contact(3, "Budi Santoso"),
            contact(4, "Santoso Budi"),
        ),
    )

    private val fakeContacts = object : ContactsSource {
        override fun observeContacts(): Flow<List<Contact>> = contactsFlow

        override suspend fun snapshot(): List<Contact> = contactsFlow.value
    }

    private class FakePrefs(
        explained: Set<DuplicateAction> = emptySet(),
    ) : DuplicatePrefs {
        val dismissed = MutableStateFlow<Set<String>>(emptySet())
        val explained = explained.toMutableSet()

        override fun observeDismissedKeys(): Flow<Set<String>> = dismissed

        override suspend fun dismissedKeys(): Set<String> = dismissed.value

        override suspend fun dismiss(key: String) {
            dismissed.update { it + key }
        }

        override suspend fun restore(key: String) {
            dismissed.update { it - key }
        }

        override suspend fun isExplained(action: DuplicateAction): Boolean = action in explained

        override suspend fun markExplained(action: DuplicateAction) {
            explained += action
        }
    }

    private class FakeWriter : ContactsWriter {
        var linked: List<Long>? = null
        var separated: List<List<Long>>? = null
        var reset: List<List<Long>>? = null
        var merged: Pair<RawContact, List<RawContact>>? = null
        var separateResult: ContactOpResult = ContactOpResult.Success
        var resetResult: ContactOpResult = ContactOpResult.Success
        var linkResult: ContactOpResult = ContactOpResult.Success

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

        override suspend fun copyRawContacts(
            rawContactIds: List<Long>,
            targetType: String?,
            targetName: String?,
            onProgress: (done: Int, total: Int) -> Unit,
        ): ContactOpResult = ContactOpResult.Success

        override suspend fun linkContacts(rawContactIds: List<Long>): ContactOpResult {
            linked = rawContactIds
            return linkResult
        }

        override suspend fun keepSeparate(contacts: List<List<Long>>): ContactOpResult {
            separated = contacts
            return separateResult
        }

        override suspend fun resetAggregation(contacts: List<List<Long>>): ContactOpResult {
            reset = contacts
            return resetResult
        }

        override suspend fun mergeContacts(
            target: RawContact,
            sources: List<RawContact>,
        ): ContactOpResult {
            merged = target to sources
            return ContactOpResult.Success
        }
    }

    private fun explainedPrefs() = FakePrefs(setOf(DuplicateAction.LINK, DuplicateAction.DISMISS))

    private fun vm(
        writer: FakeWriter = FakeWriter(),
        prefs: FakePrefs = FakePrefs(),
        analytics: FakeAnalytics = FakeAnalytics(),
    ) =
        DuplicatesViewModel(
            contactsSource = fakeContacts,
            writer = writer,
            prefs = prefs,
            strings = FakeStringProvider(),
            analytics = analytics,
            defaultDispatcher = dispatcher,
        )

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

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
        val vm = vm(writer, explainedPrefs())
        val job = launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        vm.link(vm.uiState.value.groups[0])
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(10L, 20L), writer.linked)
        job.cancel()
    }

    @Test fun `link and dismiss log analytics events`() = runTest(dispatcher) {
        val analytics = FakeAnalytics()
        val vm = vm(prefs = explainedPrefs(), analytics = analytics)
        val job = launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        val group = vm.uiState.value.groups[0]
        vm.link(group)
        dispatcher.scheduler.advanceUntilIdle()
        vm.dismiss(group)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(analytics.events.any { it is AnalyticsEvent.ContactsLink })
        assertTrue(analytics.events.any { it is AnalyticsEvent.DuplicateDismiss })
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
        val prefs = explainedPrefs()
        val vm = vm(writer, prefs)
        val job = launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        val group = vm.uiState.value.groups[0]
        vm.dismiss(group)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(setOf(DuplicateFinder.groupKey(group)), prefs.dismissed.value)
        assertEquals(
            listOf(listOf(3L, 4L)),
            vm.uiState.value.groups
                .map { g -> g.contacts.map { it.contactId } },
        )
        assertEquals(listOf(listOf(10L), listOf(20L)), writer.separated)
        job.cancel()
    }

    @Test fun `dismiss still hides the group when keepSeparate fails`() = runTest(dispatcher) {
        val writer = FakeWriter().apply { separateResult = ContactOpResult.Failure("nope") }
        val prefs = explainedPrefs()
        val vm = vm(writer, prefs)
        val job = launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        vm.dismiss(vm.uiState.value.groups[0])
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.uiState.value.groups.size)
        assertNotNull(prefs.dismissed.value.firstOrNull())
        assertTrue(
            vm.uiState.value.groups
                .none { it.confidence == MatchConfidence.HIGH },
        )
        job.cancel()
    }

    private val strings = FakeStringProvider()

    private class Harness(
        val vm: DuplicatesViewModel,
        val writer: FakeWriter,
        val prefs: FakePrefs,
        val analytics: FakeAnalytics,
        val events: MutableList<UiEvent>,
    ) {
        val group get() = vm.uiState.value.groups[0]
    }

    private suspend fun kotlinx.coroutines.test.TestScope.harness(
        prefs: FakePrefs = FakePrefs(),
        writer: FakeWriter = FakeWriter(),
        block: suspend Harness.() -> Unit,
    ) {
        val analytics = FakeAnalytics()
        val vm = vm(writer, prefs, analytics)
        val events = mutableListOf<UiEvent>()
        val j1 = launch { vm.uiState.collect {} }
        val j2 = launch { vm.events.collect { events += it } }
        dispatcher.scheduler.advanceUntilIdle()
        Harness(vm, writer, prefs, analytics, events).block()
        j1.cancel()
        j2.cancel()
    }

    private fun idle() = dispatcher.scheduler.advanceUntilIdle()

    private fun UiEvent.snack() = this as UiEvent.ShowSnackbar

    @Test fun `first link shows the explanation and does not link`() = runTest(dispatcher) {
        harness {
            vm.link(group)
            idle()
            assertEquals(PendingExplain(DuplicateAction.LINK, group), vm.uiState.value.pendingExplain)
            assertNull(writer.linked)
            assertTrue(events.isEmpty())
        }
    }

    @Test fun `first dismiss shows the explanation and does not dismiss`() = runTest(dispatcher) {
        harness {
            vm.dismiss(group)
            idle()
            assertEquals(PendingExplain(DuplicateAction.DISMISS, group), vm.uiState.value.pendingExplain)
            assertNull(writer.separated)
            assertTrue(prefs.dismissed.value.isEmpty())
        }
    }

    @Test fun `confirming the explanation links, remembers it, and later links run straight away`() = runTest(dispatcher) {
        harness {
            vm.link(group)
            idle()
            vm.confirmExplained()
            idle()
            assertEquals(listOf(10L, 20L), writer.linked)
            assertNull(vm.uiState.value.pendingExplain)
            assertTrue(DuplicateAction.LINK in prefs.explained)
            writer.linked = null
            vm.link(group)
            idle()
            assertEquals(listOf(10L, 20L), writer.linked)
            assertNull(vm.uiState.value.pendingExplain)
        }
    }

    @Test fun `confirming the dismiss explanation dismisses and remembers it`() = runTest(dispatcher) {
        harness {
            val g = group
            vm.dismiss(g)
            idle()
            vm.confirmExplained()
            idle()
            assertEquals(setOf(DuplicateFinder.groupKey(g)), prefs.dismissed.value)
            assertTrue(DuplicateAction.DISMISS in prefs.explained)
        }
    }

    @Test fun `cancelling the explanation is not remembered`() = runTest(dispatcher) {
        harness {
            vm.link(group)
            idle()
            vm.cancelExplain()
            idle()
            assertNull(vm.uiState.value.pendingExplain)
            assertNull(writer.linked)
            assertFalse(DuplicateAction.LINK in prefs.explained)
            vm.link(group)
            idle()
            assertEquals(
                DuplicateAction.LINK,
                vm.uiState.value.pendingExplain
                    ?.action,
            )
        }
    }

    @Test fun `link snackbar offers undo and undo resets aggregation across contacts`() = runTest(dispatcher) {
        harness(explainedPrefs()) {
            vm.link(group)
            idle()
            val event = events.single().snack()
            assertEquals(strings.getQuantity(R.plurals.duplicates_msg_linked, 2, 2), event.message)
            assertEquals(strings.get(R.string.duplicates_undo), event.actionLabel)
            event.onAction!!.invoke()
            idle()
            assertEquals(listOf(listOf(10L), listOf(20L)), writer.reset)
        }
    }

    @Test fun `dismiss separates only across contacts`() = runTest(dispatcher) {
        contactsFlow.value = listOf(
            contact(1, "Andi Wijaya", "+62812345678").let {
                it.copy(rawContacts = it.rawContacts + it.rawContacts[0].copy(rawContactId = 11, accountType = "com.samsung"))
            },
            contact(2, "Andi W", "0812345678", accType = "com.whatsapp"),
        )
        harness(explainedPrefs()) {
            vm.dismiss(group)
            idle()
            assertEquals(listOf(listOf(10L, 11L), listOf(20L)), writer.separated)
        }
    }

    @Test fun `dismiss snackbar undo restores the group and resets aggregation`() = runTest(dispatcher) {
        harness(explainedPrefs()) {
            val g = group
            vm.dismiss(g)
            idle()
            assertTrue(
                vm.uiState.value.groups
                    .none { it == g },
            )
            val event = events.single().snack()
            assertEquals(strings.get(R.string.duplicates_msg_dismissed), event.message)
            assertEquals(strings.get(R.string.duplicates_undo), event.actionLabel)
            event.onAction!!.invoke()
            idle()
            assertTrue(prefs.dismissed.value.isEmpty())
            assertTrue(
                vm.uiState.value.groups
                    .any { it == g },
            )
            assertEquals(listOf(listOf(10L), listOf(20L)), writer.reset)
        }
    }

    @Test fun `failed link offers no undo`() = runTest(dispatcher) {
        val writer = FakeWriter().apply { linkResult = ContactOpResult.Failure("nope") }
        harness(explainedPrefs(), writer) {
            vm.link(group)
            idle()
            val event = events.single().snack()
            assertEquals("nope", event.message)
            assertNull(event.actionLabel)
            assertNull(event.onAction)
        }
    }

    @Test fun `dismiss still hides the group when keepSeparate fails, and undo restores it`() = runTest(dispatcher) {
        val writer = FakeWriter().apply {
            separateResult = ContactOpResult.Failure("sim")
            resetResult = ContactOpResult.Failure("sim")
        }
        harness(explainedPrefs(), writer) {
            val g = group
            vm.dismiss(g)
            idle()
            assertTrue(
                vm.uiState.value.groups
                    .none { it == g },
            )
            events
                .single()
                .snack()
                .onAction!!
                .invoke()
            idle()
            assertTrue(prefs.dismissed.value.isEmpty())
            assertTrue(
                vm.uiState.value.groups
                    .any { it == g },
            )
        }
    }

    @Test fun `a failing undo does not crash and emits nothing more`() = runTest(dispatcher) {
        val writer = FakeWriter().apply { resetResult = ContactOpResult.Failure("gone") }
        harness(explainedPrefs(), writer) {
            vm.link(group)
            idle()
            events
                .single()
                .snack()
                .onAction!!
                .invoke()
            idle()
            assertEquals(1, events.size)
        }
    }

    @Test fun `second tap while the explanation is pending is ignored`() = runTest(dispatcher) {
        harness {
            val g = group
            vm.link(g)
            idle()
            vm.dismiss(g)
            vm.link(g)
            idle()
            assertEquals(
                DuplicateAction.LINK,
                vm.uiState.value.pendingExplain
                    ?.action,
            )
            vm.confirmExplained()
            idle()
            assertEquals(listOf(10L, 20L), writer.linked)
            assertTrue(prefs.dismissed.value.isEmpty())
            assertFalse(DuplicateAction.DISMISS in prefs.explained)
        }
    }

    @Test fun `two quick taps before the lookup returns act once`() = runTest(dispatcher) {
        harness(explainedPrefs()) {
            val g = group
            vm.link(g)
            vm.link(g)
            idle()
            assertEquals(1, events.size)
        }
    }

    @Test fun `undo logs duplicate_undo with action and count`() = runTest(dispatcher) {
        harness(explainedPrefs()) {
            val g = group
            vm.link(g)
            idle()
            events
                .last()
                .snack()
                .onAction!!
                .invoke()
            idle()
            vm.dismiss(g)
            idle()
            events
                .last()
                .snack()
                .onAction!!
                .invoke()
            idle()
            val undos = analytics.events.filterIsInstance<AnalyticsEvent.DuplicateUndo>()
            assertEquals(listOf(AnalyticsEvent.DuplicateUndo("link", 2), AnalyticsEvent.DuplicateUndo("dismiss", 2)), undos)
            assertEquals("duplicate_undo", undos[0].name)
            assertEquals(mapOf("action" to "link", "count" to 2), undos[0].params)
        }
    }
}
