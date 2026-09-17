package com.ryccoatika.contactmanager.data

import com.ryccoatika.contactmanager.domain.model.RawContact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BatchOperationManagerTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeWriter(
        var moveResult: ContactOpResult = ContactOpResult.Success,
        var delayPerContactMs: Long = 0,
    ) : ContactsWriter {
        val moved = mutableListOf<Long>()
        val copied = mutableListOf<Long>()
        var target: Pair<String?, String?>? = null

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
            target = targetType to targetName
            rawContactIds.forEachIndexed { index, id ->
                if (delayPerContactMs > 0) delay(delayPerContactMs)
                moved += id
                onProgress(index + 1, rawContactIds.size)
            }
            return moveResult
        }

        override suspend fun copyRawContacts(
            rawContactIds: List<Long>,
            targetType: String?,
            targetName: String?,
            onProgress: (done: Int, total: Int) -> Unit,
        ): ContactOpResult {
            copied += rawContactIds
            rawContactIds.forEachIndexed { index, _ -> onProgress(index + 1, rawContactIds.size) }
            return moveResult
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

    private fun manager(writer: ContactsWriter) =
        BatchOperationManager(writer, CoroutineScope(SupervisorJob() + dispatcher))

    @Test fun `moveContacts reports progress then finished`() = runTest(dispatcher) {
        val writer = FakeWriter()
        val manager = manager(writer)
        manager.moveContacts(listOf(1, 2, 3), "com.google", "a@gmail.com", "Moving 3", "Done")
        assertEquals(BatchProgress(0, 3, "Moving 3", finished = false), manager.progress.value)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            BatchProgress(3, 3, "Moving 3", finished = true, finishedMessage = "Done"),
            manager.progress.value,
        )
        assertEquals(listOf(1L, 2L, 3L), writer.moved)
        assertEquals("com.google" to "a@gmail.com", writer.target)
    }

    @Test fun `copyContacts reports progress then finished without touching sources`() = runTest(dispatcher) {
        val writer = FakeWriter()
        val manager = manager(writer)
        manager.copyContacts(listOf(1, 2), "com.google", "a@gmail.com", "Copying 2", "Copied 2")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            BatchProgress(2, 2, "Copying 2", finished = true, finishedMessage = "Copied 2"),
            manager.progress.value,
        )
        assertEquals(listOf(1L, 2L), writer.copied)
        assertTrue(writer.moved.isEmpty())
    }

    @Test fun `failed move surfaces error in finished progress`() = runTest(dispatcher) {
        val writer = FakeWriter(moveResult = ContactOpResult.Failure("boom"))
        val manager = manager(writer)
        manager.moveContacts(listOf(1), null, null, "Moving 1", "Done")
        dispatcher.scheduler.advanceUntilIdle()
        val progress = manager.progress.value
        assertEquals(true, progress?.finished)
        assertEquals("boom", progress?.error)
    }

    @Test fun `cancel stops the move and clears progress`() = runTest(dispatcher) {
        val writer = FakeWriter(delayPerContactMs = 100)
        val manager = manager(writer)
        manager.moveContacts(listOf(1, 2, 3), null, null, "Moving 3", "Done")
        dispatcher.scheduler.advanceTimeBy(150)
        manager.cancel()
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(manager.progress.value)
        assertEquals(listOf(1L), writer.moved)
    }

    @Test fun `clearFinished clears only finished progress`() = runTest(dispatcher) {
        val writer = FakeWriter(delayPerContactMs = 100)
        val manager = manager(writer)
        manager.moveContacts(listOf(1), null, null, "Moving 1", "Done")
        manager.clearFinished()
        assertNotNull(manager.progress.value)
        dispatcher.scheduler.advanceUntilIdle()
        manager.clearFinished()
        assertNull(manager.progress.value)
    }

    @Test fun `second moveContacts while one is running is ignored`() = runTest(dispatcher) {
        val writer = FakeWriter(delayPerContactMs = 100)
        val manager = manager(writer)
        manager.moveContacts(listOf(1, 2), null, null, "First", "Done")
        manager.moveContacts(listOf(3), null, null, "Second", "Done")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(1L, 2L), writer.moved)
        assertEquals("First", manager.progress.value?.label)
    }

    @Test fun `run drives progress and finishes with the given message`() = runTest(dispatcher) {
        val manager = BatchOperationManager(FakeWriter(), CoroutineScope(SupervisorJob() + dispatcher))
        val started = manager.run(total = 2, label = "L", finishedMessage = "done!") { onProgress ->
            onProgress(1, 2)
            onProgress(2, 2)
            ContactOpResult.Success
        }
        assertTrue(started)
        dispatcher.scheduler.advanceUntilIdle()
        val p = manager.progress.value!!
        assertTrue(p.finished)
        assertEquals("done!", p.finishedMessage)
        assertNull(p.error)
    }

    @Test fun `run refuses while another batch is active`() = runTest(dispatcher) {
        val manager = BatchOperationManager(FakeWriter(), CoroutineScope(SupervisorJob() + dispatcher))
        manager.run(1, "L", "m") { kotlinx.coroutines.awaitCancellation() }
        assertFalse(manager.run(1, "L2", "m2") { ContactOpResult.Success })
        manager.cancel()
    }
}
