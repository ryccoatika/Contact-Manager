package com.ryccoatika.contactmanager.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactAggregatorTest {

    private fun row(
        dataId: Long, rawId: Long, contactId: Long, mime: String?,
        data1: String?, name: String = "Contact $contactId",
        accType: String? = "com.google", accName: String? = "a@gmail.com",
        data2: String? = null, data3: String? = null, data4: String? = null,
        typeLabel: String? = null,
    ) = DataRow(
        dataId = dataId, rawContactId = rawId, contactId = contactId,
        mimeType = mime, data1 = data1, data2 = data2, data3 = data3, data4 = data4,
        typeLabel = typeLabel,
        accountType = accType, accountName = accName,
        displayName = name, photoThumbUri = null, starred = false,
    )

    private val PHONE = "vnd.android.cursor.item/phone_v2"
    private val EMAIL = "vnd.android.cursor.item/email_v2"
    private val NAME = "vnd.android.cursor.item/name"
    private val ORG = "vnd.android.cursor.item/organization"
    private val NICKNAME = "vnd.android.cursor.item/nickname"
    private val WEBSITE = "vnd.android.cursor.item/website"
    private val POSTAL = "vnd.android.cursor.item/postal-address_v2"
    private val EVENT = "vnd.android.cursor.item/contact_event"

    @Test
    fun `groups rows into one contact with one raw contact`() {
        val contacts = ContactAggregator.aggregate(listOf(
            row(1, 10, 100, PHONE, "+62812111"),
            row(2, 10, 100, EMAIL, "x@y.com"),
        ))
        assertEquals(1, contacts.size)
        assertEquals(1, contacts[0].rawContacts.size)
        assertEquals("+62812111", contacts[0].rawContacts[0].phones[0].value)
        assertEquals("x@y.com", contacts[0].rawContacts[0].emails[0].value)
    }

    @Test
    fun `same contact across two accounts yields two raw contacts`() {
        val contacts = ContactAggregator.aggregate(listOf(
            row(1, 10, 100, PHONE, "+62812111", accType = "com.google"),
            row(2, 11, 100, PHONE, "+62812111", accType = "com.whatsapp", accName = "WhatsApp"),
        ))
        assertEquals(1, contacts.size)
        assertEquals(2, contacts[0].rawContacts.size)
    }

    @Test
    fun `contact with no data rows still appears (name-only row)`() {
        val contacts = ContactAggregator.aggregate(listOf(
            row(0, 10, 100, null, null),
        ))
        assertEquals(1, contacts.size)
        assertEquals(0, contacts[0].rawContacts[0].phones.size)
    }

    @Test
    fun `duplicate phone values within raw contact deduped`() {
        val contacts = ContactAggregator.aggregate(listOf(
            row(1, 10, 100, PHONE, "+62812111"),
            row(2, 10, 100, PHONE, "+62812111"),
        ))
        assertEquals(1, contacts[0].rawContacts[0].phones.size)
    }

    @Test
    fun `sorted by display name case-insensitive`() {
        val contacts = ContactAggregator.aggregate(listOf(
            row(1, 10, 100, PHONE, "1", name = "zack"),
            row(2, 11, 101, PHONE, "2", name = "Anna"),
        ))
        assertEquals(listOf("Anna", "zack"), contacts.map { it.displayName })
    }

    @Test
    fun `blank display name falls back to phone then unnamed`() {
        val contacts = ContactAggregator.aggregate(listOf(
            row(1, 10, 100, PHONE, "+62899", name = ""),
        ))
        assertEquals("+62899", contacts[0].displayName)
    }

    @Test
    fun `structured name row fills given and family name`() {
        val contacts = ContactAggregator.aggregate(listOf(
            row(1, 10, 100, NAME, "Budi Santoso", data2 = "Budi", data3 = "Santoso"),
            row(2, 10, 100, PHONE, "+62812111"),
        ))
        assertEquals("Budi", contacts[0].rawContacts[0].givenName)
        assertEquals("Santoso", contacts[0].rawContacts[0].familyName)
    }

    @Test
    fun `missing or blank structured name parts yield null given and family name`() {
        val contacts = ContactAggregator.aggregate(listOf(
            row(1, 10, 100, NAME, "Budi", data2 = "Budi", data3 = ""),
            row(2, 11, 101, PHONE, "+62812111"),
        ))
        assertEquals("Budi", contacts[0].rawContacts[0].givenName)
        assertEquals(null, contacts[0].rawContacts[0].familyName)
        assertEquals(null, contacts[1].rawContacts[0].givenName)
        assertEquals(null, contacts[1].rawContacts[0].familyName)
    }

    @Test
    fun `extended fields parsed - org title, nickname, website, address, birthday, anniversary`() {
        val contacts = ContactAggregator.aggregate(listOf(
            row(1, 10, 100, ORG, "PT Maju", data4 = "Engineer"),
            row(2, 10, 100, NICKNAME, "Bud"),
            row(3, 10, 100, WEBSITE, "https://budi.dev", typeLabel = "Homepage"),
            row(4, 10, 100, POSTAL, "Jl. Merdeka 1, Jakarta", typeLabel = "Home"),
            row(5, 10, 100, EVENT, "1990-08-12", data2 = "3"),   // TYPE_BIRTHDAY
            row(6, 10, 100, EVENT, "2015-06-01", data2 = "1"),   // TYPE_ANNIVERSARY
        ))
        val raw = contacts[0].rawContacts[0]
        assertEquals("PT Maju", raw.organization)
        assertEquals("Engineer", raw.jobTitle)
        assertEquals("Bud", raw.nickname)
        assertEquals("https://budi.dev", raw.websites[0].value)
        assertEquals("Homepage", raw.websites[0].typeLabel)
        assertEquals("Jl. Merdeka 1, Jakarta", raw.addresses[0].value)
        assertEquals("1990-08-12", raw.birthday)
        assertEquals("2015-06-01", raw.anniversary)
    }

    @Test
    fun `type label carried through to phones and emails`() {
        val contacts = ContactAggregator.aggregate(listOf(
            row(1, 10, 100, PHONE, "+62812111", typeLabel = "Mobile"),
            row(2, 10, 100, EMAIL, "x@y.com", typeLabel = "Work"),
        ))
        assertEquals("Mobile", contacts[0].rawContacts[0].phones[0].typeLabel)
        assertEquals("Work", contacts[0].rawContacts[0].emails[0].typeLabel)
    }
}
