package com.mynkralus.mynassistant

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantCommandRoutingPolicyTest {
    @Test
    fun explicitPhoneTargetOverridesComputerPrefix() {
        assertEquals("PHONE", resolveAssistantCommandTarget("PHONE", hasComputerPrefix = true))
    }

    @Test
    fun explicitPcTargetDoesNotDependOnPrefix() {
        assertEquals("PC", resolveAssistantCommandTarget("PC", hasComputerPrefix = false))
    }

    @Test
    fun autoTargetsWindowsOnlyWhenComputerPrefixIsPresent() {
        assertEquals("PC", resolveAssistantCommandTarget("AUTO", hasComputerPrefix = true))
        assertEquals("PHONE", resolveAssistantCommandTarget("AUTO", hasComputerPrefix = false))
    }

    @Test
    fun unexpectedTargetFallsBackToPrefixBasedRouting() {
        assertEquals("PC", resolveAssistantCommandTarget("unknown", hasComputerPrefix = true))
        assertEquals("PHONE", resolveAssistantCommandTarget("unknown", hasComputerPrefix = false))
    }
}
