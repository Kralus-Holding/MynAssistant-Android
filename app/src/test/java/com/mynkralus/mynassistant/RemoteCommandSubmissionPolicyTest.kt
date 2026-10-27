package com.mynkralus.mynassistant

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
}
