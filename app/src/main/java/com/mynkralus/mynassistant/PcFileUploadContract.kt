package com.mynkralus.mynassistant

import java.security.MessageDigest
import java.util.Base64

internal const val MAX_PC_FILE_UPLOAD_BYTES = 500_000L

internal data class PcFileUploadPayload(
    val name: String,
    val destinationPath: String,
    val size: Long,
    val sha256: String,
    val contentBase64: String
) {
    fun toCommandFields(): Map<String, Any> = mapOf(
        "destinationPath" to destinationPath,
        "name" to name,
        "size" to size,
        "sha256" to sha256,
        "contentBase64" to contentBase64
    )
}

internal fun buildPcFileUploadPayload(
    name: String,
    destinationPath: String,
    content: ByteArray
): PcFileUploadPayload {
    require(name.isNotBlank() && name.length <= 255) { "Yüklenecek dosyanın adı geçersiz." }
    require(name != "." && name != ".." && !name.contains('/') && !name.contains('\\')) {
        "Dosya adı klasör yolu içeremez."
    }
    require(name.none { it.code < 32 } && !Regex("""[<>:"/\\|?*]""").containsMatchIn(name)) {
        "Dosya adında Windows tarafından desteklenmeyen karakter var."
    }
    require(!name.endsWith('.') && !name.endsWith(' ')) {
        "Dosya adı nokta veya boşlukla bitemez."
    }
    val reservedBaseName = name.substringBefore('.').uppercase()
    require(reservedBaseName !in setOf("CON", "PRN", "AUX", "NUL") &&
        !reservedBaseName.matches(Regex("^(COM|LPT)[1-9]$"))) {
        "Bu Windows dosya adı kullanılamaz."
    }
    require(destinationPath.isNotBlank() && destinationPath.length <= 1024) {
        "Bilgisayardaki hedef klasör yolu geçersiz."
    }
    require(content.size.toLong() <= MAX_PC_FILE_UPLOAD_BYTES) {
        "Bu sürüm tek dosyada en fazla $MAX_PC_FILE_UPLOAD_BYTES bayt yükler. Büyük dosyalar için parçalı aktarım henüz etkin değil."
    }
    val hash = MessageDigest.getInstance("SHA-256")
        .digest(content)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    return PcFileUploadPayload(
        name = name,
        destinationPath = destinationPath,
        size = content.size.toLong(),
        sha256 = hash,
        contentBase64 = Base64.getEncoder().encodeToString(content)
    )
}
