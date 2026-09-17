package com.ryccoatika.contactmanager.domain.vcard

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class VCardParserTest {
    @Test fun `round trips every field through the writer`() {
        val original = listOf(
            VCardContact(
                displayName = "Budi Santoso",
                givenName = "Budi",
                familyName = "Santoso",
                phones = listOf("+62812111" to "Mobile", "021555" to null),
                emails = listOf("b@x.id" to "Home"),
                organization = "PT Maju",
                jobTitle = "CTO",
                nickname = "Bud",
                websites = listOf("https://x.id"),
                addresses = listOf("Jl. Sudirman 1, Jakarta"),
                birthday = "1990-08-12",
                anniversary = "2015-01-02",
                note = "line1\nline2; with, punctuation\\",
                photo = ByteArray(64) { it.toByte() },
            ),
            VCardContact(displayName = "Siti"),
        )
        val parsed = VCardParser.parse(VCardWriter.write(original))
        assertEquals(0, parsed.skippedCards)
        assertEquals(original, parsed.contacts)
    }

    @Test fun `parses vcard 21 quoted printable with soft breaks`() {
        val vcf = "BEGIN:VCARD\r\nVERSION:2.1\r\n" +
            "N;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:;=41=6E=64=\r\n=69\r\n" +
            "TEL;CELL:+62812\r\nEND:VCARD\r\n"
        val c = VCardParser.parse(vcf).contacts.single()
        assertEquals("Andi", c.givenName)
        assertEquals("Andi", c.displayName) // FN absent: joined N
        assertEquals(listOf("+62812" to "CELL"), c.phones)
    }

    @Test fun `parses vcard 21 base64 photo with continuation lines`() {
        val vcf = "BEGIN:VCARD\r\nVERSION:2.1\r\nFN:A\r\n" +
            "PHOTO;ENCODING=BASE64;JPEG:AQID\r\n BAU=\r\n\r\nEND:VCARD\r\n"
        assertArrayEquals(
            byteArrayOf(1, 2, 3, 4, 5),
            VCardParser
                .parse(vcf)
                .contacts
                .single()
                .photo,
        )
    }

    @Test fun `parses 40 basics ANNIVERSARY and item groups`() {
        val vcf = "BEGIN:VCARD\r\nVERSION:4.0\r\nFN:A\r\n" +
            "ANNIVERSARY:20150102\r\nitem1.TEL:+62899\r\nitem1.X-ABLabel:Work\r\nEND:VCARD\r\n"
        val c = VCardParser.parse(vcf).contacts.single()
        assertEquals("20150102", c.anniversary)
        assertEquals("+62899", c.phones.single().first)
    }

    @Test fun `unknown properties are skipped`() {
        val vcf = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:A\r\nX-WEIRD:zzz\r\nIMPP:sip:a@b\r\nEND:VCARD\r\n"
        assertEquals(
            "A",
            VCardParser
                .parse(vcf)
                .contacts
                .single()
                .displayName,
        )
    }

    @Test fun `malformed card is skipped and counted, good ones survive`() {
        val vcf = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Good\r\nEND:VCARD\r\n" +
            "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:NoEnd\r\n" // truncated
        val r = VCardParser.parse(vcf)
        assertEquals(listOf("Good"), r.contacts.map { it.displayName })
        assertEquals(1, r.skippedCards)
    }

    @Test fun `card without FN and without N is skipped as malformed`() {
        val vcf = "BEGIN:VCARD\r\nVERSION:3.0\r\nTEL:1\r\nEND:VCARD\r\n"
        val r = VCardParser.parse(vcf)
        assertEquals(0, r.contacts.size)
        assertEquals(1, r.skippedCards)
    }

    @Test fun `tolerates LF-only line endings and lowercase property names`() {
        val vcf = "begin:vcard\nversion:3.0\nfn:Lima\ntel;type=cell:+1\nend:vcard\n"
        val c = VCardParser.parse(vcf).contacts.single()
        assertEquals("Lima", c.displayName)
        assertEquals(listOf("+1" to "cell"), c.phones)
    }

    @Test fun `empty input parses to empty result`() {
        val r = VCardParser.parse("")
        assertEquals(0, r.contacts.size)
        assertEquals(0, r.skippedCards)
    }

    @Test fun `ADR maps middle component and TYPE params carry to labels`() {
        val vcf = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:A\r\nADR;TYPE=HOME:;;Street 1;Town;;12345;ID\r\nEND:VCARD\r\n"
        val c = VCardParser.parse(vcf).contacts.single()
        assertEquals(listOf("Street 1, Town, 12345, ID"), c.addresses)
    }

    @Test fun `unescapes values`() {
        val vcf = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:A\\;B\\,C\\\\D\r\nNOTE:l1\\nl2\r\nEND:VCARD\r\n"
        val c = VCardParser.parse(vcf).contacts.single()
        assertEquals("A;B,C\\D", c.displayName)
        assertEquals("l1\nl2", c.note)
    }
}
