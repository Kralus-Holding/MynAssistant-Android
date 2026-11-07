package com.mynkralus.mynassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidCommandClaimRetryPolicyTest {
    @Test
    fun appliesIncreasingBackoffForThreeRetries() {
        assertEquals(2_000L, AndroidCommandClaimRetryPolicy.delayMillis(1))
        assertEquals(4_000L, AndroidCommandClaimRetryPolicy.delayMillis(2))
        assertEquals(6_000L, AndroidCommandClaimRetryPolicy.delayMillis(3))
    }

    @Test
    fun doesNotScheduleInfiniteRetries() {
        assertNull(AndroidCommandClaimRetryPolicy.delayMillis(0))
        assertNull(AndroidCommandClaimRetryPolicy.delayMillis(4))
        assertNull(AndroidCommandClaimRetryPolicy.delayMillis(10))
    }
}
