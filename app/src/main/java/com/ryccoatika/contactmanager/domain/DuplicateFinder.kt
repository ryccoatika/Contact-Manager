package com.ryccoatika.contactmanager.domain

import com.ryccoatika.contactmanager.domain.model.Contact
import java.text.Normalizer

enum class MatchConfidence { HIGH, MEDIUM }

data class DuplicateGroup(
    val confidence: MatchConfidence,
    val contacts: List<Contact>,
    val matchReason: String, // e.g. "Same phone number", "Same email", "Similar name"
)

/**
 * Pure duplicate detection over aggregated contacts; run it on Dispatchers.Default.
 *
 * Contacts are indexed by normalized phone (full form and last-nine-digits key
 * for country-code tolerance), normalized email, and folded name-token-set
 * signature; overlapping matches are merged into transitive groups with a
 * union-find — no pairwise scan, so thousands of contacts stay cheap.
 * Raw contacts already aggregated into the same [Contact] never match themselves.
 */
object DuplicateFinder {

    private enum class Reason { PHONE, EMAIL, NAME }

    fun find(contacts: List<Contact>, dismissedKeys: Set<String> = emptySet()): List<DuplicateGroup> {
        val buckets = HashMap<String, LinkedHashSet<Int>>()
        fun put(key: String, index: Int) = buckets.getOrPut(key) { LinkedHashSet() }.add(index)

        contacts.forEachIndexed { index, contact ->
            contact.rawContacts.forEach { raw ->
                raw.phones.forEach { phone ->
                    val normalized = normalizePhone(phone.value)
                    if (normalized.isNotEmpty()) {
                        put("phone:$normalized", index)
                        lastNine(normalized)?.let { put("phone9:$it", index) }
                    }
                }
                raw.emails.forEach { email ->
                    val normalized = email.value.trim().lowercase()
                    if (normalized.isNotEmpty()) put("email:$normalized", index)
                }
            }
            nameSignature(contact.displayName)?.let { put("name:$it", index) }
        }

        // Union-find with path halving; every bucket with 2+ members is a set of edges.
        val parent = IntArray(contacts.size) { it }
        fun root(i: Int): Int {
            var x = i
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }

        val matching = buckets.filterValues { it.size >= 2 }
        matching.values.forEach { members ->
            val first = root(members.first())
            members.forEach { parent[root(it)] = first }
        }

        val reasonsByRoot = HashMap<Int, MutableSet<Reason>>()
        matching.forEach { (key, members) ->
            val reason = when (key.substringBefore(':')) {
                "phone", "phone9" -> Reason.PHONE
                "email" -> Reason.EMAIL
                else -> Reason.NAME
            }
            reasonsByRoot.getOrPut(root(members.first())) { mutableSetOf() } += reason
        }

        val membersByRoot = LinkedHashMap<Int, MutableList<Contact>>()
        contacts.forEachIndexed { index, contact ->
            val r = root(index)
            if (r in reasonsByRoot) membersByRoot.getOrPut(r) { mutableListOf() } += contact
        }

        return membersByRoot.mapNotNull { (r, members) ->
            if (members.size < 2) return@mapNotNull null
            val reasons = reasonsByRoot.getValue(r)
            DuplicateGroup(
                confidence = if (Reason.PHONE in reasons || Reason.EMAIL in reasons) {
                    MatchConfidence.HIGH
                } else {
                    MatchConfidence.MEDIUM
                },
                contacts = members.sortedBy { it.contactId },
                matchReason = when {
                    Reason.PHONE in reasons -> "Same phone number"
                    Reason.EMAIL in reasons -> "Same email"
                    else -> "Similar name"
                },
            )
        }
            .filterNot { groupKey(it) in dismissedKeys }
            .sortedWith(compareBy({ it.confidence }, { it.contacts.first().contactId }))
    }

    /** Stable per-group id: sorted contact ids joined with dashes. */
    fun groupKey(group: DuplicateGroup): String =
        group.contacts.map { it.contactId }.sorted().joinToString("-")

    /** Strips everything but digits, keeping a leading '+'. */
    internal fun normalizePhone(s: String): String {
        val digits = s.filter { it.isDigit() }
        return if (s.trimStart().startsWith('+')) "+$digits" else digits
    }

    /** Equal normalized forms, or equal last nine digits (country-code tolerance). */
    internal fun phonesMatch(a: String, b: String): Boolean {
        val na = normalizePhone(a)
        val nb = normalizePhone(b)
        if (na.isEmpty() || nb.isEmpty()) return false
        if (na == nb) return true
        val lastA = lastNine(na)
        return lastA != null && lastA == lastNine(nb)
    }

    /** Last nine digits, or null for numbers too short for the tolerance rule. */
    private fun lastNine(normalized: String): String? {
        val digits = normalized.removePrefix("+")
        return if (digits.length >= 9) digits.takeLast(9) else null
    }

    /** Order-insensitive, case- and diacritic-folded token-set signature; null when blank. */
    private fun nameSignature(name: String): String? {
        val tokens = fold(name).split(NON_ALPHANUMERIC).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        return tokens.toSortedSet().joinToString(" ")
    }

    /** Lowercases and strips combining diacritic marks ("Renée" -> "renee"). */
    private fun fold(s: String): String =
        COMBINING_MARKS.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), "")

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")
}
