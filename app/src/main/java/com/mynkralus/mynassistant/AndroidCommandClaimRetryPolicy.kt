package com.mynkralus.mynassistant

/**
 * Bounded retry policy for transient Android-device command claim failures.
 * The receiver stops retrying after three attempts and waits for a queue change.
 */
internal object AndroidCommandClaimRetryPolicy {
    const val MAX_RETRIES = 3
    const val BASE_DELAY_MILLIS = 2_000L

    fun delayMillis(retryNumber: Int): Long? =
        retryNumber.takeIf { it in 1..MAX_RETRIES }?.times(BASE_DELAY_MILLIS)
}
