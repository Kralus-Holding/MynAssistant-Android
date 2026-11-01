package com.mynkralus.mynassistant

import java.security.MessageDigest
import java.util.Base64

internal const val MAX_PC_FILE_DOWNLOAD_BYTES = 500_000L

internal data class PcFileDownloadPayload(
    val name: String,
    val path: String,
    val size: Long,
    val sha256: String,
    val content: ByteArray
)

/**
 * Validates a bounded Windows file payload before the app writes any bytes to a user-selected URI.
 * The transfer currently supports small files only; larger files need a separately implemented chunk protocol.
 */
internal fun verifyPcFileDownloadPayload(
    name: String,
    path: String,
    size: Long,
    expectedSha256: String,
    contentBase64: String
): PcFileDownloadPayload {
    require(name.isNotBlank()) { "İndirilen dosyanın adı eksik." }
    require(path.isNotBlank() && path.length <= 1024) { "İndirilen dosyanın yolu geçersiz." }
    require(size in 0..MAX_PC_FILE_DOWNLOAD_BYTES) {
        "Dosya $size bayt; bu sürüm tek yanıtla en fazla $MAX_PC_FILE_DOWNLOAD_BYTES bayt indirir. Büyük dosyalar için parçalı aktarım henüz etkin değil."
    }
    require(expectedSha256.matches(Regex("^[0-9a-fA-F]{64}$"))) {
        "İndirilen dosyanın SHA-256 bilgisi geçersiz."
    }

    val bytes = try {
        Base64.getDecoder().decode(contentBase64)
    } catch (error: IllegalArgumentException) {
        throw IllegalArgumentException("Dosya içeriği geçerli Base64 değil.", error)
    }
    require(bytes.size.toLong() == size) {
        "İndirilen dosyanın boyutu yanıtla uyuşmuyor."
    }

    val actualHash = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    require(actualHash.equals(expectedSha256, ignoreCase = true)) {
        "İndirilen dosyanın SHA-256 doğrulaması başarısız."
    }

    return PcFileDownloadPayload(name, path, size, actualHash, bytes)
}
