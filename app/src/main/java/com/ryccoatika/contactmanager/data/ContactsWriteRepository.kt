package com.ryccoatika.contactmanager.data

import android.content.ContentProviderOperation
import android.content.Context
import android.content.OperationApplicationException
import android.database.Cursor
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import com.ryccoatika.contactmanager.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
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

    /** Duplicates one raw contact (all data rows incl. photo) into the target account. */
    suspend fun copyRawContact(rawContactId: Long, targetType: String?, targetName: String?): ContactOpResult

    /**
     * Moves raw contacts into the target account, copy-then-delete per contact:
     * a mid-flight failure can leave a duplicate, never lose data.
     */
    suspend fun moveRawContacts(
        rawContactIds: List<Long>,
        targetType: String?,
        targetName: String?,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ContactOpResult
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
            runCatchingOp("delete contact") { applyChunked(deleteOps(rawContactIds)) }
        }

    override suspend fun copyRawContact(
        rawContactId: Long,
        targetType: String?,
        targetName: String?,
    ): ContactOpResult = withContext(ioDispatcher) {
        runCatchingOp("copy contact") { insertCopy(rawContactId, targetType, targetName) }
    }

    override suspend fun moveRawContacts(
        rawContactIds: List<Long>,
        targetType: String?,
        targetName: String?,
        onProgress: (done: Int, total: Int) -> Unit,
    ): ContactOpResult = withContext(ioDispatcher) {
        var failed = 0
        var firstCause: Throwable? = null
        rawContactIds.forEachIndexed { index, rawContactId ->
            ensureActive()
            try {
                // Copy first; delete only after the copy landed. A failure in
                // between leaves a duplicate, never loses the contact.
                insertCopy(rawContactId, targetType, targetName)
                applyChunked(deleteOps(listOf(rawContactId)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                if (firstCause == null) firstCause = e
            }
            onProgress(index + 1, rawContactIds.size)
        }
        if (failed == 0) {
            ContactOpResult.Success
        } else {
            ContactOpResult.Failure(
                "Could not move $failed of ${rawContactIds.size} contacts.",
                firstCause,
            )
        }
    }

    private fun deleteOps(rawContactIds: List<Long>): List<ContentProviderOperation> =
        rawContactIds.chunked(MAX_OPS_PER_BATCH).map { ids ->
            ContentProviderOperation.newDelete(RawContacts.CONTENT_URI)
                .withSelection(
                    "${RawContacts._ID} IN (${ids.joinToString(",")})",
                    null,
                )
                .build()
        }

    /** Inserts a duplicate of [rawContactId] with all its data rows into the target account. */
    private fun insertCopy(rawContactId: Long, targetType: String?, targetName: String?) {
        val ops = ArrayList<ContentProviderOperation>()
        ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
            .withValue(RawContacts.ACCOUNT_TYPE, targetType)
            .withValue(RawContacts.ACCOUNT_NAME, targetName)
            .build()
        // Back-references point at op 0, so one contact per batch keeps every
        // data row in the same chunk as its raw-contact insert.
        ops += copyDataOps(rawContactId)
        applyChunked(ops)
    }

    private fun copyDataOps(sourceRawContactId: Long): List<ContentProviderOperation> {
        val ops = ArrayList<ContentProviderOperation>()
        context.contentResolver.query(
            Data.CONTENT_URI,
            arrayOf(Data.MIMETYPE, *GENERIC_DATA_COLUMNS),
            "${Data.RAW_CONTACT_ID}=?",
            arrayOf(sourceRawContactId.toString()),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val mimeType = c.getString(0) ?: continue
                // Group memberships reference group ids that only exist in the
                // source account; copying them would corrupt the target.
                if (mimeType == GroupMembership.CONTENT_ITEM_TYPE) continue
                val builder = ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                    .withValue(Data.MIMETYPE, mimeType)
                if (mimeType == Photo.CONTENT_ITEM_TYPE) {
                    // DATA14 holds a photo-file id tied to the source raw
                    // contact, so only the blob (DATA15) is carried over.
                    val blob = c.getBlob(c.getColumnIndexOrThrow(Photo.PHOTO)) ?: continue
                    builder.withValue(Photo.PHOTO, blob)
                } else {
                    GENERIC_DATA_COLUMNS.forEachIndexed { i, column ->
                        val index = i + 1 // offset by the MIMETYPE column
                        if (!c.isNull(index) && c.getType(index) != Cursor.FIELD_TYPE_BLOB) {
                            builder.withValue(column, c.getString(index))
                        }
                    }
                }
                ops += builder.build()
            }
        }
        return ops
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

        val GENERIC_DATA_COLUMNS = arrayOf(
            Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4, Data.DATA5,
            Data.DATA6, Data.DATA7, Data.DATA8, Data.DATA9, Data.DATA10,
            Data.DATA11, Data.DATA12, Data.DATA13, Data.DATA14, Data.DATA15,
        )
    }
}
