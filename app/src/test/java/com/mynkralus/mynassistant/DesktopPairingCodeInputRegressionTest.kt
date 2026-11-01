package com.mynkralus.mynassistant

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class DesktopPairingCodeFormatTest {
    @Test
    fun formatsCodeWithAutomaticSeparatorAfterFiveCharacters() {
        assertEquals("AZ89E-GXUR9", DesktopPairingCodeFormat.format("AZ89EGXUR9"))
        assertEquals("AZ89E-GXUR9", DesktopPairingCodeFormat.format("az89e-gxur9"))
    }

    @Test
    fun visualTransformationShowsDashWithoutChangingRawTextOrCaretPositions() {
        val transformed = DesktopPairingCodeVisualTransformation()
            .filter(AnnotatedString("AZ89EGXUR9"))

        assertEquals("AZ89E-GXUR9", transformed.text.text)
        assertEquals(6, transformed.offsetMapping.originalToTransformed(5))
        assertEquals(5, transformed.offsetMapping.transformedToOriginal(6))
        assertEquals(7, transformed.offsetMapping.originalToTransformed(6))
        assertEquals(6, transformed.offsetMapping.transformedToOriginal(7))
    }

    @Test
    fun parsesSignedWindowsQrAndKeepsSignatureAndExpiry() {
        val expiry = 1_800_000_300L
        val signature = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopq"
        val result = DesktopPairingCodeFormat.parseQr(
            "MYNASSISTANT-PAIR:AZ89E-GXUR9:$expiry:$signature"
        )

        assertNotNull(result)
        assertEquals("AZ89EGXUR9", result!!.code)
        assertEquals("AZ89E-GXUR9", result.formattedCode)
        assertEquals(signature, result.signature)
        assertEquals(expiry, result.expiresAtUnix)
    }

    @Test
    fun preservesSignedQrExpiryWithoutUsingPhoneClock() {
        val signature = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopq"
        val result = DesktopPairingCodeFormat.parseQr(
            "MYNASSISTANT-PAIR:AZ89EGXUR9:1800000600:$signature"
        )
        assertNotNull(result)
        assertEquals(1_800_000_600L, result!!.expiresAtUnix)
    }

    @Test
    fun acceptsLegacyPlainCodeQrWithoutAddingSignature() {
        val result = DesktopPairingCodeFormat.parseQr("AZ89E-GXUR9")

        assertNotNull(result)
        assertEquals("AZ89EGXUR9", result!!.code)
        assertNull(result.signature)
        assertNull(result.expiresAtUnix)
    }

    @Test
    fun retainsExpiryForServerValidationInsteadOfTrustingPhoneClock() {
        val signature = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopq"
        val result = DesktopPairingCodeFormat.parseQr(
            "MYNASSISTANT-PAIR:AZ89E-GXUR9:1799999999:$signature"
        )
        assertNotNull(result)
        assertEquals(1_799_999_999L, result!!.expiresAtUnix)
        assertEquals("AZ89EGXUR9", result.code)
    }

    @Test
    fun rejectsMalformedSignedQrAndPlainText() {
        val signature = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopq"
        assertNull(
            DesktopPairingCodeFormat.parseQr(
                "MYNASSISTANT-PAIR:AZ89EGXUR9:1800000300:bad"
            )
        )
        assertNull(DesktopPairingCodeFormat.parseQr("not-a-pairing-code"))
    }
}


class DesktopPairingCodeInputRegressionTest {
    @org.junit.Test
    fun normalizesRepeatedRawAndFormattedInputWithoutDroppingCharacters() {
        val keystrokes = listOf("A", "AZ", "AZ8", "AZ89", "AZ89E", "AZ89EG", "AZ89EGX", "AZ89EGXU", "AZ89EGXUR", "AZ89EGXUR9")
        var value = ""
        for (input in keystrokes) {
            value = DesktopPairingCodeFormat.normalize(input)
        }
        org.junit.Assert.assertEquals("AZ89EGXUR9", value)
        org.junit.Assert.assertEquals("AZ89E-GXUR9", DesktopPairingCodeFormat.format(value))
    }

    @org.junit.Test
    fun normalizesPastedCodeWithVisualDash() {
        org.junit.Assert.assertEquals("AZ89EGXUR9", DesktopPairingCodeFormat.normalize("AZ89E-GXUR9"))
    }
}
