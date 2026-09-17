package com.ryccoatika.contactmanager.domain.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimRoutingTest {
    @Test fun `isSimAccount only matches icc prefix`() {
        assertTrue(SimRouting.isSimAccount("icc/-1"))
        assertTrue(SimRouting.isSimAccount("icc/2"))
        assertFalse(SimRouting.isSimAccount("com.google"))
        assertFalse(SimRouting.isSimAccount("vnd.sec.contact.sim"))
        assertFalse(SimRouting.isSimAccount(null))
    }

    @Test fun `simAccountType encodes subscription id and null as -1`() {
        assertEquals("icc/3", SimRouting.simAccountType(3))
        assertEquals("icc/-1", SimRouting.simAccountType(null))
    }

    @Test fun `subscriptionIdOf decodes account type`() {
        assertEquals(3, SimRouting.subscriptionIdOf("icc/3"))
        assertNull(SimRouting.subscriptionIdOf("icc/-1"))
        assertNull(SimRouting.subscriptionIdOf("com.google"))
        assertNull(SimRouting.subscriptionIdOf(null))
        assertNull(SimRouting.subscriptionIdOf("icc/abc"))
    }

    @Test fun `round trip type to subscription id`() {
        assertEquals(7, SimRouting.subscriptionIdOf(SimRouting.simAccountType(7)))
        assertNull(SimRouting.subscriptionIdOf(SimRouting.simAccountType(null)))
    }

    @Test fun `splitSimIds partitions negatives from positives preserving order`() {
        val (sim, normal) = SimRouting.splitSimIds(listOf(-1_000_000L, 10L, -1_000_001L, 20L))
        assertEquals(listOf(-1_000_000L, -1_000_001L), sim)
        assertEquals(listOf(10L, 20L), normal)
    }

    @Test fun `splitSimIds with empty input`() {
        val (sim, normal) = SimRouting.splitSimIds(emptyList())
        assertTrue(sim.isEmpty())
        assertTrue(normal.isEmpty())
    }

    @Test fun `synthetic ids are negative unique and flagged as sim`() {
        assertEquals(-1_000_000L, SimRouting.syntheticId(0))
        assertEquals(-1_000_005L, SimRouting.syntheticId(5))
        assertTrue(SimRouting.isSimRawContactId(SimRouting.syntheticId(0)))
        assertFalse(SimRouting.isSimRawContactId(42L))
    }

    @Test fun `nativeSimSlot maps samsung sim account types to physical slots`() {
        assertEquals(0, SimRouting.nativeSimSlot("vnd.sec.contact.sim"))
        assertEquals(1, SimRouting.nativeSimSlot("vnd.sec.contact.sim2"))
        assertEquals(2, SimRouting.nativeSimSlot("vnd.sec.contact.sim3"))
        assertEquals(0, SimRouting.nativeSimSlot("com.android.contacts.sim"))
    }

    @Test fun `nativeSimSlot returns null for unmappable types`() {
        assertNull(SimRouting.nativeSimSlot("USIM Account"))
        assertNull(SimRouting.nativeSimSlot("com.google"))
        assertNull(SimRouting.nativeSimSlot(null))
    }

    @Test fun `normalizeNumber strips formatting keeps dialable chars`() {
        assertEquals("+62812345", SimRouting.normalizeNumber("+62 812-345"))
        assertEquals("0812345", SimRouting.normalizeNumber("(0812) 345"))
        assertEquals("*123#", SimRouting.normalizeNumber("*123#"))
        assertEquals("", SimRouting.normalizeNumber("call me"))
    }
}
