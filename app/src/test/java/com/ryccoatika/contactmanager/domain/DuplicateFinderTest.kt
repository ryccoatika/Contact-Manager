package com.ryccoatika.contactmanager.domain

import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.LabeledValue
import com.ryccoatika.contactmanager.domain.model.RawContact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateFinderTest {

    private var nextDataId = 0L

    private fun contact(
        id: Long,
        name: String,
        phones: List<String> = emptyList(),
        emails: List<String> = emptyList(),
        accountType: String? = "com.google",
    ) = Contact(
        contactId = id,
        displayName = name,
        rawContacts = listOf(
            RawContact(
                rawContactId = id * 10,
                accountType = accountType,
                accountName = "acc",
                phones = phones.map { LabeledValue(++nextDataId, it, null) },
                emails = emails.map { LabeledValue(++nextDataId, it, null) },
            ),
        ),
    )

    // --- phone helpers -----------------------------------------------------

    @Test fun `normalizePhone strips formatting and keeps leading plus`() {
        assertEquals("+62812345678", DuplicateFinder.normalizePhone("+62 812-345-678"))
        assertEquals("0812345678", DuplicateFinder.normalizePhone("(0812) 345.678"))
    }

    @Test fun `phonesMatch on equal normalized form and on shared last nine digits`() {
        assertTrue(DuplicateFinder.phonesMatch("+62 812-345-678", "+62812345678"))
        assertTrue(DuplicateFinder.phonesMatch("+62812345678", "0812345678"))
        assertFalse(DuplicateFinder.phonesMatch("0812", "0813"))
        // Short numbers (under nine digits) must match exactly.
        assertFalse(DuplicateFinder.phonesMatch("12345", "812345"))
    }

    // --- matching rules ----------------------------------------------------

    @Test fun `formatted and unformatted numbers group as HIGH`() {
        val groups = DuplicateFinder.find(
            listOf(
                contact(1, "Budi", phones = listOf("+62 812-345-678")),
                contact(2, "B Santoso", phones = listOf("+62812345678")),
                contact(3, "Citra", phones = listOf("0899000111")),
            ),
        )
        assertEquals(1, groups.size)
        assertEquals(MatchConfidence.HIGH, groups[0].confidence)
        assertEquals("Same phone number", groups[0].matchReason)
        assertEquals(listOf(1L, 2L), groups[0].contacts.map { it.contactId })
    }

    @Test fun `country-code prefixed and local numbers group`() {
        val groups = DuplicateFinder.find(
            listOf(
                contact(1, "Budi", phones = listOf("+62812345678")),
                contact(2, "Budi Work", phones = listOf("0812345678")),
            ),
        )
        assertEquals(1, groups.size)
        assertEquals(MatchConfidence.HIGH, groups[0].confidence)
    }

    @Test fun `emails match case-insensitively and trimmed as HIGH`() {
        val groups = DuplicateFinder.find(
            listOf(
                contact(1, "Budi", emails = listOf("Budi.Santoso@Gmail.com ")),
                contact(2, "Bud", emails = listOf("budi.santoso@gmail.com")),
            ),
        )
        assertEquals(1, groups.size)
        assertEquals(MatchConfidence.HIGH, groups[0].confidence)
        assertEquals("Same email", groups[0].matchReason)
    }

    @Test fun `diacritic-folded names group as MEDIUM`() {
        val groups = DuplicateFinder.find(listOf(contact(1, "Renée"), contact(2, "renee")))
        assertEquals(1, groups.size)
        assertEquals(MatchConfidence.MEDIUM, groups[0].confidence)
        assertEquals("Similar name", groups[0].matchReason)
    }

    @Test fun `token-swapped names group as MEDIUM`() {
        val groups = DuplicateFinder.find(
            listOf(contact(1, "Budi Santoso"), contact(2, "Santoso Budi")),
        )
        assertEquals(1, groups.size)
        assertEquals(MatchConfidence.MEDIUM, groups[0].confidence)
    }

    @Test fun `blank names never group`() {
        val groups = DuplicateFinder.find(listOf(contact(1, " "), contact(2, "")))
        assertTrue(groups.isEmpty())
    }

    // --- group building ----------------------------------------------------

    @Test fun `overlapping phone and email pairs merge into one transitive group`() {
        val groups = DuplicateFinder.find(
            listOf(
                contact(1, "Budi A", phones = listOf("0812345678")),
                contact(2, "Budi B", phones = listOf("+62812345678"), emails = listOf("budi@x.com")),
                contact(3, "Budi C", emails = listOf("BUDI@X.COM")),
            ),
        )
        assertEquals(1, groups.size)
        assertEquals(listOf(1L, 2L, 3L), groups[0].contacts.map { it.contactId })
        assertEquals(MatchConfidence.HIGH, groups[0].confidence)
        assertEquals("Same phone number", groups[0].matchReason)
    }

    @Test fun `name-only edge joining a phone group keeps HIGH confidence`() {
        val groups = DuplicateFinder.find(
            listOf(
                contact(1, "Budi Santoso", phones = listOf("0812345678")),
                contact(2, "Budi", phones = listOf("+62812345678")),
                contact(3, "Santoso Budi"),
            ),
        )
        assertEquals(1, groups.size)
        assertEquals(MatchConfidence.HIGH, groups[0].confidence)
    }

    @Test fun `contact repeating its own phone never forms a group`() {
        val groups = DuplicateFinder.find(
            listOf(contact(1, "Budi", phones = listOf("0812345678", "+62812345678"))),
        )
        assertTrue(groups.isEmpty())
    }

    @Test fun `groups come HIGH first`() {
        val groups = DuplicateFinder.find(
            listOf(
                contact(1, "Renee"),
                contact(2, "Renée"),
                contact(3, "X", phones = listOf("0812345678")),
                contact(4, "Y", phones = listOf("0812345678")),
            ),
        )
        assertEquals(listOf(MatchConfidence.HIGH, MatchConfidence.MEDIUM), groups.map { it.confidence })
    }

    @Test fun `read-only sourced contacts still participate`() {
        val groups = DuplicateFinder.find(
            listOf(
                contact(1, "Budi", phones = listOf("0812345678"), accountType = "com.whatsapp"),
                contact(2, "Budiman", phones = listOf("0812345678")),
            ),
        )
        assertEquals(1, groups.size)
    }

    // --- dismissal & keys ---------------------------------------------------

    @Test fun `dismissed group keys are skipped`() {
        val contacts = listOf(contact(1, "Renee"), contact(2, "Renée"))
        val all = DuplicateFinder.find(contacts)
        assertEquals(1, all.size)
        val none = DuplicateFinder.find(contacts, dismissedKeys = setOf(DuplicateFinder.groupKey(all[0])))
        assertTrue(none.isEmpty())
    }

    @Test fun `groupKey is sorted contact ids joined with dashes`() {
        val group = DuplicateGroup(
            confidence = MatchConfidence.HIGH,
            contacts = listOf(contact(9, "A"), contact(2, "B")),
            matchReason = "Same phone number",
        )
        assertEquals("2-9", DuplicateFinder.groupKey(group))
    }

    // --- scale ---------------------------------------------------------------

    @Test fun `five thousand unique contacts produce no groups`() {
        val contacts = (1L..5000L).map {
            contact(it, "Contact $it", phones = listOf("+62811${"%07d".format(it)}"))
        }
        assertTrue(DuplicateFinder.find(contacts).isEmpty())
    }
}
