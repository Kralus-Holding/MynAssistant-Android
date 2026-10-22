package com.mynkralus.mynassistant

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
}
