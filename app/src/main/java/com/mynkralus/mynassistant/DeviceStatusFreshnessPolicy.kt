package com.mynkralus.mynassistant

import java.util.Locale

internal const val DEVICE_HEARTBEAT_MAX_AGE_MS = 90_000L
internal const val DEVICE_FUTURE_TIMESTAMP_TOLERANCE_MS = 120_000L

private val onlineDeviceStates = setOf("online", "connected", "ready", "active")

/**
 * Treat a Windows target as offline when its last heartbeat is too old.
 *
 * The small future tolerance avoids false-offline results caused by clock skew.
 * Missing or malformed heartbeat timestamps are not treated as a live connection.
 */
internal fun normalizeDeviceStatus(
    rawStatus: String?,
    lastSeenAtMillis: Long?,
    nowMillis: Long = System.currentTimeMillis()
): String {
    val status = rawStatus?.trim().orEmpty()
    if (status.isBlank()) return "offline"
    if (status.lowercase(Locale.ROOT) !in onlineDeviceStates) return status

    val lastSeen = lastSeenAtMillis ?: return "offline"
    val ageMillis = nowMillis - lastSeen
    return if (
        ageMillis >= -DEVICE_FUTURE_TIMESTAMP_TOLERANCE_MS &&
        ageMillis <= DEVICE_HEARTBEAT_MAX_AGE_MS
    ) {
        status
    } else {
        "offline"
    }
}

internal fun isDeviceOnlineAt(
    rawStatus: String?,
    lastSeenAtMillis: Long?,
    nowMillis: Long
): Boolean = normalizeDeviceStatus(rawStatus, lastSeenAtMillis, nowMillis)
    .lowercase(Locale.ROOT) in onlineDeviceStates
