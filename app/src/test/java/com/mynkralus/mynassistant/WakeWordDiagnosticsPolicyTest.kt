package com.mynkralus.mynassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakeWordDiagnosticsPolicyTest {
    @Test
    fun transcriptIsHiddenUnlessUserOptedIn() {
        assertNull(
            WakeWordDiagnosticsPolicy.transcriptToDisplay(
                enabled = false,
                awaitingCommand = false,
                commandReady = false,
                transcript = "hey meyene"
            )
        )
    }

    @Test
    fun transcriptIsNotCapturedAfterWakePhraseWasMatched() {
        assertNull(
            WakeWordDiagnosticsPolicy.transcriptToDisplay(
                enabled = true,
                awaitingCommand = true,
                commandReady = false,
                transcript = "hey meyene hava nasıl"
            )
        )
    }

    @Test
    fun transcriptIsNotCapturedWhenCommandIsReady() {
        assertNull(
            WakeWordDiagnosticsPolicy.transcriptToDisplay(
                enabled = true,
                awaitingCommand = false,
                commandReady = true,
                transcript = "private command"
            )
        )
    }

    @Test
    fun onlyLastShortNonBlankTranscriptIsReturned() {
        assertNull(
            WakeWordDiagnosticsPolicy.transcriptToDisplay(
                enabled = true,
                awaitingCommand = false,
                commandReady = false,
                transcript = "   "
            )
        )
        val longTranscript = "x".repeat(200)
        val result = WakeWordDiagnosticsPolicy.transcriptToDisplay(
            enabled = true,
            awaitingCommand = false,
            commandReady = false,
            transcript = longTranscript
        )
        assertEquals(WakeWordDiagnosticsPolicy.MAX_TRANSCRIPT_LENGTH, result?.length)

        val phrasePrefix = WakeWordDiagnosticsPolicy.transcriptToDisplay(
            enabled = true,
            awaitingCommand = false,
            commandReady = false,
            transcript = "Hey maybe open WhatsApp and send a message"
        )
        assertEquals("Hey maybe open", phrasePrefix)
        assertEquals(WakeWordDiagnosticsPolicy.MAX_TRANSCRIPT_WORDS, phrasePrefix?.split(" ")?.size)
    }
}
