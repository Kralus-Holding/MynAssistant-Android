package com.mynkralus.mynassistant

import org.junit.Assert.assertEquals
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

    @Test
    fun onlineCloudAgentWinsOverSelectedLegacyLocalBridge() {
        val devices = listOf(
            PcDeviceRouteCandidate("windows-user-123", "mynai-local-bridge", true),
            PcDeviceRouteCandidate("win-agent-123", "firebase-agent", true)
        )
        assertEquals(
            "win-agent-123",
            DevicePresencePolicy.preferredPcDeviceId(
                devices, "windows-user-123"
            )
        )
    }

    @Test
    fun explicitlySelectedOnlineCloudAgentStaysSelected() {
        val devices = listOf(
            PcDeviceRouteCandidate("win-a", "firebase-agent", true),
            PcDeviceRouteCandidate("win-b", "firebase-agent", true)
        )
        assertEquals("win-b", DevicePresencePolicy.preferredPcDeviceId(devices, "win-b"))
    }

    @Test
    fun onlineCloudAgentWinsOverStaleSelectedLocalRecord() {
        val devices = listOf(
            PcDeviceRouteCandidate("windows-user-123", "mynai-local-bridge", false),
            PcDeviceRouteCandidate("win-agent-123", "firebase-agent", true)
        )
        assertEquals(
            "win-agent-123",
            DevicePresencePolicy.preferredPcDeviceId(
                devices, "windows-user-123"
            )
        )
    }
    @Test
    fun onlineLegacyBridgeIsNotAcceptedAsCloudQueueTarget() {
        val devices = listOf(
            PcDeviceRouteCandidate("windows-user-123", "mynai-local-bridge", true)
        )
        assertEquals(null, DevicePresencePolicy.preferredPcDeviceId(devices, "windows-user-123"))
    }

    @Test
    fun offlineCloudAgentIsNotSelected() {
        val devices = listOf(
            PcDeviceRouteCandidate("win-agent-123", "firebase-agent", false)
        )
        assertEquals(null, DevicePresencePolicy.preferredPcDeviceId(devices, "win-agent-123"))
    }

    @Test
    fun explicitOfflineCloudAgentRemainsSelectedInsteadOfAnotherOnlinePc() {
        val devices = listOf(
            PcDeviceRouteCandidate("win-offline", "firebase-agent", false),
            PcDeviceRouteCandidate("win-online", "firebase-agent", true)
        )
        assertEquals(
            "win-offline",
            DevicePresencePolicy.selectedPcDeviceId("win-offline", devices, "win-online")
        )
    }

    @Test
    fun disappearedManualSelectionFallsBackToOnlineCloudAgent() {
        val devices = listOf(
            PcDeviceRouteCandidate("win-online", "firebase-agent", true)
        )
        assertEquals(
            "win-online",
            DevicePresencePolicy.selectedPcDeviceId("win-removed", devices, "win-online")
        )
    }

    @Test
    fun explicitRegisteredLocalBridgeCanRemainSelected() {
        val devices = listOf(
            PcDeviceRouteCandidate("windows-user-123", "mynai-local-bridge", false),
            PcDeviceRouteCandidate("win-agent-123", "firebase-agent", true)
        )
        assertEquals(
            "windows-user-123",
            DevicePresencePolicy.selectedPcDeviceId("windows-user-123", devices, "win-agent-123")
        )
    }

}
