package edu.fudan.elearning.sync.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 节流/退避策略的数值单测（与桌面端语义一致）。 */
class RequestPolicyTest {

    private val policy = RequestPolicy()

    @Test
    fun backoff_growsExponentiallyAndCaps() {
        assertEquals(500, policy.backoffMs(1))
        assertEquals(1_000, policy.backoffMs(2))
        assertEquals(2_000, policy.backoffMs(3))
        assertEquals(30_000, policy.backoffMs(20))
        assertEquals(500, policy.backoffMs(0))
    }

    @Test
    fun retryAfter_parsesSecondsAndFallsBack() {
        assertEquals(2_000, policy.retryAfterMs("2"))
        assertEquals(0, policy.retryAfterMs("0"))
        assertEquals(8_000, policy.retryAfterMs(null))
        assertEquals(8_000, policy.retryAfterMs("Wed, 21 Oct 2015 07:28:00 GMT"))
        // 超大值必须被上限截断，避免一次睡十几分钟
        assertEquals(30_000, policy.retryAfterMs("600"))
    }

    @Test
    fun rateLimitDetection_matchesDesktopRules() {
        assertTrue(policy.isRateLimited(429, ""))
        assertTrue(policy.isRateLimited(403, "Rate Limit Exceeded"))
        assertFalse(policy.isRateLimited(403, "forbidden"))
        assertFalse(policy.isRateLimited(500, ""))
    }

    @Test
    fun retryable_onlyForTransientFailures() {
        assertTrue(policy.isRetryable(500, rateLimited = false))
        assertTrue(policy.isRetryable(503, rateLimited = false))
        assertTrue(policy.isRetryable(408, rateLimited = false))
        assertTrue(policy.isRetryable(429, rateLimited = true))
        assertFalse(policy.isRetryable(400, rateLimited = false))
        assertFalse(policy.isRetryable(404, rateLimited = false))
    }

    @Test
    fun slowDownOnlyWhenRemainingIsLow() {
        assertEquals(0, policy.slowDownMs(50))
        assertEquals(0, policy.slowDownMs(15))
        assertEquals(500, policy.slowDownMs(14))
        assertEquals(0, policy.slowDownMs(null))
        assertEquals(7, policy.remaining(" 7 "))
        assertEquals(null, policy.remaining("many"))
    }
}
