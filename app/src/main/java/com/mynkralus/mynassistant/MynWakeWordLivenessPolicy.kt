package com.mynkralus.mynassistant

/**
 * Detects persisted service states that may have survived after Android killed the service process.
 * Time values are wall-clock milliseconds stored in app-private preferences.
 */
internal object MynWakeWordLivenessPolicy {
    const val LISTENING_HEARTBEAT_TIMEOUT_MS = 45_000L
    const val ACTIVE_PHASE_TIMEOUT_MS = 15 * 60_000L

    fun isStale(
        enabled: Boolean,
        phase: MynWakeWordPhase,
        lastHeartbeatAt: Long,
        lastStatusUpdatedAt: Long,
        now: Long
    ): Boolean {
        if (!enabled || phase == MynWakeWordPhase.OFF || phase == MynWakeWordPhase.ERROR) {
            return false
        }
        if (now < 0L) return false

        return when (phase) {
            MynWakeWordPhase.LISTENING ->
                lastHeartbeatAt <= 0L ||
                    (now >= lastHeartbeatAt &&
                        now - lastHeartbeatAt > LISTENING_HEARTBEAT_TIMEOUT_MS)
            else ->
                lastStatusUpdatedAt <= 0L ||
                    (now >= lastStatusUpdatedAt &&
                        now - lastStatusUpdatedAt > ACTIVE_PHASE_TIMEOUT_MS)
        }
    }
}
