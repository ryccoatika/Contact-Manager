package com.ryccoatika.contactmanager.domain

import com.ryccoatika.contactmanager.domain.model.LabeledValue
import com.ryccoatika.contactmanager.domain.model.RawContact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovePlannerTest {
    private fun raw(
        id: Long,
        phones: List<String> = emptyList(),
        emails: List<String> = emptyList(),
        organization: String? = null,
        note: String? = null,
    ) = RawContact(
        rawContactId = id,
        accountType = "com.google",
        accountName = "a@gmail.com",
        givenName = "Budi",
        phones = phones.mapIndexed { i, v -> LabeledValue(i.toLong(), v, null) },
        emails = emails.mapIndexed { i, v -> LabeledValue(100L + i, v, null) },
        organization = organization,
        note = note,
    )

    @Test fun `full-crud target loses nothing`() {
        val sources = listOf(
            raw(1, phones = listOf("0812", "0813"), emails = listOf("a@b.c"), organization = "PT", note = "n"),
        )
        val plan = MovePlanner.plan(sources, "com.google", "b@gmail.com")
        assertEquals(sources, plan.sources)
        assertEquals("com.google", plan.targetType)
        assertEquals("b@gmail.com", plan.targetName)
        assertTrue(plan.losses.isEmpty())
    }

    @Test fun `device-local null target type loses nothing`() {
        val plan = MovePlanner.plan(listOf(raw(1, emails = listOf("a@b.c"))), null, null)
        assertTrue(plan.losses.isEmpty())
    }

    @Test fun `sim target reports lost fields for a rich contact`() {
        val plan = MovePlanner.plan(
            listOf(
                raw(
                    1,
                    phones = listOf("0812", "0813"),
                    emails = listOf("a@b.c"),
                    organization = "PT Maju",
                    note = "VIP",
                ),
            ),
            "vnd.sec.contact.sim",
            "SIM",
        )
        assertEquals(1, plan.losses.size)
        assertEquals(1L, plan.losses[0].rawContactId)
        assertEquals(
            listOf("additional phone numbers", "emails", "organization", "note"),
            plan.losses[0].lostFields,
        )
    }

    @Test fun `sim target with name and single phone loses nothing`() {
        val plan = MovePlanner.plan(
            listOf(raw(1, phones = listOf("0812"))),
            "vnd.sec.contact.sim",
            "SIM",
        )
        assertTrue(plan.losses.isEmpty())
    }

    @Test fun `sim target reports only affected raw contacts`() {
        val plan = MovePlanner.plan(
            listOf(raw(1, phones = listOf("0812")), raw(2, emails = listOf("x@y.z"))),
            "vnd.sec.contact.sim",
            "SIM",
        )
        assertEquals(listOf(2L), plan.losses.map { it.rawContactId })
        assertEquals(listOf("emails"), plan.losses[0].lostFields)
    }
}
