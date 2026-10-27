package com.mynkralus.mynassistant

import org.junit.Assert.assertEquals
import org.junit.Test

class PcFileListingPayloadTest {
    @Test
    fun rawJsonPayloadIsUsedInsteadOfConcatenatedStatusMessage() {
        val rawPayload = """{"schemaVersion":1,"currentPath":"","parentPath":null,"entries":[],"truncated":false,"warnings":[]}"""
        val displayMessage = "Dosya listesi alındı.\n" + rawPayload

        assertEquals(rawPayload, resolvePcFileListingPayload(rawPayload, displayMessage))
    }

    @Test
    fun blankPayloadFallsBackToDisplayMessage() {
        assertEquals("Dosya listesi yanıtı yok.", resolvePcFileListingPayload(null, "Dosya listesi yanıtı yok."))
        assertEquals("Gösterilecek sonuç yok.", resolvePcFileListingPayload("   ", "Gösterilecek sonuç yok."))
    }
}
