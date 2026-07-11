package com.ryccoatika.contactmanager.data

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.LabeledValue
import com.ryccoatika.contactmanager.domain.model.RawContact

/** One row of the bulk Data-table query. Pure value type so aggregation is JVM-testable. */
data class DataRow(
    val dataId: Long,
    val rawContactId: Long,
    val contactId: Long,
    val mimeType: String?,
    val data1: String?,
    val typeLabel: String?,
    val accountType: String?,
    val accountName: String?,
    val displayName: String?,
    val photoThumbUri: String?,
    val starred: Boolean,
)

object ContactAggregator {

    fun aggregate(rows: List<DataRow>): List<Contact> =
        rows.groupBy { it.contactId }
            .map { (contactId, contactRows) -> buildContact(contactId, contactRows) }
            .sortedBy { it.displayName.lowercase() }

    private fun buildContact(contactId: Long, rows: List<DataRow>): Contact {
        val rawContacts = rows.groupBy { it.rawContactId }.map { (rawId, rawRows) ->
            val first = rawRows.first()
            RawContact(
                rawContactId = rawId,
                accountType = first.accountType,
                accountName = first.accountName,
                phones = rawRows.filter { it.mimeType == Phone.CONTENT_ITEM_TYPE && !it.data1.isNullOrBlank() }
                    .distinctBy { it.data1 }
                    .map { LabeledValue(it.dataId, it.data1!!, it.typeLabel) },
                emails = rawRows.filter { it.mimeType == Email.CONTENT_ITEM_TYPE && !it.data1.isNullOrBlank() }
                    .distinctBy { it.data1 }
                    .map { LabeledValue(it.dataId, it.data1!!, it.typeLabel) },
                organization = rawRows.firstOrNull { it.mimeType == Organization.CONTENT_ITEM_TYPE }?.data1,
                note = rawRows.firstOrNull { it.mimeType == Note.CONTENT_ITEM_TYPE }?.data1,
            )
        }
        val first = rows.first()
        val fallbackName = rawContacts.firstNotNullOfOrNull { it.phones.firstOrNull()?.value }
            ?: rawContacts.firstNotNullOfOrNull { it.emails.firstOrNull()?.value }
            ?: "(unnamed)"
        return Contact(
            contactId = contactId,
            displayName = first.displayName?.takeIf { it.isNotBlank() } ?: fallbackName,
            photoThumbnailUri = rows.firstNotNullOfOrNull { it.photoThumbUri },
            starred = first.starred,
            rawContacts = rawContacts,
        )
    }
}
