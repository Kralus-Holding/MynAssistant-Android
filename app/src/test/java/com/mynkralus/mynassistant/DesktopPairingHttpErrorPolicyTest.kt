package com.mynkralus.mynassistant

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopPairingHttpErrorPolicyTest {
    @Test
    fun missingOrWrongMethodEndpointIsNotReportedAsBadCode() {
        val expected =
            "Bu sunucuda telefon-PC eşleştirme uç noktası henüz yayımlanmamış. Sunucu güncellendikten sonra tekrar dene."

        assertEquals(expected, DesktopPairingHttpErrorPolicy.message(404, null))
        assertEquals(expected, DesktopPairingHttpErrorPolicy.message(405, ""))
    }

    @Test
    fun unavailableServerExplainsFirebaseConfigurationRequirement() {
        assertEquals(
            "Telefon-PC eşleştirmesi sunucuda yapılandırılmamış. Sunucu yöneticisi Firebase ayarlarını kontrol etmeli.",
            DesktopPairingHttpErrorPolicy.message(503, "internal configuration details")
        )
    }

    @Test
    fun authenticationAndRateLimitErrorsRemainActionable() {
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
    fun unknownStatusUsesSafeServerDetailWhenPresent() {
        assertEquals(
            "Kod süresi doldu.",
            DesktopPairingHttpErrorPolicy.message(400, "  Kod süresi doldu.  ")
        )
        assertEquals(
            "Eşleştirme kodu kabul edilmedi. Kodu kontrol edip yeniden dene.",
            DesktopPairingHttpErrorPolicy.message(400, null)
        )
    }
}
