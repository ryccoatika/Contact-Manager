package com.ryccoatika.contactmanager.domain.vcard

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Serializes contacts as vCard 3.0 (CRLF, 75-octet folding, deterministic order). */
@OptIn(ExperimentalEncodingApi::class)
object VCardWriter {
    fun write(contacts: List<VCardContact>): String = buildString {
        contacts.forEach { c ->
            line("BEGIN:VCARD")
            line("VERSION:3.0")
            line("FN:${esc(c.displayName)}")
            line("N:${esc(c.familyName ?: "")};${esc(c.givenName ?: c.displayName.takeIf { c.familyName == null } ?: "")};;;")
            c.phones.forEach { (v, t) -> line(if (t.isNullOrBlank()) "TEL:${esc(v)}" else "TEL;TYPE=${esc(t)}:${esc(v)}") }
            c.emails.forEach { (v, t) -> line(if (t.isNullOrBlank()) "EMAIL:${esc(v)}" else "EMAIL;TYPE=${esc(t)}:${esc(v)}") }
            c.organization?.ifBlank { null }?.let { line("ORG:${esc(it)}") }
            c.jobTitle?.ifBlank { null }?.let { line("TITLE:${esc(it)}") }
            c.nickname?.ifBlank { null }?.let { line("NICKNAME:${esc(it)}") }
            c.websites.forEach { line("URL:${esc(it)}") }
            c.addresses.forEach { line("ADR:;;${esc(it)};;;;") }
            c.birthday?.ifBlank { null }?.let { line("BDAY:${esc(it)}") }
            c.anniversary?.ifBlank { null }?.let { line("X-ANNIVERSARY:${esc(it)}") }
            c.note?.ifBlank { null }?.let { line("NOTE:${esc(it)}") }
            c.photo
                ?.takeIf { it.isNotEmpty() }
                ?.let { line("PHOTO;ENCODING=b;TYPE=JPEG:${Base64.encode(it)}") }
            line("END:VCARD")
        }
    }

    private fun esc(v: String): String = v
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")

    /** Appends [raw] folded to ≤75 octets per physical line (RFC 2426 §2.6). */
    private fun StringBuilder.line(raw: String) {
        var rest = raw
        var first = true
        while (true) {
            val budget = if (first) 75 else 74
            val bytes = rest.toByteArray(Charsets.UTF_8)
            if (bytes.size <= budget) break
            var cut = budget
            while (cut > 0 && (bytes[cut].toInt() and 0xC0) == 0x80) cut-- // don't split UTF-8
            val head = String(bytes, 0, cut, Charsets.UTF_8)
            append(if (first) head else " $head")
            append("\r\n")
            rest = String(bytes, cut, bytes.size - cut, Charsets.UTF_8)
            first = false
        }
        append(if (first) rest else " $rest")
        append("\r\n")
    }
}
