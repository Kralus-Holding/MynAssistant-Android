package com.mynkralus.mynassistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MynWakeWordStartupReadinessTest {
    @Test
    fun loadedModelAndScheduledWorkerDoNotCountAsListening() {
        assertFalse(
            MynWakeWordStartupReadiness.canReportListening(
                MynWakeWordPhase.LOADING_ENGINE,
                samplesRead = 512
            )
        )
    }

    @Test
    fun emptyOrFailedAudioReadsCannotReportListening() {
        assertFalse(
            MynWakeWordStartupReadiness.canReportListening(
                MynWakeWordPhase.STARTING_MICROPHONE,
                samplesRead = 0
            )
        )
        assertFalse(
            MynWakeWordStartupReadiness.canReportListening(
                MynWakeWordPhase.STARTING_MICROPHONE,
                samplesRead = -3
            )
        )
    }

    @Test
    fun positivePcmReadDuringMicrophoneStartupAllowsListeningTransition() {
        assertTrue(
            MynWakeWordStartupReadiness.canReportListening(
                MynWakeWordPhase.STARTING_MICROPHONE,
                samplesRead = 512
            )
        )
    }
}
