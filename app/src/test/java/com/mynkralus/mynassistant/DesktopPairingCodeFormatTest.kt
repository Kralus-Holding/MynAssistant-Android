package com.mynkralus.mynassistant

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
    fun parsesSignedWindowsQrAndKeepsSignatureAndExpiry() {
        val expiry = 1_800_000_300L
        val signature = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopq"
        val result = DesktopPairingCodeFormat.parseQr(
            "MYNASSISTANT-PAIR:AZ89EGXUR9:$expiry:$signature",
            nowEpochSeconds = 1_800_000_000L
        )

        assertNotNull(result)
        assertEquals("AZ89EGXUR9", result!!.code)
        assertEquals("AZ89E-GXUR9", result.formattedCode)
        assertEquals(signature, result.signature)
        assertEquals(expiry, result.expiresAtUnix)
    }

    @Test
    fun acceptsLegacyPlainCodeQrWithoutAddingSignature() {
        val result = DesktopPairingCodeFormat.parseQr("AZ89E-GXUR9", nowEpochSeconds = 1_800_000_000L)

        assertNotNull(result)
        assertEquals("AZ89EGXUR9", result!!.code)
        assertNull(result.signature)
        assertNull(result.expiresAtUnix)
    }

    @Test
    fun rejectsExpiredOrMalformedSignedQr() {
        val signature = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopq"
        assertNull(
            DesktopPairingCodeFormat.parseQr(
                "MYNASSISTANT-PAIR:AZ89EGXUR9:1799999999:$signature",
                nowEpochSeconds = 1_800_000_000L
            )
        )
        assertNull(
            DesktopPairingCodeFormat.parseQr(
                "MYNASSISTANT-PAIR:AZ89EGXUR9:1800000300:bad",
                nowEpochSeconds = 1_800_000_000L
            )
        )
        assertNull(DesktopPairingCodeFormat.parseQr("not-a-pairing-code"))
    }
}
