package com.ryccoatika.contactmanager.domain.vcard

/** One vCard's worth of contact data — mirrors what the app can store. */
data class VCardContact(
    val displayName: String,
    val givenName: String? = null,
    val familyName: String? = null,
    val phones: List<Pair<String, String?>> = emptyList(), // value to typeLabel
    val emails: List<Pair<String, String?>> = emptyList(),
    val organization: String? = null,
    val jobTitle: String? = null,
    val nickname: String? = null,
    val websites: List<String> = emptyList(),
    val addresses: List<String> = emptyList(), // one formatted string each
    val birthday: String? = null,
    val anniversary: String? = null,
    val note: String? = null,
    val photo: ByteArray? = null,
) {
    // ByteArray needs manual equals for test assertions.
    override fun equals(other: Any?): Boolean = other is VCardContact &&
        displayName == other.displayName && givenName == other.givenName &&
        familyName == other.familyName && phones == other.phones &&
        emails == other.emails && organization == other.organization &&
        jobTitle == other.jobTitle && nickname == other.nickname &&
        websites == other.websites && addresses == other.addresses &&
        birthday == other.birthday && anniversary == other.anniversary &&
        note == other.note && photo.contentEquals(other.photo)

    override fun hashCode(): Int = displayName.hashCode()
}

/** Parser output: parsed cards plus how many malformed ones were skipped. */
data class VCardParseResult(
    val contacts: List<VCardContact>,
    val skippedCards: Int,
)
