package com.ryccoatika.contactmanager.data

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.database.Cursor
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

/**
 * ContentProviderOperation builders for ContactsWriteRepository — the batch
 * plumbing, separated from the CRUD orchestration and its error mapping.
 */
internal object ContactOps {
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
        Data.DATA1,
        Data.DATA2,
        Data.DATA3,
        Data.DATA4,
        Data.DATA5,
        Data.DATA6,
        Data.DATA7,
        Data.DATA8,
        Data.DATA9,
        Data.DATA10,
        Data.DATA11,
        Data.DATA12,
        Data.DATA13,
        Data.DATA14,
        Data.DATA15,
    )

    fun applyChunked(resolver: ContentResolver, ops: List<ContentProviderOperation>) {
        ops.chunked(MAX_OPS_PER_BATCH).forEach { chunk ->
            resolver.applyBatch(ContactsContract.AUTHORITY, ArrayList(chunk))
        }
    }

    fun deleteOps(rawContactIds: List<Long>): List<ContentProviderOperation> =
        rawContactIds.chunked(MAX_OPS_PER_BATCH).map { ids ->
            ContentProviderOperation
                .newDelete(RawContacts.CONTENT_URI)
                .withSelection(
                    "${RawContacts._ID} IN (${ids.joinToString(",")})",
                    null,
                ).build()
        }

    /** One AggregationExceptions update per unordered pair of [rawContactIds]. */
    fun aggregationOps(rawContactIds: List<Long>, type: Int): List<ContentProviderOperation> {
        val ids = rawContactIds.distinct()
        val ops = ArrayList<ContentProviderOperation>()
        for (i in ids.indices) {
            for (j in i + 1 until ids.size) {
                ops += ContentProviderOperation
                    .newUpdate(AggregationExceptions.CONTENT_URI)
                    .withValue(AggregationExceptions.TYPE, type)
                    .withValue(AggregationExceptions.RAW_CONTACT_ID1, ids[i])
                    .withValue(AggregationExceptions.RAW_CONTACT_ID2, ids[j])
                    .build()
            }
        }
        return ops
    }

    fun copyDataOps(resolver: ContentResolver, sourceRawContactId: Long): List<ContentProviderOperation> {
        val ops = ArrayList<ContentProviderOperation>()
        resolver
            .query(
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
                    val builder = ContentProviderOperation
                        .newInsert(Data.CONTENT_URI)
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

    /**
     * Inserts copying every data row of [sourceRawContactIds] into the merge
     * target, skipping rows the target already has (same mimetype + data1),
     * group memberships (group ids are account-local), and photos when the
     * target already has one.
     */
    fun mergeCopyOps(
        resolver: ContentResolver,
        targetRawContactId: Long,
        sourceRawContactIds: List<Long>,
    ): List<ContentProviderOperation> {
        val seen = HashSet<Pair<String, String?>>()
        var hasPhoto = false
        resolver
            .query(
                Data.CONTENT_URI,
                arrayOf(Data.MIMETYPE, Data.DATA1),
                "${Data.RAW_CONTACT_ID}=?",
                arrayOf(targetRawContactId.toString()),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val mimeType = c.getString(0) ?: continue
                    if (mimeType == Photo.CONTENT_ITEM_TYPE) {
                        hasPhoto = true
                    } else {
                        seen += mimeType to c.getString(1)
                    }
                }
            }

        val ops = ArrayList<ContentProviderOperation>()
        resolver
            .query(
                Data.CONTENT_URI,
                arrayOf(Data.MIMETYPE, *GENERIC_DATA_COLUMNS),
                "${Data.RAW_CONTACT_ID} IN (${sourceRawContactIds.joinToString(",")})",
                null,
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val mimeType = c.getString(0) ?: continue
                    if (mimeType == GroupMembership.CONTENT_ITEM_TYPE) continue
                    val builder = ContentProviderOperation
                        .newInsert(Data.CONTENT_URI)
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

    /** Data-row inserts for every non-blank field; [attach] binds the raw contact id. */
    fun dataInsertOps(
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
}
