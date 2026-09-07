package com.ryccoatika.contactmanager.domain

import com.ryccoatika.contactmanager.domain.model.SimCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimContactValidatorTest {
    private val caps = SimCapabilities(canRead = true, canWrite = true, maxNameLength = 14)

    private fun errorOf(name: String, number: String): SimError {
        val result = SimContactValidator.validate(name, number, caps)
        assertTrue("expected Error, got $result", result is SimValidation.Error)
        return (result as SimValidation.Error).error
    }

    @Test fun `valid name and number pass`() {
        assertEquals(SimValidation.Ok, SimContactValidator.validate("Budi", "+62812345", caps))
    }

    @Test fun `name at exactly max length passes`() {
        assertEquals(SimValidation.Ok, SimContactValidator.validate("A".repeat(14), "0812", caps))
    }

    @Test fun `blank name rejected`() {
        assertEquals(SimError.BlankName, errorOf("   ", "0812"))
    }

    @Test fun `name over max length rejected with limit in reason`() {
        assertEquals(SimError.NameTooLong(14), errorOf("A".repeat(15), "0812"))
    }

    @Test fun `name length limit follows capabilities`() {
        val shortCaps = caps.copy(maxNameLength = 6)
        val result = SimContactValidator.validate("Budiman", "0812", shortCaps)
        assertEquals(SimValidation.Error(SimError.NameTooLong(6)), result)
    }

    @Test fun `blank number rejected`() {
        assertEquals(SimError.BlankNumber, errorOf("Budi", "  "))
    }

    @Test fun `number with letters or spaces rejected`() {
        assertTrue(SimContactValidator.validate("Budi", "0812 345", caps) is SimValidation.Error)
        assertTrue(SimContactValidator.validate("Budi", "CALL-ME", caps) is SimValidation.Error)
        assertTrue(SimContactValidator.validate("Budi", "08-12", caps) is SimValidation.Error)
    }

    @Test fun `number allows plus prefix star and hash`() {
        assertEquals(SimValidation.Ok, SimContactValidator.validate("Budi", "+628123", caps))
        assertEquals(SimValidation.Ok, SimContactValidator.validate("Budi", "*123#", caps))
    }

    @Test fun `number longer than 20 digits rejected`() {
        assertTrue(SimContactValidator.validate("Budi", "1".repeat(21), caps) is SimValidation.Error)
        assertEquals(SimValidation.Ok, SimContactValidator.validate("Budi", "1".repeat(20), caps))
    }

    @Test fun `plus only allowed as leading character`() {
        assertTrue(SimContactValidator.validate("Budi", "0812+34", caps) is SimValidation.Error)
    }
}
