package com.mynkralus.mynassistant

/**
 * Locally validated QR fields. The server remains authoritative for signature, expiry
 * and one-time-use checks when the user confirms the pairing.
 */
internal data class DesktopPairingQrPayload(
    val code: String,
    val expiresAtUnix: Long,
    val signature: String
)

internal object DesktopPairingQrPayloadParser {
    private const val MARKER = "MYNASSISTANT-PAIR"
    private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val signaturePattern = Regex("^[A-Za-z0-9_-]{43}$")

    fun parse(rawValue: String?): DesktopPairingQrPayload? {
        val parts = rawValue?.trim()?.split(':', limit = 4) ?: return null
        if (parts.size != 4 || !parts[0].equals(MARKER, ignoreCase = true)) return null

        val code = parts[1].uppercase(java.util.Locale.ROOT)
        if (code.length != 10 || code.any { it !in CODE_ALPHABET }) return null

        val expiry = parts[2].toLongOrNull()?.takeIf { it > 0L } ?: return null
        val signature = parts[3]
        if (!signaturePattern.matches(signature)) return null

        return DesktopPairingQrPayload(code, expiry, signature)
    }
}
