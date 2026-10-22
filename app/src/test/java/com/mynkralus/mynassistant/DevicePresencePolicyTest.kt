package com.mynkralus.mynassistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicePresencePolicyTest {
    @Test
    fun androidDeviceIsNotAPcRemoteTarget() {
        assertFalse(DevicePresencePolicy.isPcTarget("android-abc123", "Android", "android-app"))
    }

    @Test
    fun firebaseAgentIsAPcTargetEvenWithLegacyPlatformName() {
        assertTrue(DevicePresencePolicy.isPcTarget("agent-123", "Unknown", "firebase-agent"))
    }

    @Test
    fun legacyWindowsBridgeIdIsAPcTarget() {
        assertTrue(DevicePresencePolicy.isPcTarget("windows-user-123", "Unknown", "mynai-local-bridge"))
    }

    @Test
    fun onlineStatusWithoutLastSeenIsNotOnline() {
        assertFalse(DevicePresencePolicy.isOnline("online", null, 1_000_000L))
    }

    @Test
    fun freshHeartbeatKeepsDeviceOnline() {
        val now = 1_000_000L
        assertTrue(DevicePresencePolicy.isOnline("online", now - 60_000L, now))
    }

    @Test
    fun heartbeatOlderThanThreeMinutesExpires() {
        val now = 1_000_000L
        assertFalse(
            DevicePresencePolicy.isOnline(
                "online",
                now - DevicePresencePolicy.ONLINE_WINDOW_MILLIS,
                now
            )
        )
    }

    @Test
    fun offlineStatusCannotBeOverriddenByFreshHeartbeat() {
        val now = 1_000_000L
        assertFalse(DevicePresencePolicy.isOnline("offline", now - 1_000L, now))
    }

    @Test
    fun futureHeartbeatIsNotTrustedAsOnline() {
        val now = 1_000_000L
        assertFalse(DevicePresencePolicy.isOnline("online", now + 1_000L, now))
    }
}
