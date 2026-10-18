package com.mynkralus.mynassistant

private val reservedServerCommandFields = setOf(
    "ownerId",
    "deviceId",
    "command",
    "status",
    "createdAt",
    "startedAt",
    "processedAt",
    "result"
)

/**
 * Keeps command identity and lifecycle fields under the app's control.
 * Caller-supplied payload may contain action inputs only (for example text/dx/dy).
 */
internal fun sanitizeRemoteCommandPayload(payload: Map<String, Any>): Map<String, Any> =
    payload.filterKeys { it !in reservedServerCommandFields }
