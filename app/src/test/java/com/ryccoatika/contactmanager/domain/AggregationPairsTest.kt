package com.ryccoatika.contactmanager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AggregationPairsTest {
    private fun unordered(pairs: List<Pair<Long, Long>>): Set<Set<Long>> = pairs.map { setOf(it.first, it.second) }.toSet()

    @Test fun `pairs only across different contacts`() {
        val pairs = AggregationPairs.across(listOf(listOf(1L, 2L), listOf(3L)))
        assertEquals(2, pairs.size)
        assertEquals(setOf(setOf(1L, 3L), setOf(2L, 3L)), unordered(pairs))
    }

    @Test fun `a single contact yields no pairs`() {
        assertTrue(AggregationPairs.across(listOf(listOf(1L, 2L, 3L))).isEmpty())
    }

    @Test fun `three single-entry contacts yield three pairs`() {
        val pairs = AggregationPairs.across(listOf(listOf(1L), listOf(2L), listOf(3L)))
        assertEquals(3, pairs.size)
        assertEquals(setOf(setOf(1L, 2L), setOf(1L, 3L), setOf(2L, 3L)), unordered(pairs))
    }

    @Test fun `duplicate ids inside a list do not duplicate pairs`() {
        val pairs = AggregationPairs.across(listOf(listOf(1L, 1L), listOf(2L, 2L)))
        assertEquals(listOf(setOf(1L, 2L)), pairs.map { setOf(it.first, it.second) })
    }

    @Test fun `empty input yields no pairs`() {
        assertTrue(AggregationPairs.across(emptyList()).isEmpty())
        assertTrue(AggregationPairs.across(listOf(emptyList(), listOf(1L))).isEmpty())
    }
}
