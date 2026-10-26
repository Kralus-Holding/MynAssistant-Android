package com.mynkralus.mynassistant

/**
 * Converts desktop-pairing HTTP failures into safe, actionable user messages.
 * This deliberately handles deployment/configuration failures before any API
 * detail so a missing endpoint is not incorrectly reported as a bad pairing code.
 */
internal object DesktopPairingHttpErrorPolicy {
    fun message(responseCode: Int, detail: String?): String {
        return when {
            responseCode == 401 ->
                "Telefon hesabı doğrulanamadı. Yeniden giriş yapıp tekrar dene."
            responseCode == 429 ->
                "Çok fazla eşleştirme denemesi yapıldı. Biraz bekleyip tekrar dene."
            responseCode == 404 || responseCode == 405 ->
                "Bu sunucuda telefon-PC eşleştirme uç noktası henüz yayımlanmamış. Sunucu güncellendikten sonra tekrar dene."
            responseCode == 503 ->
                "Telefon-PC eşleştirmesi sunucuda yapılandırılmamış. Sunucu yöneticisi Firebase ayarlarını kontrol etmeli."
            !detail.isNullOrBlank() ->
                detail.trim()
            else ->
                "Eşleştirme kodu kabul edilmedi. Kodu kontrol edip yeniden dene."
        }
    }
}
