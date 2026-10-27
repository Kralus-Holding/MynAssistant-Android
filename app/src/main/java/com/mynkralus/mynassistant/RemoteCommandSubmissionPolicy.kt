package com.mynkralus.mynassistant

import java.util.Locale

internal const val REMOTE_COMMAND_SUBMISSION_TIMEOUT_MILLIS = 20_000L

/**
 * Allows only one outstanding remote command per account screen. The submission
 * flag covers the asynchronous interval before Firestore returns a command ID;
 * the listener flag covers the period while waiting for that command's result.
 */
internal fun isRemoteCommandSubmissionAllowed(
    submissionInProgress: Boolean,
    hasActiveResultListener: Boolean
): Boolean = !submissionInProgress && !hasActiveResultListener

internal fun isRemoteCommandSubmissionTimeoutExpired(
    submissionInProgress: Boolean,
    startedAtMillis: Long,
    nowMillis: Long,
    timeoutMillis: Long = REMOTE_COMMAND_SUBMISSION_TIMEOUT_MILLIS
): Boolean = submissionInProgress &&
    startedAtMillis > 0L &&
    nowMillis >= startedAtMillis &&
    nowMillis - startedAtMillis >= timeoutMillis

/**
 * "pending" means Firestore accepted a queued document, not that Windows received it.
 * Only "running" confirms that the Agent claimed the command.
 */
internal fun remoteCommandStatusMessage(status: String): String =
    when (status.lowercase(Locale.ROOT)) {
        "pending" -> "Komut kuyruğa alındı; Windows Agent tarafından alınması bekleniyor."
        "running" -> "Windows Agent komutu aldı ve uyguluyor..."
        else -> "Windows komut durumu: $status"
    }
