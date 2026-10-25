package com.mynkralus.mynassistant

internal data class PcDeviceRouteCandidate(
    val id: String,
    val connectionMode: String,
    val isOnline: Boolean
)

/**
 * Keeps Android phone records separate from Windows remote-control targets and
 * derives presence from a recent server heartbeat rather than a stale status flag.
 */
internal object DevicePresencePolicy {
    const val ONLINE_WINDOW_MILLIS = 3 * 60 * 1000L

    private val onlineStatuses = setOf("online", "connected", "ready", "active")

    fun isPcTarget(deviceId: String, platform: String, connectionMode: String): Boolean {
        if (connectionMode.trim().equals("firebase-agent", ignoreCase = true)) return true
        if (platform.trim().equals("Windows", ignoreCase = true)) return true
        return deviceId.startsWith("windows-", ignoreCase = true)
    }

    fun isOnline(status: String, lastSeenAtMillis: Long?, nowMillis: Long): Boolean {
        if (status.trim().lowercase() !in onlineStatuses) return false
        val lastSeen = lastSeenAtMillis ?: return false
        val age = nowMillis - lastSeen
        return age >= 0L && age < ONLINE_WINDOW_MILLIS
    }

    /**
     * A fresh Firebase Agent is the cloud route used by both Android and the web panel.
     * Prefer it over the legacy windows-{uid} LAN bridge when both records exist.
     */
    fun preferredPcDeviceId(
        candidates: List<PcDeviceRouteCandidate>,
        selectedDeviceId: String?,
        legacyLocalPcDeviceId: String?
    ): String? {
        val selected = candidates.firstOrNull { it.id == selectedDeviceId }
        if (selected != null && selected.isOnline &&
            (selected.connectionMode.equals("firebase-agent", ignoreCase = true) ||
                selected.id != legacyLocalPcDeviceId)
        ) {
            return selected.id
        }

        return candidates.firstOrNull {
            it.isOnline && it.connectionMode.equals("firebase-agent", ignoreCase = true)
        }?.id
            ?: candidates.firstOrNull {
                it.isOnline && it.id != legacyLocalPcDeviceId
            }?.id
            ?: selected?.id
            ?: candidates.firstOrNull()?.id
    }
}
