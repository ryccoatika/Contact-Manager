package com.ryccoatika.contactmanager.data.transfer

import android.net.FakeUri
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.data.BatchOperationManager
import com.ryccoatika.contactmanager.data.BatchProgress
import com.ryccoatika.contactmanager.data.BatchRunner
import com.ryccoatika.contactmanager.data.ContactOpResult
import com.ryccoatika.contactmanager.data.ContactsSource
import com.ryccoatika.contactmanager.data.ContactsWriter
import com.ryccoatika.contactmanager.data.EditableContact
import com.ryccoatika.contactmanager.data.FakeStringProvider
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.domain.model.LabeledValue
import com.ryccoatika.contactmanager.domain.model.RawContact
import com.ryccoatika.contactmanager.domain.vcard.VCardContact
import com.ryccoatika.contactmanager.domain.vcard.VCardParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultContactTransferTest {
    private val dispatcher = StandardTestDispatcher()
    private val strings = FakeStringProvider()

    private val googleAccount = ContactAccount("acc1", "com.google", AccountCapability.FULL_CRUD)

    private val contacts = listOf(
        Contact(
            contactId = 1,
            displayName = "Alice Wonderland",
            rawContacts = listOf(
                RawContact(
                    rawContactId = 10,
                    accountType = "com.google",
                    accountName = "acc1",
                    givenName = "Alice",
                    familyName = "Wonderland",
                    phones = listOf(LabeledValue(101, "+1000", "Mobile")),
                    emails = listOf(LabeledValue(102, "alice@x.com", "Home")),
                ),
            ),
        ),
        Contact(
            contactId = 2,
            displayName = "Bob Marley",
            rawContacts = listOf(
                RawContact(
                    rawContactId = 20,
                    accountType = "com.google",
                    accountName = "acc1",
                    givenName = "Bob",
                    familyName = "Marley",
                ),
            ),
        ),
        Contact(
            contactId = 3,
            displayName = "Whats Guy",
            rawContacts = listOf(
                RawContact(rawContactId = 30, accountType = "com.whatsapp", accountName = "acc2"),
            ),
        ),
        Contact(
            contactId = 4,
            displayName = "Multi Account",
            rawContacts = listOf(
                RawContact(
                    rawContactId = 40,
                    accountType = "com.google",
                    accountName = "acc1",
                    givenName = "Multi",
                    familyName = "Account",
                ),
                RawContact(rawContactId = 41, accountType = "com.whatsapp", accountName = "acc2"),
            ),
        ),
        Contact(
            contactId = 5,
            displayName = "Sales Rep",
            rawContacts = listOf(
                RawContact(
                    rawContactId = 50,
                    accountType = "com.google",
                    accountName = "Sales/Team:2026",
                    givenName = "Sales",
                    familyName = "Rep",
                ),
            ),
        ),
    )

    private fun fakeContactsSource(items: List<Contact> = contacts) = object : ContactsSource {
        override fun observeContacts(): Flow<List<Contact>> = MutableStateFlow(items)

        override suspend fun snapshot(): List<Contact> = items
    }

    private class NoopContactsWriter : ContactsWriter {
        override suspend fun createContact(accountType: String?, accountName: String?, contact: EditableContact) =
            ContactOpResult.Success

        override suspend fun updateRawContact(rawContactId: Long, contact: EditableContact) =
            ContactOpResult.Success

        override suspend fun deleteRawContacts(rawContactIds: List<Long>) = ContactOpResult.Success

        override suspend fun copyRawContact(rawContactId: Long, targetType: String?, targetName: String?) =
            ContactOpResult.Success

        override suspend fun copyRawContacts(
            rawContactIds: List<Long>,
            targetType: String?,
            targetName: String?,
            onProgress: (done: Int, total: Int) -> Unit,
        ) = ContactOpResult.Success

        override suspend fun moveRawContacts(
            rawContactIds: List<Long>,
            targetType: String?,
            targetName: String?,
            onProgress: (done: Int, total: Int) -> Unit,
        ) = ContactOpResult.Success

        override suspend fun linkContacts(rawContactIds: List<Long>) = ContactOpResult.Success

        override suspend fun keepSeparate(rawContactIds: List<Long>) = ContactOpResult.Success

        override suspend fun mergeContacts(target: RawContact, sources: List<RawContact>) = ContactOpResult.Success
    }

    /** Records [createContact] calls; [failFor] lets selected calls return a Failure. */
    private class FakeContactsWriter : ContactsWriter {
        val created = mutableListOf<Triple<String?, String?, EditableContact>>()
        var failFor: (String?, String?, EditableContact) -> Boolean = { _, _, _ -> false }

        override suspend fun createContact(accountType: String?, accountName: String?, contact: EditableContact): ContactOpResult {
            created += Triple(accountType, accountName, contact)
            return if (failFor(accountType, accountName, contact)) ContactOpResult.Failure("x") else ContactOpResult.Success
        }

        override suspend fun updateRawContact(rawContactId: Long, contact: EditableContact) = ContactOpResult.Success

        override suspend fun deleteRawContacts(rawContactIds: List<Long>) = ContactOpResult.Success

        override suspend fun copyRawContact(rawContactId: Long, targetType: String?, targetName: String?) =
            ContactOpResult.Success

        override suspend fun copyRawContacts(
            rawContactIds: List<Long>,
            targetType: String?,
            targetName: String?,
            onProgress: (done: Int, total: Int) -> Unit,
        ) = ContactOpResult.Success

        override suspend fun moveRawContacts(
            rawContactIds: List<Long>,
            targetType: String?,
            targetName: String?,
            onProgress: (done: Int, total: Int) -> Unit,
        ) = ContactOpResult.Success

        override suspend fun linkContacts(rawContactIds: List<Long>) = ContactOpResult.Success

        override suspend fun keepSeparate(rawContactIds: List<Long>) = ContactOpResult.Success

        override suspend fun mergeContacts(target: RawContact, sources: List<RawContact>) = ContactOpResult.Success
    }

    private class NoopBatchRunner : BatchRunner {
        override val progress: StateFlow<BatchProgress?> = MutableStateFlow(null)

        override fun run(
            total: Int,
            label: String,
            finishedMessage: String,
            operation: suspend (onProgress: (Int, Int) -> Unit) -> ContactOpResult,
        ) = false

        override fun moveContacts(
            rawContactIds: List<Long>,
            targetType: String?,
            targetName: String?,
            label: String,
            finishedMessage: String,
        ) = false

        override fun copyContacts(
            rawContactIds: List<Long>,
            targetType: String?,
            targetName: String?,
            label: String,
            finishedMessage: String,
        ) = false

        override fun cancel() = Unit

        override fun clearFinished() = Unit
    }

    private class FakePhotoSource(
        private val photos: Map<Long, ByteArray> = emptyMap(),
    ) : ContactPhotoSource {
        override suspend fun photoOf(rawContactId: Long): ByteArray? = photos[rawContactId]
    }

    private fun transfer(
        items: List<Contact> = contacts,
        files: FakeTransferFiles = FakeTransferFiles(),
        photos: ContactPhotoSource = FakePhotoSource(),
        writer: ContactsWriter = NoopContactsWriter(),
        batchRunner: BatchRunner = NoopBatchRunner(),
    ) = DefaultContactTransfer(
        contactsSource = fakeContactsSource(items),
        writer = writer,
        batchRunner = batchRunner,
        files = files,
        photos = photos,
        strings = strings,
        ioDispatcher = dispatcher,
    )

    @Test fun `exportAccounts writes every raw contact of the accounts to one file`() = runTest(dispatcher) {
        val files = FakeTransferFiles()
        val uri = FakeUri("file:///out.vcf")
        val result = transfer(files = files).exportAccounts(listOf(googleAccount), uri)

        assertEquals(TransferResult.Success(3, 1), result)
        val parsed = VCardParser.parse(files.writes.getValue(uri.toString()).toString(Charsets.UTF_8))
        assertEquals(listOf("Alice Wonderland", "Bob Marley", "Multi Account"), parsed.contacts.map { it.displayName })
    }

    @Test fun `exportAccounts attaches photos from the photo source`() = runTest(dispatcher) {
        val files = FakeTransferFiles()
        val uri = FakeUri("file:///out.vcf")
        val photoBytes = byteArrayOf(1, 2, 3, 4)
        val result = transfer(files = files, photos = FakePhotoSource(mapOf(10L to photoBytes)))
            .exportAccounts(listOf(googleAccount), uri)

        assertTrue(result is TransferResult.Success)
        val parsed = VCardParser.parse(files.writes.getValue(uri.toString()).toString(Charsets.UTF_8))
        val alice = parsed.contacts.first { it.displayName == "Alice Wonderland" }
        val bob = parsed.contacts.first { it.displayName == "Bob Marley" }
        assertTrue(photoBytes.contentEquals(alice.photo))
        assertNull(bob.photo)
    }

    @Test fun `exportRawContacts writes exactly the given raw contacts`() = runTest(dispatcher) {
        val files = FakeTransferFiles()
        val uri = FakeUri("file:///out.vcf")
        val result = transfer(files = files).exportRawContacts(listOf(20L, 30L), uri)

        assertEquals(TransferResult.Success(2, 1), result)
        val parsed = VCardParser.parse(files.writes.getValue(uri.toString()).toString(Charsets.UTF_8))
        assertEquals(listOf("Bob Marley", "Whats Guy"), parsed.contacts.map { it.displayName })
    }

    @Test fun `export uses contact displayName when raw has no given or family name`() = runTest(dispatcher) {
        val files = FakeTransferFiles()
        val uri = FakeUri("file:///out.vcf")
        transfer(files = files).exportRawContacts(listOf(30L), uri)

        val parsed = VCardParser.parse(files.writes.getValue(uri.toString()).toString(Charsets.UTF_8))
        assertEquals("Whats Guy", parsed.contacts.single().displayName)
    }

    @Test fun `exportAccountsToFolder writes one file per account named after its label, sanitized`() = runTest(dispatcher) {
        val files = FakeTransferFiles()
        val treeUri = FakeUri("content://tree/root")
        val messyAccount = ContactAccount("Sales/Team:2026", "com.google", AccountCapability.FULL_CRUD)
        val result = transfer(files = files).exportAccountsToFolder(listOf(messyAccount), treeUri)

        assertEquals(TransferResult.Success(1, 1), result)
        assertEquals(listOf("Sales_Team_2026.vcf"), files.createdNames)
    }

    @Test fun `exportAccountsToFolder appends numeric suffix on display-name collision`() = runTest(dispatcher) {
        val files = FakeTransferFiles()
        val treeUri = FakeUri("content://tree/root")
        val accountA = ContactAccount("Team", "com.google", AccountCapability.FULL_CRUD)
        val accountB = ContactAccount("Team", "com.whatsapp", AccountCapability.READ_ONLY)
        val result = transfer(files = files).exportAccountsToFolder(listOf(accountA, accountB), treeUri)

        assertEquals(TransferResult.Success(0, 2), result)
        assertEquals(listOf("Team.vcf", "Team (2).vcf"), files.createdNames)
    }

    @Test fun `export returns Failure with message when the stream cannot be opened`() = runTest(dispatcher) {
        val uri = FakeUri("file:///out.vcf")
        val files = FakeTransferFiles().apply { openWriteFailsFor = setOf(uri.toString()) }
        val result = transfer(files = files).exportAccounts(listOf(googleAccount), uri)

        assertEquals(TransferResult.Failure(strings.get(R.string.transfer_error_open_file)), result)
    }

    @Test fun `export deletes the partial single file when writing throws`() = runTest(dispatcher) {
        val uri = FakeUri("file:///out.vcf")
        val files = FakeTransferFiles().apply { writeThrowsFor = setOf(uri.toString()) }
        val result = transfer(files = files).exportAccounts(listOf(googleAccount), uri)

        assertEquals(TransferResult.Failure(strings.get(R.string.transfer_error_write)), result)
        assertEquals(listOf(uri.toString()), files.deleted)
    }

    @Test fun `exportAccountsToFolder deletes earlier files when a later createInTree fails`() = runTest(dispatcher) {
        val files = FakeTransferFiles().apply { createFailsFor = setOf("Second.vcf") }
        val treeUri = FakeUri("content://tree/root")
        val accountA = ContactAccount("First", "com.google", AccountCapability.FULL_CRUD)
        val accountB = ContactAccount("Second", "com.whatsapp", AccountCapability.READ_ONLY)
        val result = transfer(files = files).exportAccountsToFolder(listOf(accountA, accountB), treeUri)

        assertEquals(TransferResult.Failure(strings.get(R.string.transfer_error_open_file)), result)
        assertEquals(listOf("$treeUri/First.vcf"), files.deleted)
    }

    @Test fun `export returns Failure without crashing when opening the stream throws SecurityException`() = runTest(dispatcher) {
        val uri = FakeUri("file:///out.vcf")
        val files = FakeTransferFiles().apply { openWriteThrowsSecurityFor = setOf(uri.toString()) }
        val result = transfer(files = files).exportAccounts(listOf(googleAccount), uri)

        assertEquals(TransferResult.Failure(strings.get(R.string.transfer_error_open_file)), result)
    }

    @Test fun `parseFile returns contacts and skipped count`() = runTest(dispatcher) {
        val files = FakeTransferFiles()
        val uri = FakeUri("file:///in.vcf")
        files.reads[uri.toString()] = """
            BEGIN:VCARD
            FN:Alice Wonderland
            END:VCARD
            BEGIN:VCARD
            FN:Bob Marley
            END:VCARD
            BEGIN:VCARD
            FN:Broken
        """.trimIndent()

        val result = transfer(files = files).parseFile(uri)

        assertTrue(result is ParseOutcome.Parsed)
        val parsed = result as ParseOutcome.Parsed
        assertEquals(listOf("Alice Wonderland", "Bob Marley"), parsed.contacts.map { it.displayName })
        assertEquals(1, parsed.skippedCards)
    }

    @Test fun `parseFile returns Failure when stream is null`() = runTest(dispatcher) {
        val files = FakeTransferFiles()
        val uri = FakeUri("file:///missing.vcf")

        val result = transfer(files = files).parseFile(uri)

        assertEquals(ParseOutcome.Failure(strings.get(R.string.transfer_error_open_file)), result)
    }

    @Test fun `parseFile returns Failure when zero cards parse`() = runTest(dispatcher) {
        val files = FakeTransferFiles()
        val uri = FakeUri("file:///garbage.vcf")
        files.reads[uri.toString()] = "this is not a vcard\njust some random text"

        val result = transfer(files = files).parseFile(uri)

        assertEquals(ParseOutcome.Failure(strings.get(R.string.transfer_error_no_contacts)), result)
    }

    @Test fun `startImport creates every contact in every target account`() = runTest(dispatcher) {
        val writer = FakeContactsWriter()
        val batchRunner = BatchOperationManager(writer, CoroutineScope(SupervisorJob() + dispatcher))
        val vc1 = VCardContact(displayName = "Alice Wonderland")
        val vc2 = VCardContact(displayName = "Bob Marley")
        val target1 = ContactAccount("acc1", "com.google", AccountCapability.FULL_CRUD)
        val target2 = ContactAccount("acc2", "com.whatsapp", AccountCapability.READ_ONLY)

        val started = transfer(writer = writer, batchRunner = batchRunner)
            .startImport(listOf(vc1, vc2), listOf(target1, target2))
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(started)
        assertEquals(4, writer.created.size)
        assertEquals(
            listOf(
                "com.google" to "acc1",
                "com.whatsapp" to "acc2",
                "com.google" to "acc1",
                "com.whatsapp" to "acc2",
            ),
            writer.created.map { it.first to it.second },
        )
    }

    @Test fun `startImport maps vcard fields into EditableContact including photo`() = runTest(dispatcher) {
        val writer = FakeContactsWriter()
        val batchRunner = BatchOperationManager(writer, CoroutineScope(SupervisorJob() + dispatcher))
        val photoBytes = byteArrayOf(9, 8, 7)
        val vc = VCardContact(
            displayName = "Carol Lee",
            givenName = "Carol",
            familyName = "Lee",
            phones = listOf("+1000" to "Mobile"),
            emails = listOf("carol@x.com" to "Home"),
            organization = "Acme",
            jobTitle = "Engineer",
            nickname = "Caz",
            websites = listOf("http://acme.example"),
            addresses = listOf("123 Main St"),
            birthday = "1990-01-01",
            anniversary = "2010-01-01",
            note = "vip",
            photo = photoBytes,
        )
        val target = ContactAccount("acc1", "com.google", AccountCapability.FULL_CRUD)

        transfer(writer = writer, batchRunner = batchRunner).startImport(listOf(vc), listOf(target))
        dispatcher.scheduler.advanceUntilIdle()

        val (accountType, accountName, contact) = writer.created.single()
        assertEquals("com.google", accountType)
        assertEquals("acc1", accountName)
        assertEquals(vc.displayName, contact.displayName)
        assertEquals(vc.phones, contact.phones)
        assertEquals(vc.emails, contact.emails)
        assertEquals(vc.organization, contact.organization)
        assertEquals(vc.note, contact.note)
        assertEquals(vc.jobTitle, contact.jobTitle)
        assertEquals(vc.nickname, contact.nickname)
        assertEquals(vc.websites, contact.websites)
        assertEquals(vc.addresses, contact.addresses)
        assertEquals(vc.birthday, contact.birthday)
        assertEquals(vc.anniversary, contact.anniversary)
        assertSame(photoBytes, contact.photo)
    }

    @Test fun `startImport counts failures and finishes with imported-x-of-y message`() = runTest(dispatcher) {
        val writer = FakeContactsWriter().apply { failFor = { _, _, contact -> contact.displayName == "Bob Marley" } }
        val batchRunner = BatchOperationManager(writer, CoroutineScope(SupervisorJob() + dispatcher))
        val vc1 = VCardContact(displayName = "Alice Wonderland")
        val vc2 = VCardContact(displayName = "Bob Marley")
        val target = ContactAccount("acc1", "com.google", AccountCapability.FULL_CRUD)

        transfer(writer = writer, batchRunner = batchRunner).startImport(listOf(vc1, vc2), listOf(target))
        dispatcher.scheduler.advanceUntilIdle()

        val progress = batchRunner.progress.value
        assertEquals(true, progress?.finished)
        assertEquals(strings.get(R.string.transfer_imported_partial, 1, 2), progress?.error)
    }

    @Test fun `startImport returns false while another batch runs`() = runTest(dispatcher) {
        val writer = FakeContactsWriter()
        val batchRunner = BatchOperationManager(writer, CoroutineScope(SupervisorJob() + dispatcher))
        val vc = VCardContact(displayName = "Alice Wonderland")
        val target = ContactAccount("acc1", "com.google", AccountCapability.FULL_CRUD)
        val t = transfer(writer = writer, batchRunner = batchRunner)

        assertTrue(t.startImport(listOf(vc), listOf(target)))
        assertFalse(t.startImport(listOf(vc), listOf(target)))
        dispatcher.scheduler.advanceUntilIdle()
    }
}
