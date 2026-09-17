package com.ryccoatika.contactmanager.domain.vcard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VCardWriterTest {
    @Test fun `writes minimal card with CRLF and version 3`() {
        val out = VCardWriter.write(listOf(VCardContact(displayName = "Andi Wijaya")))
        assertEquals(
            "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Andi Wijaya\r\nN:;Andi Wijaya;;;\r\nEND:VCARD\r\n",
            out,
        )
    }

    @Test fun `writes N from family and given when present`() {
        val out = VCardWriter.write(
            listOf(VCardContact(displayName = "Andi Wijaya", givenName = "Andi", familyName = "Wijaya")),
        )
        assertTrue(out.contains("\r\nN:Wijaya;Andi;;;\r\n"))
    }

    @Test fun `writes all simple properties in stable order`() {
        val out = VCardWriter.write(
            listOf(
                VCardContact(
                    displayName = "Budi",
                    phones = listOf("+62812111" to "Mobile", "021555" to null),
                    emails = listOf("b@x.id" to "Home"),
                    organization = "PT Maju",
                    jobTitle = "CTO",
                    nickname = "Bud",
                    websites = listOf("https://x.id"),
                    addresses = listOf("Jl. Sudirman 1, Jakarta"),
                    birthday = "1990-08-12",
                    anniversary = "2015-01-02",
                    note = "VIP",
                ),
            ),
        )
        val lines = out.split("\r\n")
        val idx = { p: String -> lines.indexOfFirst { it.startsWith(p) } }
        assertTrue(idx("TEL;TYPE=Mobile:+62812111") in 0 until idx("TEL:021555"))
        assertTrue(idx("EMAIL;TYPE=Home:b@x.id") > 0)
        assertTrue(lines.contains("ORG:PT Maju"))
        assertTrue(lines.contains("TITLE:CTO"))
        assertTrue(lines.contains("NICKNAME:Bud"))
        assertTrue(lines.contains("URL:https://x.id"))
        assertTrue(lines.contains("ADR:;;Jl. Sudirman 1\\, Jakarta;;;;"))
        assertTrue(lines.contains("BDAY:1990-08-12"))
        assertTrue(lines.contains("X-ANNIVERSARY:2015-01-02"))
        assertTrue(lines.contains("NOTE:VIP"))
    }

    @Test fun `escapes backslash comma semicolon and newline in values`() {
        val out = VCardWriter.write(listOf(VCardContact(displayName = "A;B,C\\D", note = "l1\nl2")))
        assertTrue(out.contains("FN:A\\;B\\,C\\\\D"))
        assertTrue(out.contains("NOTE:l1\\nl2"))
    }

    @Test fun `folds lines longer than 75 octets with space continuation`() {
        val long = "x".repeat(200)
        val out = VCardWriter.write(listOf(VCardContact(displayName = "A", note = long)))
        val noteBlock = out.substringAfter("NOTE:").substringBefore("END:VCARD")
        assertTrue(noteBlock.contains("\r\n "))
        out.split("\r\n").forEach { assertTrue(it.toByteArray(Charsets.UTF_8).size <= 75) }
    }

    @Test fun `embeds photo as base64 with b encoding`() {
        val out = VCardWriter.write(listOf(VCardContact(displayName = "A", photo = byteArrayOf(1, 2, 3))))
        assertTrue(out.contains("PHOTO;ENCODING=b;TYPE=JPEG:AQID"))
    }

    @Test fun `omits empty and null fields entirely`() {
        val out = VCardWriter.write(listOf(VCardContact(displayName = "A")))
        listOf("TEL", "EMAIL", "ORG", "TITLE", "NICKNAME", "URL", "ADR", "BDAY", "X-ANNIVERSARY", "NOTE", "PHOTO")
            .forEach { assertTrue("$it leaked", !out.contains("\r\n$it")) }
    }

    @Test fun `writes multiple cards back to back`() {
        val out = VCardWriter.write(listOf(VCardContact("A"), VCardContact("B")))
        assertEquals(2, Regex("BEGIN:VCARD").findAll(out).count())
    }
}
