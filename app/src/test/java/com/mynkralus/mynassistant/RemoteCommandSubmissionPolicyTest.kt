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
    fun commandValidityWindowIsBoundedToOneMinute() {
        assertEquals(60_000L, REMOTE_COMMAND_VALIDITY_MILLIS)
    }

}
