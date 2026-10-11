package com.mynkralus.mynassistant

/**
 * Guards the transition from a startup phase to LISTENING.
 * A loaded model or a scheduled Vosk worker is not enough: at least one positive
 * PCM sample count must have been read from Android's microphone capture API.
 */
internal object MynWakeWordStartupReadiness {
    fun hasCapturedAudioSamples(samplesRead: Int): Boolean = samplesRead > 0

    fun canReportListening(
        phase: MynWakeWordPhase,
        samplesRead: Int
    ): Boolean =
        phase == MynWakeWordPhase.STARTING_MICROPHONE && hasCapturedAudioSamples(samplesRead)
}
