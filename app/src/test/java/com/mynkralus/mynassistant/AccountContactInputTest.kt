package com.mynkralus.mynassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountContactInputTest {
    @Test
    fun normalizesTurkishMobileNumberWithLeadingZero() {
        assertEquals("+905551112233", normalizePendingPhoneNumber("0555 111 22 33"))
    }

    @Test
    fun normalizesTurkishMobileNumberWithoutLeadingZero() {
        assertEquals("+905551112233", normalizePendingPhoneNumber("5551112233"))
    }

    @Test
    fun normalizesInternationalPrefixWithDoubleZero() {
        assertEquals("+905551112233", normalizePendingPhoneNumber("0090 555 111 22 33"))
    }

    @Test
    fun preservesValidInternationalNumber() {
        assertEquals("+442071838750", normalizePendingPhoneNumber("+44 20 7183 8750"))
    }

    @Test
    fun rejectsIncompleteOrMalformedPhoneNumber() {
        assertNull(normalizePendingPhoneNumber("55512"))
        assertNull(normalizePendingPhoneNumber("+90 abc"))
    }
}
