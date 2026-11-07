package com.mynkralus.mynassistant

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopPairingCodeInputRegressionTest {
    @Test
    fun normalizesRepeatedRawInputWithoutDroppingCharacters() {
        val keystrokes = listOf(
            "A", "AZ", "AZ8", "AZ89", "AZ89E",
            "AZ89EG", "AZ89EGX", "AZ89EGXU", "AZ89EGXUR", "AZ89EGXUR9"
        )
        var value = ""
        for (input in keystrokes) {
            value = DesktopPairingCodeFormat.normalize(input)
        }

        assertEquals("AZ89EGXUR9", value)
        assertEquals("AZ89E-GXUR9", DesktopPairingCodeFormat.format(value))
    }

    @Test
    fun normalizesPastedCodeWithVisualDash() {
        assertEquals(
            "AZ89EGXUR9",
            DesktopPairingCodeFormat.normalize("AZ89E-GXUR9")
        )
    }

    @Test
    fun removesSeparatorsAndLimitsInputToTenCharacters() {
        assertEquals(
            "AZ89EGXUR9",
            DesktopPairingCodeFormat.normalize("az89e-gxur9-extra")
        )
    }
}
