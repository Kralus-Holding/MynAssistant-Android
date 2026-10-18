package com.mynkralus.mynassistant

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceStatusFreshnessPolicyTest {
    @Test
    fun freshHeartbeatKeepsWindowsTargetOnline() {
        assertEquals(
            "online",
            normalizeDeviceStatus("online", lastSeenAtMillis = 70_000L, nowMillis = 100_000L)
        )
    }

    @Test
    fun staleHeartbeatMarksWindowsTargetOffline() {
        assertEquals(
            "offline",
            normalizeDeviceStatus("online", lastSeenAtMillis = 9_000L, nowMillis = 100_000L)
        )
    }

    @Test
    fun missingHeartbeatDoesNotLeaveTargetOnline() {
        assertEquals(
            "offline",
            normalizeDeviceStatus("online", lastSeenAtMillis = null, nowMillis = 100_000L)
        )
    }

    @Test
    fun disconnectedStateIsNotChangedByTimestamp() {
        assertEquals(
            "offline",
            normalizeDeviceStatus("offline", lastSeenAtMillis = 99_000L, nowMillis = 100_000L)
        )
    }

    @Test
    fun smallFutureClockSkewDoesNotCauseFalseOffline() {
        assertEquals(
            "online",
            normalizeDeviceStatus("online", lastSeenAtMillis = 150_000L, nowMillis = 100_000L)
        )
    }
}
