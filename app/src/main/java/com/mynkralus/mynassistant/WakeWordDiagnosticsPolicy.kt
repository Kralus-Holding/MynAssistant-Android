package com.mynkralus.mynassistant

/**
 * Keeps opt-in wake-word debugging limited to the most recent short ASR result while
 * the service is still waiting for the wake phrase. It never stores audio.
 */
internal object WakeWordDiagnosticsPolicy {
    const val MAX_TRANSCRIPT_LENGTH = 120
    const val MAX_TRANSCRIPT_WORDS = 3

    fun transcriptToDisplay(
        enabled: Boolean,
        awaitingCommand: Boolean,
        commandReady: Boolean,
        transcript: String
    ): String? {
        if (!enabled || awaitingCommand || commandReady || transcript.isBlank()) return null
        // The wake phrase is expected at the beginning of the utterance. Keep only its
        // first few ASR words so the following command isn't retained in diagnostics.
        return transcript.trim()
            .split(Regex("\\s+"))
            .take(MAX_TRANSCRIPT_WORDS)
            .joinToString(" ")
            .take(MAX_TRANSCRIPT_LENGTH)
            .takeIf { it.isNotBlank() }
    }
}
