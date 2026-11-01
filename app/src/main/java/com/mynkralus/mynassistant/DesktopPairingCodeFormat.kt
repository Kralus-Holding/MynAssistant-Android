package com.mynkralus.mynassistant

import java.util.Locale

internal data class DesktopPairingQrPayload(
    val code: String,
    val signature: String?,
    val expiresAtUnix: Long?
) {
    val formattedCode: String
        get() = DesktopPairingCodeFormat.format(code)
}

internal object DesktopPairingCodeFormat {
    private const val QR_PREFIX = "MYNASSISTANT-PAIR"
    private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val CODE_LENGTH = 10

    fun normalize(value: String): String = value
        .uppercase(Locale.ROOT)
        .filter { it in CODE_ALPHABET }
        .take(CODE_LENGTH)

    fun format(value: String): String {
        val code = normalize(value)
        return if (code.length > 5) "${code.take(5)}-${code.drop(5)}" else code
    }

    fun parseQr(rawValue: String, nowEpochSeconds: Long = System.currentTimeMillis() / 1000): DesktopPairingQrPayload? {
        val raw = rawValue.trim()
        if (raw.isEmpty()) return null

        if (raw.startsWith("$QR_PREFIX:", ignoreCase = true)) {
            val parts = raw.split(':')
            if (parts.size != 4 || !parts[0].equals(QR_PREFIX, ignoreCase = true)) return null

            val code = normalize(parts[1])
            if (code.length != CODE_LENGTH || !isValidCode(parts[1])) return null

            val expiry = parts[2].toLongOrNull() ?: return null
            // Do not reject a valid signed QR because the phone clock is slightly behind.
            // The Server performs the authoritative HMAC and expiry validation.
            if (expiry <= nowEpochSeconds) return null

            val signature = parts[3]
            if (signature.length != 43 ||
                signature.any { !(it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_') }
            ) return null

            return DesktopPairingQrPayload(code, signature, expiry)
        }

        // Older Windows builds may encode only the one-time manual code.
        val code = normalize(raw)
        if (code.length != CODE_LENGTH || !isValidCode(raw)) return null
        return DesktopPairingQrPayload(code, null, null)
    }

    fun isValidCode(value: String): Boolean {
        val normalized = value.uppercase(Locale.ROOT).filter { it in CODE_ALPHABET }
        return normalized.length == CODE_LENGTH &&
            value.filterNot { it == '-' || it.isWhitespace() }.all { it.uppercaseChar() in CODE_ALPHABET }
    }
}
