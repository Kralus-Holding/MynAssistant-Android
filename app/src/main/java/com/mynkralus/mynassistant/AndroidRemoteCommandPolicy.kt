package com.mynkralus.mynassistant

import java.util.Locale

/**
 * Limits phone-to-phone remote commands to reversible UI and media actions.
 * Messages, calls, camera/gallery access and other privacy-sensitive actions require
 * local confirmation and are never executed silently on a second phone.
 */
internal object AndroidRemoteCommandPolicy {
    private val allowedCommands = setOf(
        "ses artır", "sesi artır", "sesini artır", "ses yükselt", "sesi yükselt",
        "ses arttır", "sesi arttır", "ses azalt", "sesi azalt", "sesini azalt",
        "ses kıs", "sesi kıs", "sessize al", "sesi kapat", "sesini kapat",
        "medya sesini kapat", "medya sesini sessize al", "medya sesini aç", "sessizden çıkar",
        "bluetooth ayarlarını aç", "bluetooth aç", "bluetooth'u aç", "bluetoothu aç",
        "wifi ayarlarını aç", "wi-fi ayarlarını aç", "wifi aç", "wi-fi aç", "kablosuz ağ ayarlarını aç",
        "ayarları aç", "telefon ayarlarını aç", "android ayarlarını aç",
        "arama ekranını aç", "telefon uygulamasını aç", "arama aç", "telefonu aç",
        "chrome aç", "tarayıcı aç", "internet aç", "google aç",
        "youtube aç", "youtube'u aç", "youtubeu aç",
        "play store aç", "play store'u aç", "play storeu aç"
    )

    fun isAllowed(rawCommand: String): Boolean {
        val normalized = rawCommand.trim()
            .lowercase(Locale.forLanguageTag("tr-TR"))
            .replace(Regex("\\s+"), " ")
        return normalized.isNotBlank() &&
            normalized.length <= 120 &&
            normalized.none { it.isISOControl() } &&
            normalized in allowedCommands
    }

    fun rejectionMessage(): String =
        "Bu Android komutu uzaktan otomatik çalıştırılmadı. İzin verilen temel ses, ayar ve uygulama açma komutlarını kullan. Arama, mesaj, kamera ve özel içerik işlemleri için cihazda yerel onay gerekir."
}
