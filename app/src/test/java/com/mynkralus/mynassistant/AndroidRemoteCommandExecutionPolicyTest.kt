package com.mynkralus.mynassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidRemoteCommandExecutionPolicyTest {
    private fun rejection(
        receiverDisposed: Boolean = false,
        sameAuthenticatedAccount: Boolean = true,
        notExpiredAtExecution: Boolean = true,
        foreground: Boolean = true,
        commandName: String = "text_command",
        commandAllowed: Boolean = true
    ) = AndroidRemoteCommandExecutionPolicy.rejectionMessage(
        receiverDisposed,
        sameAuthenticatedAccount,
        notExpiredAtExecution,
        foreground,
        commandName,
        commandAllowed
    )

    @Test
    fun acceptsOnlyAValidForegroundCommandForTheSameAccount() {
        assertNull(rejection())
        assertNull(rejection(commandAllowed = true, commandName = "text_command"))
    }

    @Test
    fun disposedReceiverAndChangedAccountAreRejected() {
        assertTrue(rejection(receiverDisposed = true).orEmpty().contains("oturumu değişti"))
        assertTrue(rejection(sameAuthenticatedAccount = false).orEmpty().contains("oturumu değişti"))
    }

    @Test
    fun expiredCommandIsRejectedAtExecutionTime() {
        assertTrue(rejection(notExpiredAtExecution = false).orEmpty().contains("süresi doldu"))
    }

    @Test
    fun backgroundReceiverIsRejected() {
        assertTrue(rejection(foreground = false).orEmpty().contains("ön planda değil"))
    }

    @Test
    fun unknownCommandTypeIsRejected() {
        assertTrue(rejection(commandName = "delete_device").orEmpty().contains("desteklemiyor"))
    }

    @Test
    fun unsafeTextCommandIsRejectedByAllowlist() {
        assertTrue(rejection(commandAllowed = false).orEmpty().contains("yerel onay"))
    }

    @Test
    fun marksCompletedOnlyWhenEveryExecutionGuardPasses() {
        assertTrue(
            AndroidRemoteCommandExecutionPolicy.shouldMarkCompleted(
                receiverDisposed = false,
                sameAuthenticatedAccount = true,
                notExpiredAtExecution = true,
                foreground = true,
                commandAllowed = true,
                resultIndicatesFailure = false
            )
        )
        assertFalse(
            AndroidRemoteCommandExecutionPolicy.shouldMarkCompleted(
                receiverDisposed = false,
                sameAuthenticatedAccount = false,
                notExpiredAtExecution = true,
                foreground = true,
                commandAllowed = true,
                resultIndicatesFailure = false
            )
        )
        assertFalse(
            AndroidRemoteCommandExecutionPolicy.shouldMarkCompleted(
                receiverDisposed = false,
                sameAuthenticatedAccount = true,
                notExpiredAtExecution = false,
                foreground = true,
                commandAllowed = true,
                resultIndicatesFailure = false
            )
        )
    }

    @Test
    fun failureResultIsNeverReportedAsCompleted() {
        assertFalse(
            AndroidRemoteCommandExecutionPolicy.shouldMarkCompleted(
                receiverDisposed = false,
                sameAuthenticatedAccount = true,
                notExpiredAtExecution = true,
                foreground = true,
                commandAllowed = true,
                resultIndicatesFailure = true
            )
        )
    }
}
