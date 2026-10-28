package com.mynkralus.mynassistant

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopPairingHttpErrorPolicyTest {
    @Test
    fun missingEndpointIsIdentifiedAsDeploymentFailure() {
        assertEquals(
            "Telefon-PC eşleştirme API'si canlı sunucuda yayımlanmamış (MYN-SERVER-404). Sunucu dağıtımı tamamlanmalı.",
            DesktopPairingHttpErrorPolicy.message(404, "<html>Not Found</html>")
        )
    }

    @Test
    fun methodNotAllowedIsNotReportedAsABadPairingCode() {
        assertEquals(
            "Sunucudaki telefon-PC eşleştirme uç noktası bu isteği kabul etmiyor (MYN-SERVER-405). Sunucu sürümünü kontrol et.",
            DesktopPairingHttpErrorPolicy.message(405, null)
        )
    }

    @Test
    fun serverConfigurationProblemIsActionable() {
        assertEquals(
            "Telefon-PC eşleştirme API'si açık ancak Firebase sunucu ayarları eksik veya geçersiz (MYN-SERVER-503). Sunucu yapılandırması kontrol edilmeli.",
            DesktopPairingHttpErrorPolicy.message(503, null)
        )
    }

    @Test
    fun authenticationAndRateLimitErrorsAreDistinct() {
        assertEquals(
            "Telefon hesabı doğrulanamadı. Yeniden giriş yapıp tekrar dene.",
            DesktopPairingHttpErrorPolicy.message(401, null)
        )
        assertEquals(
            "Çok fazla eşleştirme denemesi yapıldı. Biraz bekleyip tekrar dene.",
            DesktopPairingHttpErrorPolicy.message(429, null)
        )
    }

    @Test
    fun expiredCodeIsActionable() {
        assertEquals(
            "Eşleştirme kodunun süresi doldu. Windows'ta yeni bir kod oluştur.",
            DesktopPairingHttpErrorPolicy.message(410, null)
        )
    }

    @Test
    fun unknownFailuresUseSafeServerDetailOrStatusFallback() {
        assertEquals(
            "Kod kullanım limiti doldu.",
            DesktopPairingHttpErrorPolicy.message(400, "  Kod kullanım limiti doldu.  ")
        )
        assertEquals(
            "Eşleştirme isteği HTTP 500 yanıtıyla başarısız oldu. Daha sonra tekrar dene.",
            DesktopPairingHttpErrorPolicy.message(500, null)
        )
    }
    @Test
    fun mismatchedAccountIsRejectedWithActionableGuidance() {
        assertEquals(
            "Bu bilgisayarda farklı bir hesap açık. Eşleştirmek için iki cihazda da aynı hesapla oturum aç. Hesabı değiştirmek için Windows'ta 'Hesabı değiştir' seçeneğini kullan.",
            DesktopPairingHttpErrorPolicy.message(409, "Server detail should not replace the actionable account-mismatch message.")
        )
    }
}
