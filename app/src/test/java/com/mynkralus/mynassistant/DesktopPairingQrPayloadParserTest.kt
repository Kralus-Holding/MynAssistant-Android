package com.mynkralus.mynassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DesktopPairingQrPayloadParserTest {
    @Test
    fun parsesSignedQrFieldsWithoutClaimingToVerifySignatureLocally() {
        val signature = "A".repeat(43)
        val result = DesktopPairingQrPayloadParser.parse(
            "MYNASSISTANT-PAIR:ABCDE23456:1790000000:$signature"
        )

        requireNotNull(result)
        assertEquals("ABCDE23456", result.code)
        assertEquals(1790000000L, result.expiresAtUnix)
        assertEquals(signature, result.signature)
    }

    @Test
    fun rejectsManualCodeAndUnsignedQrAsScannerInput() {
        assertNull(DesktopPairingQrPayloadParser.parse("ABCDE23456"))
        assertNull(DesktopPairingQrPayloadParser.parse("MYNASSISTANT-PAIR:ABCDE23456:1790000000"))
        assertNull(DesktopPairingQrPayloadParser.parse(null))
    }

    @Test
    fun rejectsMalformedCodeExpiryAndSignature() {
        assertNull(
            DesktopPairingQrPayloadParser.parse(
                "MYNASSISTANT-PAIR:ABCDE2345O:1790000000:${"A".repeat(43)}"
            )
        )
        assertNull(
            DesktopPairingQrPayloadParser.parse(
                "MYNASSISTANT-PAIR:ABCDE23456:0:${"A".repeat(43)}"
            )
        )
        assertNull(
            DesktopPairingQrPayloadParser.parse(
                "MYNASSISTANT-PAIR:ABCDE23456:1790000000:bad-signature"
            )
        )
    }
}
