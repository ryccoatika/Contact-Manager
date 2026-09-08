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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ContactOpResult {
    data object Success : ContactOpResult

    data class Failure(
        val message: String,
        val cause: Throwable? = null,
    ) : ContactOpResult
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
    val birthday: String? = null, // "yyyy-MM-dd"
    val anniversary: String? = null,
)

interface ContactsWriter {
    suspend fun createContact(accountType: String?, accountName: String?, contact: EditableContact): ContactOpResult

    suspend fun updateRawContact(rawContactId: Long, contact: EditableContact): ContactOpResult

    suspend fun deleteRawContacts(rawContactIds: List<Long>): ContactOpResult

    /** Duplicates one raw contact (all data rows incl. photo) into the target account. */
    suspend fun copyRawContact(rawContactId: Long, targetType: String?, targetName: String?): ContactOpResult

    /** Duplicates raw contacts into the target account; sources are left untouched. */
    suspend fun copyRawContacts(
        rawContactIds: List<Long>,
        targetType: String?,
        targetName: String?,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ContactOpResult

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
class ContactsWriteRepository
    @Inject
    constructor(
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
                ops += ContentProviderOperation
                    .newInsert(RawContacts.CONTENT_URI)
                    .withValue(RawContacts.ACCOUNT_TYPE, accountType)
                    .withValue(RawContacts.ACCOUNT_NAME, accountName)
                    .build()
                // Back-references point at op 0, so a create always fits one chunk:
                // a single contact's ops stay far below MAX_OPS_PER_BATCH.
                ops += ContactOps.dataInsertOps(contact) { it.withValueBackReference(Data.RAW_CONTACT_ID, 0) }
                ContactOps.applyChunked(context.contentResolver, ops)
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
                val placeholders = ContactOps.MANAGED_MIME_TYPES.joinToString(",") { "?" }
                ops += ContentProviderOperation
                    .newDelete(Data.CONTENT_URI)
                    .withSelection(
                        "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE} IN ($placeholders)",
                        arrayOf(rawContactId.toString(), *ContactOps.MANAGED_MIME_TYPES),
                    ).build()
                ops += ContactOps.dataInsertOps(contact) { it.withValue(Data.RAW_CONTACT_ID, rawContactId) }
                ContactOps.applyChunked(context.contentResolver, ops)
            }
        }

        override suspend fun deleteRawContacts(rawContactIds: List<Long>): ContactOpResult =
            withContext(ioDispatcher) {
                runCatchingOp(
                    R.string.cwr_action_delete,
                ) { ContactOps.applyChunked(context.contentResolver, ContactOps.deleteOps(rawContactIds)) }
            }

        override suspend fun copyRawContact(
            rawContactId: Long,
            targetType: String?,
            targetName: String?,
        ): ContactOpResult = withContext(ioDispatcher) {
            runCatchingOp(R.string.cwr_action_copy) { insertCopy(rawContactId, targetType, targetName) }
        }

        override suspend fun copyRawContacts(
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
                    insertCopy(rawContactId, targetType, targetName)
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
                    strings.get(R.string.cwr_error_copy_count, failed, rawContactIds.size),
                    firstCause,
                )
            }
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
                    ContactOps.applyChunked(context.contentResolver, ContactOps.deleteOps(listOf(rawContactId)))
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
                    ContactOps.applyChunked(
                        context.contentResolver,
                        ContactOps.aggregationOps(rawContactIds, AggregationExceptions.TYPE_KEEP_TOGETHER),
                    )
                }
            }

        override suspend fun keepSeparate(rawContactIds: List<Long>): ContactOpResult =
            withContext(ioDispatcher) {
                runCatchingOp(R.string.cwr_action_separate) {
                    ContactOps.applyChunked(
                        context.contentResolver,
                        ContactOps.aggregationOps(rawContactIds, AggregationExceptions.TYPE_KEEP_SEPARATE),
                    )
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
                ops += ContactOps.mergeCopyOps(context.contentResolver, target.rawContactId, sources.map { it.rawContactId })
                val (readOnly, writable) = sources.partition {
                    AccountClassifier.classify(it.accountType) == AccountCapability.READ_ONLY
                }
                if (writable.isNotEmpty()) ops += ContactOps.deleteOps(writable.map { it.rawContactId })
                if (readOnly.isNotEmpty()) {
                    // App-managed rows can't be deleted; keep them aggregated with the target.
                    ops += ContactOps.aggregationOps(
                        listOf(target.rawContactId) + readOnly.map { it.rawContactId },
                        AggregationExceptions.TYPE_KEEP_TOGETHER,
                    )
                }
                ContactOps.applyChunked(context.contentResolver, ops)
            }
        }

        /** Inserts a duplicate of [rawContactId] with all its data rows into the target account. */
        private fun insertCopy(rawContactId: Long, targetType: String?, targetName: String?) {
            val ops = ArrayList<ContentProviderOperation>()
            ops += ContentProviderOperation
                .newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_TYPE, targetType)
                .withValue(RawContacts.ACCOUNT_NAME, targetName)
                .build()
            // Back-references point at op 0, so one contact per batch keeps every
            // data row in the same chunk as its raw-contact insert.
            ops += ContactOps.copyDataOps(context.contentResolver, rawContactId)
            ContactOps.applyChunked(context.contentResolver, ops)
        }

        private inline fun runCatchingOp(
            @StringRes action: Int,
            block: () -> Unit,
        ): ContactOpResult = try {
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
    }
