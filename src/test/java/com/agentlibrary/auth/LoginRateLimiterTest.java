package com.agentlibrary.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LoginRateLimiter}.
 */
class LoginRateLimiterTest {

    private LoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new LoginRateLimiter();
    }

    @Test
    void first10AttemptsFromSameIp_succeed() {
        String ip = "192.168.1.1";
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.resolveBucket(ip).tryConsume(1),
                    "Attempt " + (i + 1) + " should succeed");
        }
    }

    @Test
    void eleventhAttemptFromSameIp_fails() {
        String ip = "10.0.0.1";
        // Exhaust 10 tokens
        for (int i = 0; i < 10; i++) {
            limiter.resolveBucket(ip).tryConsume(1);
        }
        // 11th should fail
        assertFalse(limiter.resolveBucket(ip).tryConsume(1),
                "11th attempt should be rate-limited");
    }

    @Test
    void differentIps_haveIndependentBuckets() {
        String ip1 = "1.1.1.1";
        String ip2 = "2.2.2.2";

        // Exhaust ip1
        for (int i = 0; i < 10; i++) {
            limiter.resolveBucket(ip1).tryConsume(1);
        }
        assertFalse(limiter.resolveBucket(ip1).tryConsume(1), "ip1 should be exhausted");

        // ip2 should still have all tokens
        assertTrue(limiter.resolveBucket(ip2).tryConsume(1), "ip2 should not be affected");
    }

    @Test
    void resetBucket_allowsNewAttempts() {
        String ip = "172.16.0.1";

        // Exhaust
        for (int i = 0; i < 10; i++) {
            limiter.resolveBucket(ip).tryConsume(1);
        }
        assertFalse(limiter.resolveBucket(ip).tryConsume(1), "Should be exhausted");

        // Reset
        limiter.resetBucket(ip);

        // Should be able to consume again
        assertTrue(limiter.resolveBucket(ip).tryConsume(1), "Should succeed after reset");
    }

    @Test
    void resetBucket_onlyAffectsSpecifiedIp() {
        String ip1 = "3.3.3.3";
        String ip2 = "4.4.4.4";

        // Exhaust both
        for (int i = 0; i < 10; i++) {
            limiter.resolveBucket(ip1).tryConsume(1);
            limiter.resolveBucket(ip2).tryConsume(1);
        }

        // Reset only ip1
        limiter.resetBucket(ip1);

        assertTrue(limiter.resolveBucket(ip1).tryConsume(1), "ip1 should be reset");
        assertFalse(limiter.resolveBucket(ip2).tryConsume(1), "ip2 should still be exhausted");
    }
}
