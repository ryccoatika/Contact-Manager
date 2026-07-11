package com.ryccoatika.contactmanager.domain.model

data class LabeledValue(
    val dataId: Long,
    val value: String,
    val typeLabel: String?,
)

data class RawContact(
    val rawContactId: Long,
    val accountType: String?,
    val accountName: String?,
    val givenName: String? = null,
    val familyName: String? = null,
    val phones: List<LabeledValue> = emptyList(),
    val emails: List<LabeledValue> = emptyList(),
    val organization: String? = null,
    val note: String? = null,
)

data class Contact(
    val contactId: Long,
    val displayName: String,
    val photoThumbnailUri: String? = null,
    val starred: Boolean = false,
    val rawContacts: List<RawContact> = emptyList(),
)

enum class AccountCapability { FULL_CRUD, READ_ONLY, SIM }

data class ContactAccount(
    val name: String?,
    val type: String?,
    val capability: AccountCapability,
    val contactCount: Int = 0,
) {
    val key: String get() = "$type/$name"
}
