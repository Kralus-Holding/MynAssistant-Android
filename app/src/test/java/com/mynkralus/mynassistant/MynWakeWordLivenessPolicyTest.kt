package com.mynkralus.mynassistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MynWakeWordLivenessPolicyTest {
    @Test
    fun listeningWithoutHeartbeatIsStale() {
        assertTrue(
            MynWakeWordLivenessPolicy.isStale(
                enabled = true,
                phase = MynWakeWordPhase.LISTENING,
                lastHeartbeatAt = 0L,
                lastStatusUpdatedAt = 9_000L,
                now = 100_000L
            )
        )
    }

    @Test
    fun recentListeningHeartbeatIsHealthy() {
        assertFalse(
            MynWakeWordLivenessPolicy.isStale(
                enabled = true,
                phase = MynWakeWordPhase.LISTENING,
                lastHeartbeatAt = 80_000L,
                lastStatusUpdatedAt = 80_000L,
                now = 100_000L
            )
        )
    }

    @Test
    fun oldListeningHeartbeatIsStaleAfterTimeout() {
        assertTrue(
            MynWakeWordLivenessPolicy.isStale(
                enabled = true,
                phase = MynWakeWordPhase.LISTENING,
                lastHeartbeatAt = 10_000L,
                lastStatusUpdatedAt = 10_000L,
                now = 10_000L + MynWakeWordLivenessPolicy.LISTENING_HEARTBEAT_TIMEOUT_MS + 1L
            )
        )
    }

    @Test
    fun staleStartupPhaseIsDetected() {
        assertTrue(
            MynWakeWordLivenessPolicy.isStale(
                enabled = true,
                phase = MynWakeWordPhase.DOWNLOADING_MODEL,
                lastHeartbeatAt = 0L,
                lastStatusUpdatedAt = 1_000L,
                now = 1_000L + MynWakeWordLivenessPolicy.ACTIVE_PHASE_TIMEOUT_MS + 1L
            )
        )
    }

    @Test
    fun disabledOffAndErrorStatesAreNotReclassified() {
        assertFalse(
            MynWakeWordLivenessPolicy.isStale(
                enabled = false,
                phase = MynWakeWordPhase.LISTENING,
                lastHeartbeatAt = 0L,
                lastStatusUpdatedAt = 0L,
                now = 100_000L
            )
        )
        assertFalse(
            MynWakeWordLivenessPolicy.isStale(
                enabled = true,
                phase = MynWakeWordPhase.OFF,
                lastHeartbeatAt = 0L,
                lastStatusUpdatedAt = 0L,
                now = 100_000L
            )
        )
        assertFalse(
            MynWakeWordLivenessPolicy.isStale(
                enabled = true,
                phase = MynWakeWordPhase.ERROR,
                lastHeartbeatAt = 0L,
                lastStatusUpdatedAt = 0L,
                now = 100_000L
            )
        )
    }

    @Test
    fun futureTimestampDoesNotImmediatelyDeclareFailure() {
        assertFalse(
            MynWakeWordLivenessPolicy.isStale(
                enabled = true,
                phase = MynWakeWordPhase.LISTENING,
                lastHeartbeatAt = 150_000L,
                lastStatusUpdatedAt = 150_000L,
                now = 100_000L
            )
        )
    }
}
