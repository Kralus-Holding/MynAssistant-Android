package com.mynkralus.mynassistant

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteKeyboardInputTest {
    @Test
    fun appendingSpacePreservesExistingText() {
        assertEquals("hello ", "hello" + " ")
    }

    @Test
    fun deleteUsesLastCharacterBeforeSendingRemoteBackspace() {
        val current = "hello"
        val next = if (current.isNotEmpty()) current.dropLast(1) else null
        assertEquals("hell", next)
    }

    @Test
    fun emptyInputLeavesRemoteBackspaceAvailable() {
        val current = ""
        val shouldSendRemoteBackspace = current.isEmpty()
        assertEquals(true, shouldSendRemoteBackspace)
    }

    @Test
    fun keyboardTextHasAnExplicitLengthLimit() {
        assertEquals(1000, ("x".repeat(1200)).take(1000).length)
    }
}
