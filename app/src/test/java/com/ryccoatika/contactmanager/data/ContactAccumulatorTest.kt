package com.ryccoatika.contactmanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ContactAccumulatorTest {
    private val mimes = listOf(
        "vnd.android.cursor.item/name",
        "vnd.android.cursor.item/organization",
        "vnd.android.cursor.item/nickname",
        "vnd.android.cursor.item/note",
        "vnd.android.cursor.item/contact_event",
        "vnd.android.cursor.item/phone_v2",
        "vnd.android.cursor.item/email_v2",
        "vnd.android.cursor.item/website",
        "vnd.android.cursor.item/postal-address_v2",
        "vnd.android.cursor.item/vnd.com.whatsapp.profile", // unknown custom
        null,
    )
    private val pool = listOf(null, "", "  ", "a", "b", "+62811", "+62822", "x@y.com", "1990-08-12", "Eng")
    private val eventTypes = listOf("1", "3", "2", null)
    private val displayNames = listOf(null, "", "   ", "Zed", "amy", "Bob")
    private val photos = listOf(null, "content://photo/1", "content://photo/2")
    private val accounts = listOf(
        "com.google" to "a@gmail.com",
        "com.whatsapp" to "WhatsApp",
        null to null,
        "com.samsung" to "phone",
    )

    private fun randomRows(rnd: Random): List<DataRow> {
        val contactCount = rnd.nextInt(1, 6)

        // Contact-level columns are constant per contact; a raw id belongs to one contact.
        class C(
            val id: Long,
            val name: String?,
            val photo: String?,
            val starred: Boolean,
            val raws: List<Long>,
        )
        var nextRaw = 1000L
        val cs = List(contactCount) { i ->
            val raws = List(rnd.nextInt(1, 4)) { nextRaw++ }
            C(
                100L + i,
                displayNames.random(rnd),
                photos.random(rnd),
                rnd.nextBoolean(),
                raws,
            )
        }
        val accountOf = cs.flatMap { it.raws }.associateWith { accounts.random(rnd) }
        return List(rnd.nextInt(0, 61)) { n ->
            val c = cs.random(rnd)
            val raw = c.raws.random(rnd)
            val mime = mimes.random(rnd)
            val acc = accountOf.getValue(raw)
            DataRow(
                dataId = n.toLong(),
                rawContactId = raw,
                contactId = c.id,
                mimeType = mime,
                data1 = pool.random(rnd),
                data2 = if (mime == "vnd.android.cursor.item/contact_event") {
                    eventTypes.random(rnd)
                } else {
                    pool.random(rnd)
                },
                data3 = pool.random(rnd),
                data4 = pool.random(rnd),
                typeLabel = pool.random(rnd),
                accountType = acc.first,
                accountName = acc.second,
                displayName = c.name,
                photoThumbUri = c.photo,
                starred = c.starred,
            )
        }
    }

    @Test
    fun `accumulator equals reference aggregator on random data`() {
        val rnd = Random(20261127)
        repeat(500) { iteration ->
            val rows = randomRows(rnd)
            val acc = ContactAccumulator()
            rows.forEach { acc.add(it) }
            assertEquals(rows.size, acc.rowCount)
            assertEquals(
                "iteration $iteration rows=$rows",
                ReferenceAggregator.aggregate(rows),
                acc.build(),
            )
        }
    }

    @Test
    fun `aggregate wrapper equals reference aggregator on random data`() {
        val rnd = Random(42)
        repeat(300) {
            val rows = randomRows(rnd)
            assertEquals(ReferenceAggregator.aggregate(rows), ContactAggregator.aggregate(rows))
        }
    }

    @Test
    fun `empty accumulator builds empty list`() {
        val acc = ContactAccumulator()
        assertEquals(0, acc.rowCount)
        assertTrue(acc.build().isEmpty())
    }

    @Test
    fun `usesData is true only for the nine aggregated mimetypes`() {
        mimes.take(9).forEach { assertTrue(it, ContactAggregator.usesData(it)) }
        assertFalse(ContactAggregator.usesData("vnd.android.cursor.item/vnd.com.whatsapp.profile"))
        assertFalse(ContactAggregator.usesData("vnd.android.cursor.item/group_membership"))
        assertFalse(ContactAggregator.usesData(null))
    }
}
