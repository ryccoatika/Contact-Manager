package com.ryccoatika.contactmanager.data

import android.content.ContentProviderOperation
import android.content.Context
import android.content.OperationApplicationException
import android.database.Cursor
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.ContactsContract.AggregationExceptions
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import androidx.annotation.StringRes
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.di.IoDispatcher
import com.ryccoatika.contactmanager.domain.AccountClassifier
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.RawContact
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
    val organization: String?, // company
    val note: String?,
    val jobTitle: String? = null,
    val nickname: String? = null,
    val websites: List<String> = emptyList(),
    val addresses: List<String> = emptyList(),
    val birthday: String? = null,      // "yyyy-MM-dd"
    val anniversary: String? = null,
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

    /** Aggregates all [rawContactIds] into one contact (KEEP_TOGETHER per pair, reversible). */
    suspend fun linkContacts(rawContactIds: List<Long>): ContactOpResult

    /** Forces all [rawContactIds] apart (KEEP_SEPARATE per pair). */
    suspend fun keepSeparate(rawContactIds: List<Long>): ContactOpResult

    /**
     * Physically merges [sources] into [target]: copies their data rows (deduped
     * against the target by mimetype + data1, photos skipped when the target
     * already has one), then deletes the writable sources — copy-then-delete, so
     * a mid-flight failure can duplicate data but never lose it. Read-only
     * sources (app-managed, e.g. WhatsApp) can't be deleted: their data is
     * copied and the raw contact is KEEP_TOGETHER-linked to the target instead.
     */
    suspend fun mergeContacts(target: RawContact, sources: List<RawContact>): ContactOpResult
}

@Singleton
class ContactsWriteRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val strings: StringProvider,
) : ContactsWriter {

    override suspend fun createContact(
        accountType: String?,
        accountName: String?,
        contact: EditableContact,
    ): ContactOpResult = withContext(ioDispatcher) {
        runCatchingOp(R.string.cwr_action_create) {
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
        runCatchingOp(R.string.cwr_action_update) {
            val ops = ArrayList<ContentProviderOperation>()
            // Only clear the mimetypes the editor manages, so fields it doesn't
            // touch (IM, relation, group membership, photo, …) survive the edit.
            val placeholders = MANAGED_MIME_TYPES.joinToString(",") { "?" }
            ops += ContentProviderOperation.newDelete(Data.CONTENT_URI)
                .withSelection(
                    "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE} IN ($placeholders)",
                    arrayOf(rawContactId.toString(), *MANAGED_MIME_TYPES),
                )
                .build()
            ops += dataInsertOps(contact) { it.withValue(Data.RAW_CONTACT_ID, rawContactId) }
            applyChunked(ops)
        }
    }

    override suspend fun deleteRawContacts(rawContactIds: List<Long>): ContactOpResult =
        withContext(ioDispatcher) {
            runCatchingOp(R.string.cwr_action_delete) { applyChunked(deleteOps(rawContactIds)) }
        }

    override suspend fun copyRawContact(
        rawContactId: Long,
        targetType: String?,
        targetName: String?,
    ): ContactOpResult = withContext(ioDispatcher) {
        runCatchingOp(R.string.cwr_action_copy) { insertCopy(rawContactId, targetType, targetName) }
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
                strings.get(R.string.cwr_error_move_count, failed, rawContactIds.size),
                firstCause,
            )
        }
    }

    override suspend fun linkContacts(rawContactIds: List<Long>): ContactOpResult =
        withContext(ioDispatcher) {
            runCatchingOp(R.string.cwr_action_link) {
                applyChunked(aggregationOps(rawContactIds, AggregationExceptions.TYPE_KEEP_TOGETHER))
            }
        }

    override suspend fun keepSeparate(rawContactIds: List<Long>): ContactOpResult =
        withContext(ioDispatcher) {
            runCatchingOp(R.string.cwr_action_separate) {
                applyChunked(aggregationOps(rawContactIds, AggregationExceptions.TYPE_KEEP_SEPARATE))
            }
        }

    override suspend fun mergeContacts(
        target: RawContact,
        sources: List<RawContact>,
    ): ContactOpResult = withContext(ioDispatcher) {
        runCatchingOp(R.string.cwr_action_merge) {
            if (sources.isEmpty()) return@runCatchingOp
            val ops = ArrayList<ContentProviderOperation>()
            // Copy first, delete last: a failure in between duplicates data, never loses it.
            ops += mergeCopyOps(target.rawContactId, sources.map { it.rawContactId })
            val (readOnly, writable) = sources.partition {
                AccountClassifier.classify(it.accountType) == AccountCapability.READ_ONLY
            }
            if (writable.isNotEmpty()) ops += deleteOps(writable.map { it.rawContactId })
            if (readOnly.isNotEmpty()) {
                // App-managed rows can't be deleted; keep them aggregated with the target.
                ops += aggregationOps(
                    listOf(target.rawContactId) + readOnly.map { it.rawContactId },
                    AggregationExceptions.TYPE_KEEP_TOGETHER,
                )
            }
            applyChunked(ops)
        }
    }

    /** One AggregationExceptions update per unordered pair of [rawContactIds]. */
    private fun aggregationOps(rawContactIds: List<Long>, type: Int): List<ContentProviderOperation> {
        val ids = rawContactIds.distinct()
        val ops = ArrayList<ContentProviderOperation>()
        for (i in ids.indices) {
            for (j in i + 1 until ids.size) {
                ops += ContentProviderOperation.newUpdate(AggregationExceptions.CONTENT_URI)
                    .withValue(AggregationExceptions.TYPE, type)
                    .withValue(AggregationExceptions.RAW_CONTACT_ID1, ids[i])
                    .withValue(AggregationExceptions.RAW_CONTACT_ID2, ids[j])
                    .build()
            }
        }
        return ops
    }

    /**
     * Inserts copying every data row of [sourceRawContactIds] into the merge
     * target, skipping rows the target already has (same mimetype + data1),
     * group memberships (group ids are account-local), and photos when the
     * target already has one.
     */
    private fun mergeCopyOps(
        targetRawContactId: Long,
        sourceRawContactIds: List<Long>,
    ): List<ContentProviderOperation> {
        val seen = HashSet<Pair<String, String?>>()
        var hasPhoto = false
        context.contentResolver.query(
            Data.CONTENT_URI,
            arrayOf(Data.MIMETYPE, Data.DATA1),
            "${Data.RAW_CONTACT_ID}=?",
            arrayOf(targetRawContactId.toString()),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val mimeType = c.getString(0) ?: continue
                if (mimeType == Photo.CONTENT_ITEM_TYPE) hasPhoto = true
                else seen += mimeType to c.getString(1)
            }
        }

        val ops = ArrayList<ContentProviderOperation>()
        context.contentResolver.query(
            Data.CONTENT_URI,
            arrayOf(Data.MIMETYPE, *GENERIC_DATA_COLUMNS),
            "${Data.RAW_CONTACT_ID} IN (${sourceRawContactIds.joinToString(",")})",
            null,
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val mimeType = c.getString(0) ?: continue
                if (mimeType == GroupMembership.CONTENT_ITEM_TYPE) continue
                val builder = ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValue(Data.RAW_CONTACT_ID, targetRawContactId)
                    .withValue(Data.MIMETYPE, mimeType)
                if (mimeType == Photo.CONTENT_ITEM_TYPE) {
                    if (hasPhoto) continue
                    val blob = c.getBlob(c.getColumnIndexOrThrow(Photo.PHOTO)) ?: continue
                    builder.withValue(Photo.PHOTO, blob)
                    hasPhoto = true
                } else {
                    if (!seen.add(mimeType to c.getString(1))) continue
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
        if (!contact.organization.isNullOrBlank() || !contact.jobTitle.isNullOrBlank()) {
            val builder = attach(ContentProviderOperation.newInsert(Data.CONTENT_URI))
                .withValue(Data.MIMETYPE, Organization.CONTENT_ITEM_TYPE)
            contact.organization?.takeIf { it.isNotBlank() }?.let { builder.withValue(Organization.COMPANY, it) }
            contact.jobTitle?.takeIf { it.isNotBlank() }?.let { builder.withValue(Organization.TITLE, it) }
            ops += builder.build()
        }
        if (!contact.nickname.isNullOrBlank()) {
            ops += attach(ContentProviderOperation.newInsert(Data.CONTENT_URI))
                .withValue(Data.MIMETYPE, Nickname.CONTENT_ITEM_TYPE)
                .withValue(Nickname.NAME, contact.nickname)
                .build()
        }
        contact.websites.map { it.trim() }.filter { it.isNotBlank() }.forEach { url ->
            ops += attach(ContentProviderOperation.newInsert(Data.CONTENT_URI))
                .withValue(Data.MIMETYPE, Website.CONTENT_ITEM_TYPE)
                .withValue(Website.URL, url)
                .withValue(Website.TYPE, Website.TYPE_OTHER)
                .build()
        }
        contact.addresses.map { it.trim() }.filter { it.isNotBlank() }.forEach { address ->
            ops += attach(ContentProviderOperation.newInsert(Data.CONTENT_URI))
                .withValue(Data.MIMETYPE, StructuredPostal.CONTENT_ITEM_TYPE)
                .withValue(StructuredPostal.FORMATTED_ADDRESS, address)
                .withValue(StructuredPostal.TYPE, StructuredPostal.TYPE_HOME)
                .build()
        }
        contact.birthday?.takeIf { it.isNotBlank() }?.let { date ->
            ops += attach(ContentProviderOperation.newInsert(Data.CONTENT_URI))
                .withValue(Data.MIMETYPE, Event.CONTENT_ITEM_TYPE)
                .withValue(Event.START_DATE, date)
                .withValue(Event.TYPE, Event.TYPE_BIRTHDAY)
                .build()
        }
        contact.anniversary?.takeIf { it.isNotBlank() }?.let { date ->
            ops += attach(ContentProviderOperation.newInsert(Data.CONTENT_URI))
                .withValue(Data.MIMETYPE, Event.CONTENT_ITEM_TYPE)
                .withValue(Event.START_DATE, date)
                .withValue(Event.TYPE, Event.TYPE_ANNIVERSARY)
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

    private inline fun runCatchingOp(@StringRes action: Int, block: () -> Unit): ContactOpResult = try {
        block()
        ContactOpResult.Success
    } catch (e: OperationApplicationException) {
        ContactOpResult.Failure(strings.get(R.string.cwr_error_rejected, strings.get(action)), e)
    } catch (e: RemoteException) {
        ContactOpResult.Failure(strings.get(R.string.cwr_error_unavailable, strings.get(action)), e)
    } catch (e: SecurityException) {
        ContactOpResult.Failure(strings.get(R.string.cwr_error_permission, strings.get(action)), e)
    } catch (e: IllegalArgumentException) {
        ContactOpResult.Failure(
            strings.get(
                R.string.cwr_error_generic,
                strings.get(action),
                e.message ?: strings.get(R.string.cwr_error_invalid_data),
            ),
            e,
        )
    }

    private companion object {
        /** Binder transactions cap around 1 MB; 400 ops per applyBatch stays well under it. */
        const val MAX_OPS_PER_BATCH = 400

        /** Mimetypes the editor owns — cleared and rewritten on update; others survive. */
        val MANAGED_MIME_TYPES = arrayOf(
            StructuredName.CONTENT_ITEM_TYPE,
            Phone.CONTENT_ITEM_TYPE,
            Email.CONTENT_ITEM_TYPE,
            Organization.CONTENT_ITEM_TYPE,
            Nickname.CONTENT_ITEM_TYPE,
            Website.CONTENT_ITEM_TYPE,
            StructuredPostal.CONTENT_ITEM_TYPE,
            Event.CONTENT_ITEM_TYPE,
            Note.CONTENT_ITEM_TYPE,
        )

        val GENERIC_DATA_COLUMNS = arrayOf(
            Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4, Data.DATA5,
            Data.DATA6, Data.DATA7, Data.DATA8, Data.DATA9, Data.DATA10,
            Data.DATA11, Data.DATA12, Data.DATA13, Data.DATA14, Data.DATA15,
        )
    }
}
