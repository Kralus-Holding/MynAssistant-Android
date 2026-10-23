package com.mynkralus.mynassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountProfileInputTest {
    @Test
    fun normalizesTurkishMobileNumberWithLeadingZero() {
        assertEquals("+905551112233", normalizePhoneNumberForVerification("0555 111 22 33"))
    }

    @Test
    fun normalizesTurkishMobileNumberWithoutLeadingZero() {
        assertEquals("+905551112233", normalizePhoneNumberForVerification("5551112233"))
    }

    @Test
    fun preservesExplicitInternationalNumber() {
        assertEquals("+442071838750", normalizePhoneNumberForVerification("+44 20 7183 8750"))
    }

    @Test
    fun rejectsIncompletePhoneNumber() {
        assertNull(normalizePhoneNumberForVerification("55512"))
    }
}
