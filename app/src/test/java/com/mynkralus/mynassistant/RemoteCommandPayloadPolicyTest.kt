package com.mynkralus.mynassistant

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteCommandPayloadPolicyTest {
    @Test
    fun serverControlledFieldsCannotBeOverriddenByPayload() {
        val input = mapOf(
            "text" to "Chrome aç",
            "dx" to 12,
            "ownerId" to "attacker",
            "deviceId" to "other-device",
            "command" to "delete",
            "status" to "completed",
            "createdAt" to "forged",
            "result" to "forged result"
        )

        assertEquals(
            mapOf("text" to "Chrome aç", "dx" to 12),
            sanitizeRemoteCommandPayload(input)
        )
    }

    @Test
    fun actionPayloadFieldsArePreserved() {
        val input = mapOf("text" to "Not Defteri aç", "dx" to -4, "dy" to 8)
        assertEquals(input, sanitizeRemoteCommandPayload(input))
    }
}
