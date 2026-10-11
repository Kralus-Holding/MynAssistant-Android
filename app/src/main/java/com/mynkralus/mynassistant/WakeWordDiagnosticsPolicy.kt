package com.mynkralus.mynassistant

/**
 * Keeps opt-in wake-word debugging limited to the most recent short ASR result while
 * the service is still waiting for the wake phrase. It never stores audio.
 */
internal object WakeWordDiagnosticsPolicy {
    const val MAX_TRANSCRIPT_LENGTH = 120

    fun transcriptToDisplay(
        enabled: Boolean,
        awaitingCommand: Boolean,
        commandReady: Boolean,
        transcript: String
    ): String? {
        if (!enabled || awaitingCommand || commandReady || transcript.isBlank()) return null
        return transcript.take(MAX_TRANSCRIPT_LENGTH)
    }
}
