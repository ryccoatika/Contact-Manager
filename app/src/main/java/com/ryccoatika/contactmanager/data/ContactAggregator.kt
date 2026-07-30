package com.ryccoatika.contactmanager.data

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
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
    val data2: String?,
    val data3: String?,
    val data4: String? = null,
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
            val nameRow = rawRows.firstOrNull { it.mimeType == StructuredName.CONTENT_ITEM_TYPE }
            val orgRow = rawRows.firstOrNull { it.mimeType == Organization.CONTENT_ITEM_TYPE }
            fun event(type: Int) = rawRows.firstOrNull {
                it.mimeType == Event.CONTENT_ITEM_TYPE &&
                    it.data2 == type.toString() &&
                    !it.data1.isNullOrBlank()
            }?.data1
            RawContact(
                rawContactId = rawId,
                accountType = first.accountType,
                accountName = first.accountName,
                givenName = nameRow?.data2?.takeIf { it.isNotBlank() },
                familyName = nameRow?.data3?.takeIf { it.isNotBlank() },
                phones = rawRows.filter { it.mimeType == Phone.CONTENT_ITEM_TYPE && !it.data1.isNullOrBlank() }
                    .distinctBy { it.data1 }
                    .map { LabeledValue(it.dataId, it.data1!!, it.typeLabel) },
                emails = rawRows.filter { it.mimeType == Email.CONTENT_ITEM_TYPE && !it.data1.isNullOrBlank() }
                    .distinctBy { it.data1 }
                    .map { LabeledValue(it.dataId, it.data1!!, it.typeLabel) },
                organization = orgRow?.data1?.takeIf { it.isNotBlank() },
                jobTitle = orgRow?.data4?.takeIf { it.isNotBlank() },
                nickname = rawRows.firstOrNull { it.mimeType == Nickname.CONTENT_ITEM_TYPE }
                    ?.data1?.takeIf { it.isNotBlank() },
                websites = rawRows.filter { it.mimeType == Website.CONTENT_ITEM_TYPE && !it.data1.isNullOrBlank() }
                    .distinctBy { it.data1 }
                    .map { LabeledValue(it.dataId, it.data1!!, it.typeLabel) },
                addresses = rawRows.filter { it.mimeType == StructuredPostal.CONTENT_ITEM_TYPE && !it.data1.isNullOrBlank() }
                    .distinctBy { it.data1 }
                    .map { LabeledValue(it.dataId, it.data1!!, it.typeLabel) },
                birthday = event(Event.TYPE_BIRTHDAY),
                anniversary = event(Event.TYPE_ANNIVERSARY),
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
