package com.ryccoatika.contactmanager.domain.sim

import com.ryccoatika.contactmanager.domain.model.SimContact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimCardReconcilerTest {
    private fun card(name: String, number: String, index: Int? = null) = SimContact(index, name, number, 1)

    private fun cardOnly(
        card: List<SimContact>,
        numbers: List<String> = emptyList(),
        names: List<String> = emptyList(),
    ) = SimCardReconciler.cardOnly(card, numbers, names)

    @Test fun `identical number is present`() {
        assertTrue(cardOnly(listOf(card("Budi", "0812345678")), numbers = listOf("0812345678")).isEmpty())
    }

    @Test fun `formatted and plain numbers match`() {
        assertTrue(cardOnly(listOf(card("Budi", "0812-345 678")), numbers = listOf("(0812) 345.678")).isEmpty())
    }

    @Test fun `leading plus is ignored`() {
        assertTrue(cardOnly(listOf(card("Budi", "+33612345678")), numbers = listOf("33612345678")).isEmpty())
    }

    @Test fun `international prefixes match on the last nine digits`() {
        val entry = card("Budi", "+33612345678")
        assertTrue(cardOnly(listOf(entry), numbers = listOf("0033612345678")).isEmpty())
        assertTrue(cardOnly(listOf(entry), numbers = listOf("612345678")).isEmpty())
    }

    @Test fun `different numbers are card only`() {
        val entry = card("Budi", "0812345678")
        assertEquals(listOf(entry), cardOnly(listOf(entry), numbers = listOf("0812345679")))
    }

    @Test fun `short numbers must match exactly`() {
        val entry = card("Service", "3179")
        assertEquals(listOf(entry), cardOnly(listOf(entry), numbers = listOf("93179")))
        assertTrue(cardOnly(listOf(entry), numbers = listOf("3179")).isEmpty())
    }

    @Test fun `number-less entry matches a mirror name case and space insensitively`() {
        assertTrue(cardOnly(listOf(card("  BUDI Santoso ", "")), names = listOf("budi santoso")).isEmpty())
    }

    @Test fun `number-less entry with unknown name is card only`() {
        val entry = card("Ghost", "")
        assertEquals(listOf(entry), cardOnly(listOf(entry), numbers = listOf("0812"), names = listOf("Budi")))
    }

    @Test fun `empty mirror makes everything card only`() {
        val all = listOf(card("A", "0811111111"), card("B", ""))
        assertEquals(all, cardOnly(all))
    }

    @Test fun `empty card yields nothing`() {
        assertTrue(cardOnly(emptyList(), numbers = listOf("0812"), names = listOf("Budi")).isEmpty())
    }

    @Test fun `two card entries sharing an absent number are both returned in order`() {
        val a = card("A", "0899999999", 1)
        val b = card("B", "0899999999", 2)
        assertEquals(listOf(a, b), cardOnly(listOf(a, b), numbers = listOf("0811111111")))
    }
}
