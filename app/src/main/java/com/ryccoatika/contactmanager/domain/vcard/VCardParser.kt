package com.ryccoatika.contactmanager.domain.vcard

import java.nio.charset.Charset
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** One property line, decoded down to: optional group ("item1."), name, params, raw value. */
private data class Prop(
    val name: String,
    val params: List<Pair<String, String?>>,
    val rawValue: String,
)

/** Tolerant vCard reader: 2.1 (QP/BASE64/CHARSET), 3.0, 4.0 basics. No GEO/TZ/KIND. */
@OptIn(ExperimentalEncodingApi::class)
object VCardParser {
    fun parse(text: String): VCardParseResult {
        val logical = unfold(text)
        // Split into card line-lists on BEGIN:VCARD/END:VCARD (case-insensitive).
        // A BEGIN without a matching END means that card (trailing, or reopened) is malformed.
        val cards = mutableListOf<List<String>>()
        var skipped = 0
        var current: MutableList<String>? = null
        logical.forEach { l ->
            when {
                l.equals("BEGIN:VCARD", true) -> {
                    if (current != null) skipped++
                    current = mutableListOf()
                }

                l.equals("END:VCARD", true) -> {
                    current?.let(cards::add)
                    current = null
                }

                else -> {
                    current?.add(l)
                }
            }
        }
        if (current != null) skipped++
        val contacts = cards.mapNotNull { lines ->
            card(lines) ?: run {
                skipped++
                null
            }
        }
        return VCardParseResult(contacts, skipped)
    }

    /**
     * Unfolds continuations into logical lines: normalizes CRLF/LF first, then per logical
     * line repeatedly joins (a) 2.1 QUOTED-PRINTABLE soft breaks — trailing "=" consumed,
     * next physical line appended with no leading-space requirement — and (b) standard
     * 3.0/4.0 folding, where the next physical line starts with a space/tab that gets
     * dropped. A blank line (e.g. terminating a 2.1 BASE64 photo) is neither, so it simply
     * ends up as its own empty logical line, ignored later by [splitProperty].
     */
    private fun unfold(text: String): List<String> {
        val physical = text.replace("\r\n", "\n").split("\n")
        val logical = mutableListOf<String>()
        var i = 0
        while (i < physical.size) {
            var current = physical[i]
            i++
            while (i < physical.size) {
                val next = physical[i]
                when {
                    isQpLine(current) && current.endsWith("=") -> {
                        current = current.dropLast(1) + next
                        i++
                    }

                    next.startsWith(" ") || next.startsWith("\t") -> {
                        current += next.substring(1)
                        i++
                    }

                    else -> {
                        break
                    }
                }
            }
            logical.add(current)
        }
        return logical
    }

    private fun isQpLine(line: String) = line.contains("QUOTED-PRINTABLE", ignoreCase = true)

    /** One property line -> name (group prefix like "item1." stripped, uppercased), params, raw value. */
    private fun splitProperty(line: String): Prop? {
        val colon = line.indexOf(':')
        if (colon < 0) return null
        val head = line.substring(0, colon)
        val rawValue = line.substring(colon + 1)
        val headParts = head.split(';')
        var namePart = headParts[0]
        val dot = namePart.indexOf('.')
        if (dot >= 0) namePart = namePart.substring(dot + 1)
        val params = headParts.drop(1).map { p ->
            val eq = p.indexOf('=')
            if (eq >= 0) p.substring(0, eq).uppercase() to p.substring(eq + 1) else p.uppercase() to null
        }
        return Prop(namePart.uppercase(), params, rawValue)
    }

    /** QUOTED-PRINTABLE decode per CHARSET param, else the raw value untouched. Escapes stay literal. */
    private fun decode(prop: Prop): String {
        val encoding = prop.params.firstOrNull { it.first == "ENCODING" }?.second
        if (!encoding.equals("QUOTED-PRINTABLE", ignoreCase = true)) return prop.rawValue
        val charsetName = prop.params.firstOrNull { it.first == "CHARSET" }?.second ?: "UTF-8"
        val charset = runCatching { Charset.forName(charsetName) }.getOrDefault(Charsets.UTF_8)
        return qpDecode(prop.rawValue, charset)
    }

    private fun qpDecode(value: String, charset: Charset): String {
        val bytes = mutableListOf<Byte>()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            val hex = if (c == '=' && i + 2 < value.length) value.substring(i + 1, i + 3).toIntOrNull(16) else null
            if (hex != null) {
                bytes.add(hex.toByte())
                i += 3
            } else {
                bytes.addAll(c.toString().toByteArray(Charsets.UTF_8).toList())
                i++
            }
        }
        return String(bytes.toByteArray(), charset)
    }

    /** Splits on [delim] not preceded by a backslash; escape sequences are left intact for [unescape]. */
    private fun splitUnescaped(value: String, delim: Char): List<String> {
        val parts = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when {
                c == '\\' && i + 1 < value.length -> {
                    sb.append(c).append(value[i + 1])
                    i += 2
                }

                c == delim -> {
                    parts.add(sb.toString())
                    sb.clear()
                    i++
                }

                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        parts.add(sb.toString())
        return parts
    }

    /** Resolves \\, \;, \, and \n/\N escapes; unrecognized "\x" drops the backslash. */
    private fun unescape(value: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (val n = value[i + 1]) {
                    '\\' -> sb.append('\\')
                    ';' -> sb.append(';')
                    ',' -> sb.append(',')
                    'n', 'N' -> sb.append('\n')
                    else -> sb.append(n)
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    /** TYPE=<value> wins; else the first bare (valueless) param, e.g. 2.1's "TEL;CELL:...". */
    private fun typeLabel(params: List<Pair<String, String?>>): String? =
        params.firstOrNull { it.first == "TYPE" }?.second
            ?: params.firstOrNull { it.second == null }?.first

    private fun card(lines: List<String>): VCardContact? {
        var fn: String? = null
        var familyName: String? = null
        var givenName: String? = null
        val phones = mutableListOf<Pair<String, String?>>()
        val emails = mutableListOf<Pair<String, String?>>()
        var organization: String? = null
        var jobTitle: String? = null
        var nickname: String? = null
        val websites = mutableListOf<String>()
        val addresses = mutableListOf<String>()
        var birthday: String? = null
        var anniversary: String? = null
        var note: String? = null
        var photo: ByteArray? = null

        lines.forEach lineLoop@{ line ->
            val prop = splitProperty(line) ?: return@lineLoop
            val decoded = decode(prop)
            when (prop.name) {
                "FN" -> {
                    fn = unescape(decoded)
                }

                "N" -> {
                    val comps = splitUnescaped(decoded, ';')
                    familyName = comps.getOrNull(0)?.let(::unescape)?.ifBlank { null }
                    givenName = comps.getOrNull(1)?.let(::unescape)?.ifBlank { null }
                }

                "TEL" -> {
                    phones.add(unescape(decoded) to typeLabel(prop.params))
                }

                "EMAIL" -> {
                    emails.add(unescape(decoded) to typeLabel(prop.params))
                }

                "ORG" -> {
                    organization = splitUnescaped(decoded, ';').firstOrNull()?.let(::unescape)?.ifBlank { null }
                }

                "TITLE" -> {
                    jobTitle = unescape(decoded).ifBlank { null }
                }

                "NICKNAME" -> {
                    nickname = unescape(decoded).ifBlank { null }
                }

                "URL" -> {
                    unescape(decoded).ifBlank { null }?.let(websites::add)
                }

                "ADR" -> {
                    val joined = splitUnescaped(decoded, ';').map(::unescape).filter { it.isNotBlank() }.joinToString(", ")
                    if (joined.isNotBlank()) addresses.add(joined)
                }

                "BDAY" -> {
                    birthday = unescape(decoded).ifBlank { null }
                }

                "ANNIVERSARY", "X-ANNIVERSARY" -> {
                    anniversary = unescape(decoded).ifBlank { null }
                }

                "NOTE" -> {
                    note = unescape(decoded).ifBlank { null }
                }

                "PHOTO" -> {
                    val encoding = prop.params.firstOrNull { it.first == "ENCODING" }?.second
                    if (encoding.equals("B", true) || encoding.equals("BASE64", true)) {
                        photo = runCatching { Base64.Mime.decode(decoded) }.getOrNull()
                    }
                }

                else -> {
                    Unit
                }
            }
        }

        // Writer synthesizes N's given component from FN when there's no real family/given
        // split (see VCardWriter.write); recognize and undo that on the way back in.
        if (fn != null && familyName == null && givenName == fn) givenName = null

        val displayName = fn ?: listOfNotNull(givenName, familyName).joinToString(" ").ifBlank { null } ?: return null

        return VCardContact(
            displayName = displayName,
            givenName = givenName,
            familyName = familyName,
            phones = phones,
            emails = emails,
            organization = organization,
            jobTitle = jobTitle,
            nickname = nickname,
            websites = websites,
            addresses = addresses,
            birthday = birthday,
            anniversary = anniversary,
            note = note,
            photo = photo,
        )
    }
}
