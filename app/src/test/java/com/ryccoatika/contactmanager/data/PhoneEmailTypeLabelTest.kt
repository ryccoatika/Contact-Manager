package com.ryccoatika.contactmanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneEmailTypeLabelTest {
    @Test
    fun `phone types map to labels`() {
        assertEquals("Home", PhoneEmailTypeLabel.from(PHONE, 1, null))
        assertEquals("Mobile", PhoneEmailTypeLabel.from(PHONE, 2, null))
        assertEquals("Work", PhoneEmailTypeLabel.from(PHONE, 3, null))
        assertEquals("Other", PhoneEmailTypeLabel.from(PHONE, 7, null))
    }

    @Test
    fun `phone custom type uses data3 label`() {
        assertEquals("Kantor", PhoneEmailTypeLabel.from(PHONE, 0, "Kantor"))
    }

    @Test
    fun `phone custom type with blank data3 is null`() {
        assertNull(PhoneEmailTypeLabel.from(PHONE, 0, " "))
        assertNull(PhoneEmailTypeLabel.from(PHONE, 0, null))
    }

    @Test
    fun `unknown phone type is null`() {
        assertNull(PhoneEmailTypeLabel.from(PHONE, 5, null))
        assertNull(PhoneEmailTypeLabel.from(PHONE, null, null))
    }

    @Test
    fun `email types map to labels`() {
        assertEquals("Home", PhoneEmailTypeLabel.from(EMAIL, 1, null))
        assertEquals("Work", PhoneEmailTypeLabel.from(EMAIL, 2, null))
        assertEquals("Other", PhoneEmailTypeLabel.from(EMAIL, 3, null))
        assertEquals("Mobile", PhoneEmailTypeLabel.from(EMAIL, 4, null))
    }

    @Test
    fun `email custom type uses data3 label`() {
        assertEquals("Pribadi", PhoneEmailTypeLabel.from(EMAIL, 0, "Pribadi"))
    }

    @Test
    fun `unknown email type is null`() {
        assertNull(PhoneEmailTypeLabel.from(EMAIL, 9, null))
    }

    @Test
    fun `other mimetypes are null even with known type int`() {
        assertNull(PhoneEmailTypeLabel.from("vnd.android.cursor.item/name", 1, "x"))
        assertNull(PhoneEmailTypeLabel.from(null, 1, "x"))
    }

    private companion object {
        private const val PHONE = "vnd.android.cursor.item/phone_v2"
        private const val EMAIL = "vnd.android.cursor.item/email_v2"
    }
}
