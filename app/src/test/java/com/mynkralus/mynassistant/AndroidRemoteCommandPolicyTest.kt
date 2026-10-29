package com.mynkralus.mynassistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidRemoteCommandPolicyTest {
    @Test
    fun allowsBasicReversibleVolumeCommands() {
        assertTrue(AndroidRemoteCommandPolicy.isAllowed("Sesi artır"))
        assertTrue(AndroidRemoteCommandPolicy.isAllowed("  sesi kıs  "))
        assertTrue(AndroidRemoteCommandPolicy.isAllowed("medya sesini sessize al"))
    }

    @Test
    fun allowsSettingsAndPublicAppLaunchCommands() {
        assertTrue(AndroidRemoteCommandPolicy.isAllowed("Wi-Fi ayarlarını aç"))
        assertTrue(AndroidRemoteCommandPolicy.isAllowed("Chrome aç"))
        assertTrue(AndroidRemoteCommandPolicy.isAllowed("YouTube'u aç"))
    }

    @Test
    fun rejectsCallsMessagesAndContactActions() {
        assertFalse(AndroidRemoteCommandPolicy.isAllowed("annemi ara"))
        assertFalse(AndroidRemoteCommandPolicy.isAllowed("WhatsApp'tan mesaj gönder"))
        assertFalse(AndroidRemoteCommandPolicy.isAllowed("numara 5551234567 ara"))
        assertFalse(AndroidRemoteCommandPolicy.isAllowed("SMS gönder"))
    }

    @Test
    fun rejectsCameraGalleryAndArbitraryCommands() {
        assertFalse(AndroidRemoteCommandPolicy.isAllowed("fotoğraf çek"))
        assertFalse(AndroidRemoteCommandPolicy.isAllowed("galeri aç"))
        assertFalse(AndroidRemoteCommandPolicy.isAllowed("https://example.com"))
        assertFalse(AndroidRemoteCommandPolicy.isAllowed("   "))
        assertFalse(AndroidRemoteCommandPolicy.isAllowed("x".repeat(121)))
    }
}
