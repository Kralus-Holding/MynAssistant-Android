package com.mynkralus.mynassistant

/**
 * Allows only one outstanding remote command per account screen. The submission
 * flag covers the asynchronous interval before Firestore returns a command ID;
 * the listener flag covers the period while waiting for that command's result.
 */
internal fun isRemoteCommandSubmissionAllowed(
    submissionInProgress: Boolean,
    hasActiveResultListener: Boolean
): Boolean = !submissionInProgress && !hasActiveResultListener
