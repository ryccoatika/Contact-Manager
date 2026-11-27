package com.ryccoatika.contactmanager.domain.sim

import com.ryccoatika.contactmanager.domain.model.SimContact

/**
 * Finds the SIM card entries a native SIM account's mirror does not hold.
 *
 * Lenient on purpose: a false "already present" just leaves today's behaviour
 * (the entry stays hidden until the provider rebuilds its mirror); a false
 * "missing" would list the same contact twice.
 */
object SimCardReconciler {
    private const val SUFFIX_DIGITS = 9

    /** Entries of [card] absent from the mirror's [mirrorNumbers]/[mirrorNames], in card order. */
    fun cardOnly(
        card: List<SimContact>,
        mirrorNumbers: Collection<String>,
        mirrorNames: Collection<String>,
    ): List<SimContact> {
        val keys = mirrorNumbers.map(::numberKey).filter { it.isNotEmpty() }.toSet()
        val suffixes = keys.filter { digitCount(it) >= SUFFIX_DIGITS }.map { it.takeLast(SUFFIX_DIGITS) }.toSet()
        val names = mirrorNames.map(::nameKey).filter { it.isNotEmpty() }.toSet()
        return card.filterNot { entry ->
            val key = numberKey(entry.number)
            if (key.isEmpty()) {
                nameKey(entry.name) in names
            } else {
                key in keys || (digitCount(key) >= SUFFIX_DIGITS && key.takeLast(SUFFIX_DIGITS) in suffixes)
            }
        }
    }

    /** Digits, `*` and `#` only: formatting and a leading `+` dropped. */
    private fun numberKey(raw: String): String = raw.filter { it.isDigit() || it == '*' || it == '#' }

    private fun digitCount(key: String): Int = key.count { it.isDigit() }

    private fun nameKey(raw: String): String = raw.trim().lowercase()
}
