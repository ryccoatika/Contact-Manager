package com.ryccoatika.contactmanager.data

import android.content.ContentProviderOperation
import android.content.Context
import android.content.OperationApplicationException
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import com.ryccoatika.contactmanager.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

sealed interface ContactOpResult {
    data object Success : ContactOpResult
    data class Failure(val message: String, val cause: Throwable? = null) : ContactOpResult
}

data class EditableContact(
    val displayName: String,
    val phones: List<Pair<String, String?>>, // value to typeLabel (unused for write v1)
    val emails: List<Pair<String, String?>>,
    val organization: String?,
    val note: String?,
)

interface ContactsWriter {
    suspend fun createContact(accountType: String?, accountName: String?, contact: EditableContact): ContactOpResult
    suspend fun updateRawContact(rawContactId: Long, contact: EditableContact): ContactOpResult
    suspend fun deleteRawContacts(rawContactIds: List<Long>): ContactOpResult
}

@Singleton
class ContactsWriteRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ContactsWriter {

    override suspend fun createContact(
        accountType: String?,
        accountName: String?,
        contact: EditableContact,
    ): ContactOpResult = withContext(ioDispatcher) {
        runCatchingOp("create contact") {
            val ops = ArrayList<ContentProviderOperation>()
            ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_TYPE, accountType)
                .withValue(RawContacts.ACCOUNT_NAME, accountName)
                .build()
            // Back-references point at op 0, so a create always fits one chunk:
            // a single contact's ops stay far below MAX_OPS_PER_BATCH.
            ops += dataInsertOps(contact) { it.withValueBackReference(Data.RAW_CONTACT_ID, 0) }
            applyChunked(ops)
        }
    }

    override suspend fun updateRawContact(
        rawContactId: Long,
        contact: EditableContact,
    ): ContactOpResult = withContext(ioDispatcher) {
        runCatchingOp("update contact") {
            val ops = ArrayList<ContentProviderOperation>()
            ops += ContentProviderOperation.newDelete(Data.CONTENT_URI)
                .withSelection(
                    "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE} IN (?,?,?,?,?)",
                    arrayOf(
                        rawContactId.toString(),
                        StructuredName.CONTENT_ITEM_TYPE,
                        Phone.CONTENT_ITEM_TYPE,
                        Email.CONTENT_ITEM_TYPE,
                        Organization.CONTENT_ITEM_TYPE,
                        Note.CONTENT_ITEM_TYPE,
                    ),
                )
                .build()
            ops += dataInsertOps(contact) { it.withValue(Data.RAW_CONTACT_ID, rawContactId) }
            applyChunked(ops)
        }
    }

    override suspend fun deleteRawContacts(rawContactIds: List<Long>): ContactOpResult =
        withContext(ioDispatcher) {
            runCatchingOp("delete contact") {
                val ops = rawContactIds.chunked(MAX_OPS_PER_BATCH).map { ids ->
                    ContentProviderOperation.newDelete(RawContacts.CONTENT_URI)
                        .withSelection(
                            "${RawContacts._ID} IN (${ids.joinToString(",")})",
                            null,
                        )
                        .build()
                }
                applyChunked(ops)
            }
        }

    /** Data-row inserts for every non-blank field; [attach] binds the raw contact id. */
    private fun dataInsertOps(
        contact: EditableContact,
        attach: (ContentProviderOperation.Builder) -> ContentProviderOperation.Builder,
    ): List<ContentProviderOperation> {
        val ops = ArrayList<ContentProviderOperation>()
        if (contact.displayName.isNotBlank()) {
            ops += attach(ContentProviderOperation.newInsert(Data.CONTENT_URI))
                .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                .withValue(StructuredName.DISPLAY_NAME, contact.displayName)
                .build()
        }
        contact.phones.map { it.first }.filter { it.isNotBlank() }.forEach { number ->
            ops += attach(ContentProviderOperation.newInsert(Data.CONTENT_URI))
                .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                .withValue(Phone.NUMBER, number)
                .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                .build()
        }
        contact.emails.map { it.first }.filter { it.isNotBlank() }.forEach { address ->
            ops += attach(ContentProviderOperation.newInsert(Data.CONTENT_URI))
                .withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
                .withValue(Email.ADDRESS, address)
                .withValue(Email.TYPE, Email.TYPE_HOME)
                .build()
        }
        if (!contact.organization.isNullOrBlank()) {
            ops += attach(ContentProviderOperation.newInsert(Data.CONTENT_URI))
                .withValue(Data.MIMETYPE, Organization.CONTENT_ITEM_TYPE)
                .withValue(Organization.COMPANY, contact.organization)
                .build()
        }
        if (!contact.note.isNullOrBlank()) {
            ops += attach(ContentProviderOperation.newInsert(Data.CONTENT_URI))
                .withValue(Data.MIMETYPE, Note.CONTENT_ITEM_TYPE)
                .withValue(Note.NOTE, contact.note)
                .build()
        }
        return ops
    }

    private fun applyChunked(ops: List<ContentProviderOperation>) {
        ops.chunked(MAX_OPS_PER_BATCH).forEach { chunk ->
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ArrayList(chunk))
        }
    }

    private inline fun runCatchingOp(action: String, block: () -> Unit): ContactOpResult = try {
        block()
        ContactOpResult.Success
    } catch (e: OperationApplicationException) {
        ContactOpResult.Failure("Could not $action: the contacts provider rejected the change.", e)
    } catch (e: RemoteException) {
        ContactOpResult.Failure("Could not $action: the contacts provider is unavailable.", e)
    } catch (e: SecurityException) {
        ContactOpResult.Failure("Could not $action: contacts permission is missing.", e)
    } catch (e: IllegalArgumentException) {
        ContactOpResult.Failure("Could not $action: ${e.message ?: "invalid data"}.", e)
    }

    private companion object {
        /** Binder transactions cap around 1 MB; 400 ops per applyBatch stays well under it. */
        const val MAX_OPS_PER_BATCH = 400
    }
}
