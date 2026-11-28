package com.ryccoatika.contactmanager.domain

/** Pure pairing logic for aggregation exceptions between separate contacts. */
object AggregationPairs {
    /**
     * Every unordered pair of ids taken from two different inner lists, each pair
     * once. Ids that already share an inner list (one contact's raw contacts) are
     * never paired, so entries of the same contact are left alone.
     */
    fun across(contacts: List<List<Long>>): List<Pair<Long, Long>> {
        val groups = contacts.map { it.distinct() }
        val seen = HashSet<Pair<Long, Long>>()
        val result = ArrayList<Pair<Long, Long>>()
        for (i in groups.indices) {
            for (j in i + 1 until groups.size) {
                for (a in groups[i]) {
                    for (b in groups[j]) {
                        if (a == b) continue
                        val key = if (a < b) a to b else b to a
                        if (seen.add(key)) result += a to b
                    }
                }
            }
        }
        return result
    }
}
