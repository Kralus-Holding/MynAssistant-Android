package com.mynkralus.mynassistant

import java.util.Locale

/**
 * Resolves the platform that should execute a command. An explicit platform choice
 * always wins; AUTO routes to Windows only when the user supplied a PC prefix.
 */
internal fun resolveAssistantCommandTarget(
    commandTarget: String,
    hasComputerPrefix: Boolean
): String = when (commandTarget.uppercase(Locale.ROOT)) {
    "PHONE" -> "PHONE"
    "PC" -> "PC"
    else -> if (hasComputerPrefix) "PC" else "PHONE"
}
