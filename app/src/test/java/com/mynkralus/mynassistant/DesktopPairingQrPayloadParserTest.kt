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

        val parsed = requireNotNull(result)
        assertEquals("ABCDE23456", parsed.code)
        assertEquals(1790000000L, parsed.expiresAtUnix)
        assertEquals(signature, parsed.signature)
    }

    @Test
    fun rejectsManualCodeAndUnsignedQrAsScannerInput() {
        assertNull(DesktopPairingQrPayloadParser.parse("ABCDE23456"))
        assertNull(DesktopPairingQrPayloadParser.parse("MYNASSISTANT-PAIR:ABCDE23456:1790000000"))
        assertNull(DesktopPairingQrPayloadParser.parse(null))
    }

    @Test
    fun rejectsNonBase64UrlCharactersEvenWhenSignatureLengthIsCorrect() {
        val invalidSignature = "A".repeat(42) + "+"
        assertEquals(43, invalidSignature.length)
        assertNull(
            DesktopPairingQrPayloadParser.parse(
                "MYNASSISTANT-PAIR:ABCDE23456:1790000000:$invalidSignature"
            )
        )
    }

    @Test
    fun acceptsCanonicalWindowsPayloadMarkerAndNormalizesCodeCase() {
        val signature = "A".repeat(43)
        val result = DesktopPairingQrPayloadParser.parse(
            "mynassistant-pair:abcde23456:1790000000:$signature"
        )

        assertEquals("ABCDE23456", requireNotNull(result).code)
        assertEquals(signature, result.signature)
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
