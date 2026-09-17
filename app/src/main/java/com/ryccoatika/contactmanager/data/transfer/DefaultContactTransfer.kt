package com.ryccoatika.contactmanager.data.transfer

import android.net.Uri
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.data.BatchRunner
import com.ryccoatika.contactmanager.data.ContactsSource
import com.ryccoatika.contactmanager.data.ContactsWriter
import com.ryccoatika.contactmanager.data.StringProvider
import com.ryccoatika.contactmanager.di.IoDispatcher
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.domain.model.RawContact
import com.ryccoatika.contactmanager.domain.vcard.VCardContact
import com.ryccoatika.contactmanager.domain.vcard.VCardParser
import com.ryccoatika.contactmanager.domain.vcard.VCardWriter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Export half implemented here (Task 5); [parseFile] is a trivial read-and-parse and
 * [startImport] always reports "busy" until Task 6 wires the batched import.
 */
@Singleton
class DefaultContactTransfer
    @Inject
    constructor(
        private val contactsSource: ContactsSource,
        private val writer: ContactsWriter,
        private val batchRunner: BatchRunner,
        private val files: TransferFiles,
        private val photos: ContactPhotoSource,
        private val strings: StringProvider,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ContactTransfer {
        override suspend fun exportAccounts(accounts: List<ContactAccount>, uri: Uri): TransferResult =
            withContext(ioDispatcher) {
                val keys = accounts.map { it.type to it.name }.toSet()
                val pairs = contactsSource.snapshot().flatMap { contact ->
                    contact.rawContacts
                        .filter { (it.accountType to it.accountName) in keys }
                        .map { contact to it }
                }
                writeSingleFile(pairs, uri)
            }

        override suspend fun exportRawContacts(rawContactIds: List<Long>, uri: Uri): TransferResult =
            withContext(ioDispatcher) {
                val ids = rawContactIds.toSet()
                val byId = contactsSource
                    .snapshot()
                    .flatMap { contact ->
                        contact.rawContacts.filter { it.rawContactId in ids }.map { contact to it }
                    }.associateBy { it.second.rawContactId }
                val pairs = rawContactIds.mapNotNull { byId[it] }
                writeSingleFile(pairs, uri)
            }

        override suspend fun exportAccountsToFolder(accounts: List<ContactAccount>, treeUri: Uri): TransferResult =
            withContext(ioDispatcher) {
                val allContacts = contactsSource.snapshot()
                val usedNames = mutableSetOf<String>()
                val createdUris = mutableListOf<Uri>()
                var totalContacts = 0
                try {
                    accounts.forEach { account ->
                        val pairs = allContacts.flatMap { contact ->
                            contact.rawContacts
                                .filter { it.accountType == account.type && it.accountName == account.name }
                                .map { contact to it }
                        }
                        val name = uniqueName(sanitizeFileName(account.displayLabel ?: account.name ?: account.type), usedNames)
                        val fileUri = files.createInTree(treeUri, "$name.vcf")
                        if (fileUri == null) {
                            // A later account's file couldn't be created — clean up every file
                            // already created for earlier accounts so nothing is left orphaned.
                            createdUris.forEach { files.delete(it) }
                            return@withContext TransferResult.Failure(strings.get(R.string.transfer_error_open_file))
                        }
                        createdUris += fileUri
                        totalContacts += writeVCardTo(fileUri, pairs)
                    }
                    TransferResult.Success(totalContacts, createdUris.size)
                } catch (e: IOException) {
                    createdUris.forEach { files.delete(it) }
                    TransferResult.Failure(strings.get(R.string.transfer_error_write))
                } catch (e: SecurityException) {
                    createdUris.forEach { files.delete(it) }
                    TransferResult.Failure(strings.get(R.string.transfer_error_write))
                }
            }

        override suspend fun parseFile(uri: Uri): ParseOutcome = withContext(ioDispatcher) {
            val input = files.openRead(uri)
                ?: return@withContext ParseOutcome.Failure(strings.get(R.string.transfer_error_open_file))
            val text = input.use { it.readBytes().toString(Charsets.UTF_8) }
            val result = VCardParser.parse(text)
            ParseOutcome.Parsed(result.contacts, result.skippedCards)
        }

        // Real batched import lands in Task 6, wired through [batchRunner] and [writer].
        override fun startImport(contacts: List<VCardContact>, targets: List<ContactAccount>): Boolean = false

        private suspend fun writeSingleFile(pairs: List<Pair<Contact, RawContact>>, uri: Uri): TransferResult {
            val out = openOrNull(uri) ?: return TransferResult.Failure(strings.get(R.string.transfer_error_open_file))
            return try {
                val count = writeVCard(out, pairs)
                TransferResult.Success(count, 1)
            } catch (e: IOException) {
                files.delete(uri)
                TransferResult.Failure(strings.get(R.string.transfer_error_write))
            } catch (e: SecurityException) {
                files.delete(uri)
                TransferResult.Failure(strings.get(R.string.transfer_error_write))
            }
        }

        private suspend fun writeVCardTo(uri: Uri, pairs: List<Pair<Contact, RawContact>>): Int {
            val out = openOrNull(uri) ?: throw IOException("Could not open $uri for writing")
            return writeVCard(out, pairs)
        }

        /**
         * `ContentResolver.openOutputStream` can throw `IOException` or `SecurityException`
         * (a SAF grant revoked mid-session) instead of just returning null — never let either
         * crash the caller; both collapse to "couldn't open" here, same as a null return.
         */
        private fun openOrNull(uri: Uri): OutputStream? = try {
            files.openWrite(uri)
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }

        private suspend fun writeVCard(out: OutputStream, pairs: List<Pair<Contact, RawContact>>): Int {
            val vcards = pairs.map { (contact, raw) -> toVCardContact(contact.displayName, raw) }
            val content = VCardWriter.write(vcards)
            out.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            return vcards.size
        }

        private suspend fun toVCardContact(parentDisplayName: String, raw: RawContact): VCardContact {
            val name = listOfNotNull(raw.givenName, raw.familyName).joinToString(" ").ifBlank { parentDisplayName }
            return VCardContact(
                displayName = name,
                givenName = raw.givenName,
                familyName = raw.familyName,
                phones = raw.phones.filter { it.value.isNotBlank() }.map { it.value to it.typeLabel },
                emails = raw.emails.filter { it.value.isNotBlank() }.map { it.value to it.typeLabel },
                organization = raw.organization,
                jobTitle = raw.jobTitle,
                nickname = raw.nickname,
                websites = raw.websites.map { it.value }.filter { it.isNotBlank() },
                addresses = raw.addresses.map { it.value }.filter { it.isNotBlank() },
                birthday = raw.birthday,
                anniversary = raw.anniversary,
                note = raw.note,
                photo = photos.photoOf(raw.rawContactId),
            )
        }

        private fun uniqueName(base: String, used: MutableSet<String>): String {
            var candidate = base
            var suffix = 2
            while (candidate in used) {
                candidate = "$base ($suffix)"
                suffix++
            }
            used += candidate
            return candidate
        }

        private fun sanitizeFileName(raw: String?): String {
            val cleaned = (raw ?: "")
                .map { c -> if (c in ILLEGAL_CHARS || c.code < 0x20) '_' else c }
                .joinToString("")
                .trim()
            return cleaned.ifBlank { "contacts" }
        }

        private companion object {
            const val ILLEGAL_CHARS = "/\\:*?\"<>|"
        }
    }
