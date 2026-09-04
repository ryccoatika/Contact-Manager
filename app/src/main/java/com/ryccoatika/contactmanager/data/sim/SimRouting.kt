package com.ryccoatika.contactmanager.data.sim

/**
 * Pure routing rules for icc/adn SIM pseudo-accounts. SIM entries never live
 * in ContactsContract, so they are surfaced with synthetic negative ids and an
 * "icc/<subscriptionId or -1>" account type; everything here decodes that.
 */
object SimRouting {
    const val SIM_TYPE_PREFIX = "icc/"

    /** Offset keeping synthetic ids far away from real (positive) provider ids. */
    private const val SYNTHETIC_ID_BASE = 1_000_000L

    fun isSimAccount(type: String?): Boolean = type?.startsWith(SIM_TYPE_PREFIX) == true

    /** "icc/3" for subscription 3, "icc/-1" for the single-SIM/legacy path. */
    fun simAccountType(subscriptionId: Int?): String = "$SIM_TYPE_PREFIX${subscriptionId ?: -1}"

    /** Inverse of [simAccountType]; null for non-SIM types and for "icc/-1". */
    fun subscriptionIdOf(type: String?): Int? {
        if (!isSimAccount(type)) return null
        return type?.removePrefix(SIM_TYPE_PREFIX)?.toIntOrNull()?.takeIf { it >= 0 }
    }

    fun isSimRawContactId(id: Long): Boolean = id < 0

    /** Splits into (simIds, contactsContractIds), preserving order. */
    fun splitSimIds(ids: List<Long>): Pair<List<Long>, List<Long>> =
        ids.partition { isSimRawContactId(it) }

    /** Synthetic contact/raw-contact id for the SIM entry at [stableIndex]. */
    fun syntheticId(stableIndex: Int): Long = -(SYNTHETIC_ID_BASE + stableIndex)

    /**
     * Physical slot a *native* SIM account type maps to, so an icc pseudo-account
     * isn't shown for a SIM the provider already surfaces itself. Samsung uses
     * `vnd.sec.contact.sim` (slot 0), `vnd.sec.contact.sim2` (slot 1), …; a plain
     * `…sim` maps to slot 0. Returns null when the type can't be mapped to a slot
     * (caller then conservatively skips all icc to avoid double-listing).
     */
    fun nativeSimSlot(accountType: String?): Int? {
        val match = Regex("sim(\\d*)$").find(accountType?.lowercase() ?: return null) ?: return null
        val digits = match.groupValues[1]
        return if (digits.isEmpty()) 0 else digits.toIntOrNull()?.minus(1)?.takeIf { it >= 0 }
    }

    /** Strips formatting (spaces, dashes, parens, dots) so stored numbers pass SIM validation. */
    fun normalizeNumber(raw: String): String =
        raw.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
}
