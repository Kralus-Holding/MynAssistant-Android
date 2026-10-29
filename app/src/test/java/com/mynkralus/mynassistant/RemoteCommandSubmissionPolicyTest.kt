package com.mynkralus.mynassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteCommandSubmissionPolicyTest {
    @Test
    fun allowsCommandWhenNoSubmissionOrResultIsPending() {
        assertTrue(isRemoteCommandSubmissionAllowed(false, false))
    }

    @Test
    fun rejectsSecondTapWhileFirstSubmissionIsWaitingForFirestore() {
        assertFalse(isRemoteCommandSubmissionAllowed(true, false))
    }

    @Test
    fun rejectsCommandWhilePreviousResultListenerIsActive() {
        assertFalse(isRemoteCommandSubmissionAllowed(false, true))
    }

    @Test
    fun rejectsWhenSubmissionAndResultListenerAreBothActive() {
        assertFalse(isRemoteCommandSubmissionAllowed(true, true))
    }

    @Test
    fun submissionTimeoutExpiresOnlyAfterTwentySecondsWhileStillSubmitting() {
        assertFalse(isRemoteCommandSubmissionTimeoutExpired(true, 10_000L, 29_999L))
        assertTrue(isRemoteCommandSubmissionTimeoutExpired(true, 10_000L, 30_000L))
        assertFalse(isRemoteCommandSubmissionTimeoutExpired(false, 10_000L, 30_000L))
    }

    @Test
    fun commandStatusDistinguishesQueueFromAgentAcknowledgement() {
        assertEquals(
            "Komut kuyruğa alındı; Windows Agent tarafından alınması bekleniyor.",
            remoteCommandStatusMessage("pending")
        )
        assertEquals(
            "Windows Agent komutu aldı ve uyguluyor...",
            remoteCommandStatusMessage("running")
        )
    }

    @Test
    fun commandStatusUsesCorrectPlatformWording() {
        assertEquals(
            "Komut seçili Android cihazının alması için kuyrukta bekliyor.",
            remoteCommandStatusMessageForTarget("pending", true)
        )
        assertEquals(
            "Android cihaz komutu çalıştırıyor...",
            remoteCommandStatusMessageForTarget("running", true)
        )
        assertEquals(
            "Komut kuyruğa alındı; Windows Agent tarafından alınması bekleniyor.",
            remoteCommandStatusMessageForTarget("pending", false)
        )
        assertEquals(
            "Android komut durumu: cancelled",
            remoteCommandStatusMessageForTarget("cancelled", true)
        )
    }

    @Test
    fun timeoutMessagesAreSpecificToTargetPlatformAndPhase() {
        assertTrue(remoteCommandTimeoutMessage(true, false).contains("Android cihaz üzerinden"))
        assertTrue(remoteCommandTimeoutMessage(true, true).contains("Android komutunun"))
        assertTrue(remoteCommandTimeoutMessage(false, false).contains("Bilgisayardan"))
        assertTrue(remoteCommandTimeoutMessage(false, true).contains("Komutun sunucu tarafından"))
    }

    @Test
    fun commandValidityWindowIsBoundedToOneMinute() {
        assertEquals(60_000L, REMOTE_COMMAND_VALIDITY_MILLIS)
    }

}
