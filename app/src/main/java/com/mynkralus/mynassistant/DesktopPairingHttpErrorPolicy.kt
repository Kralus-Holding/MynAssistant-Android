package com.mynkralus.mynassistant

/**
 * Gives users actionable explanations for desktop-pairing API failures.
 * In particular, a missing route is a server deployment problem, not a bad code.
 */
internal object DesktopPairingHttpErrorPolicy {
    fun message(responseCode: Int, detail: String?): String {
        return when {
            responseCode == 404 ->
                "Telefon-PC eşleştirme API'si canlı sunucuda yayımlanmamış (MYN-SERVER-404). Sunucu dağıtımı tamamlanmalı."
            responseCode == 405 ->
                "Sunucudaki telefon-PC eşleştirme uç noktası bu isteği kabul etmiyor (MYN-SERVER-405). Sunucu sürümünü kontrol et."
            responseCode == 503 ->
                "Telefon-PC eşleştirme API'si açık ancak Firebase sunucu ayarları eksik veya geçersiz (MYN-SERVER-503). Sunucu yapılandırması kontrol edilmeli."
            responseCode == 401 ->
                "Telefon hesabı doğrulanamadı. Yeniden giriş yapıp tekrar dene."
            responseCode == 429 ->
                "Çok fazla eşleştirme denemesi yapıldı. Biraz bekleyip tekrar dene."
            responseCode == 410 ->
                "Eşleştirme kodunun süresi doldu. Windows'ta yeni bir kod oluştur."
            !detail.isNullOrBlank() ->
                detail.trim()
            else ->
                "Eşleştirme isteği HTTP $responseCode yanıtıyla başarısız oldu. Daha sonra tekrar dene."
        }
    }
}
