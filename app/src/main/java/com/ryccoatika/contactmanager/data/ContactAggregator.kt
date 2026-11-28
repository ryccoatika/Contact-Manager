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
    private val USED_MIME_TYPES = setOf(
        StructuredName.CONTENT_ITEM_TYPE,
        Organization.CONTENT_ITEM_TYPE,
        Nickname.CONTENT_ITEM_TYPE,
        Note.CONTENT_ITEM_TYPE,
        Event.CONTENT_ITEM_TYPE,
        Phone.CONTENT_ITEM_TYPE,
        Email.CONTENT_ITEM_TYPE,
        Website.CONTENT_ITEM_TYPE,
        StructuredPostal.CONTENT_ITEM_TYPE,
    )

    /** True for the mimetypes whose DATA1..DATA4 the aggregator reads; other rows only register membership. */
    fun usesData(mimeType: String?): Boolean = mimeType in USED_MIME_TYPES

    fun aggregate(rows: List<DataRow>): List<Contact> {
        val acc = ContactAccumulator()
        rows.forEach(acc::add)
        return acc.build()
    }
}

/**
 * Streaming form of the aggregation: rows are folded into compact per-contact /
 * per-raw-contact builders and never retained, so peak memory is the result
 * itself rather than the whole Data table. Semantics: first row wins per field.
 */
class ContactAccumulator {
    private val contacts = LinkedHashMap<Long, ContactBuilder>()
    private val pool = HashMap<String, String>()

    var rowCount: Int = 0
        private set

    fun add(row: DataRow) {
        rowCount++
        val contact = contacts.getOrPut(row.contactId) {
            ContactBuilder(row.contactId, row.displayName, row.starred)
        }
        if (contact.photo == null) contact.photo = row.photoThumbUri
        val raw = contact.raws.getOrPut(row.rawContactId) {
            RawBuilder(row.rawContactId, pooled(row.accountType), pooled(row.accountName))
        }
        raw.add(row)
    }

    fun build(): List<Contact> =
        contacts.values
            .map { it.build() }
            .sortedBy { it.displayName.lowercase() }

    private fun pooled(value: String?): String? = value?.let { pool.getOrPut(it) { it } }

    private class ContactBuilder(
        val contactId: Long,
        val displayName: String?,
        val starred: Boolean,
    ) {
        var photo: String? = null
        val raws = LinkedHashMap<Long, RawBuilder>()

        fun build(): Contact {
            val rawContacts = raws.values.map { it.build() }
            val fallbackName = rawContacts.firstNotNullOfOrNull { it.phones.firstOrNull()?.value }
                ?: rawContacts.firstNotNullOfOrNull { it.emails.firstOrNull()?.value }
                ?: "(unnamed)"
            return Contact(
                contactId = contactId,
                displayName = displayName?.takeIf { it.isNotBlank() } ?: fallbackName,
                photoThumbnailUri = photo,
                starred = starred,
                rawContacts = rawContacts,
            )
        }
    }

    private class RawBuilder(
        val rawContactId: Long,
        val accountType: String?,
        val accountName: String?,
    ) {
        private var hasName = false
        private var givenName: String? = null
        private var familyName: String? = null
        private var hasOrg = false
        private var organization: String? = null
        private var jobTitle: String? = null
        private var hasNickname = false
        private var nickname: String? = null
        private var hasNote = false
        private var note: String? = null
        private var birthday: String? = null
        private var anniversary: String? = null
        private var phones: MutableList<LabeledValue>? = null
        private var emails: MutableList<LabeledValue>? = null
        private var websites: MutableList<LabeledValue>? = null
        private var addresses: MutableList<LabeledValue>? = null

        fun add(row: DataRow) {
            when (row.mimeType) {
                StructuredName.CONTENT_ITEM_TYPE -> if (!hasName) {
                    hasName = true
                    givenName = row.data2?.takeIf { it.isNotBlank() }
                    familyName = row.data3?.takeIf { it.isNotBlank() }
                }

                Organization.CONTENT_ITEM_TYPE -> if (!hasOrg) {
                    hasOrg = true
                    organization = row.data1?.takeIf { it.isNotBlank() }
                    jobTitle = row.data4?.takeIf { it.isNotBlank() }
                }

                Nickname.CONTENT_ITEM_TYPE -> if (!hasNickname) {
                    hasNickname = true
                    nickname = row.data1?.takeIf { it.isNotBlank() }
                }

                Note.CONTENT_ITEM_TYPE -> if (!hasNote) {
                    hasNote = true
                    note = row.data1
                }

                Event.CONTENT_ITEM_TYPE -> if (!row.data1.isNullOrBlank()) {
                    if (birthday == null && row.data2 == Event.TYPE_BIRTHDAY.toString()) birthday = row.data1
                    if (anniversary == null && row.data2 == Event.TYPE_ANNIVERSARY.toString()) anniversary = row.data1
                }

                Phone.CONTENT_ITEM_TYPE -> phones = addDistinct(phones, row)

                Email.CONTENT_ITEM_TYPE -> emails = addDistinct(emails, row)

                Website.CONTENT_ITEM_TYPE -> websites = addDistinct(websites, row)

                StructuredPostal.CONTENT_ITEM_TYPE -> addresses = addDistinct(addresses, row)
            }
        }

        /** Non-blank DATA1, distinct by value (first wins). Lists stay tiny, so a scan beats a set. */
        private fun addDistinct(list: MutableList<LabeledValue>?, row: DataRow): MutableList<LabeledValue>? {
            val value = row.data1
            if (value.isNullOrBlank()) return list
            val target = list ?: ArrayList(2)
            if (target.none { it.value == value }) target += LabeledValue(row.dataId, value, row.typeLabel)
            return target
        }

        fun build() = RawContact(
            rawContactId = rawContactId,
            accountType = accountType,
            accountName = accountName,
            givenName = givenName,
            familyName = familyName,
            phones = phones.orEmpty(),
            emails = emails.orEmpty(),
            organization = organization,
            jobTitle = jobTitle,
            nickname = nickname,
            websites = websites.orEmpty(),
            addresses = addresses.orEmpty(),
            birthday = birthday,
            anniversary = anniversary,
            note = note,
        )
    }
}
