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
    val organization: String? = null,   // company
    val jobTitle: String? = null,
    val nickname: String? = null,
    val websites: List<LabeledValue> = emptyList(),
    val addresses: List<LabeledValue> = emptyList(),
    /** Raw provider strings, e.g. "1990-08-12" or "--08-12" (no year). */
    val birthday: String? = null,
    val anniversary: String? = null,
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
    /** False for read-only accounts and SIMs whose write probe failed. */
    val writable: Boolean = true,
) {
    val key: String get() = "$type/$name"
}

/** One entry on the SIM card (EF_ADN record). */
data class SimContact(
    val indexOnIcc: Int?,        // null when the icc provider does not expose an index
    val name: String,
    val number: String,
    val subscriptionId: Int?,    // null on single-SIM/legacy path
)

/** Probe result for one SIM: what the icc provider actually allows. */
data class SimCapabilities(
    val canRead: Boolean,
    val canWrite: Boolean,
    val maxNameLength: Int = 14,
)
