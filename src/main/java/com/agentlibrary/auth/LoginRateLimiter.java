package com.agentlibrary.auth;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-IP rate limiter for login attempts using Bucket4j token buckets.
 *
 * <p>Each IP address gets its own bucket with a capacity of 10 attempts
 * that refills greedily over 10 minutes. When a bucket is exhausted,
 * further login attempts from that IP are rejected with 429.</p>
 *
 * <p>Successful authentication resets the bucket for the IP, allowing
 * the user to continue logging in if needed.</p>
 */
@Component
public class LoginRateLimiter {

    private static final int CAPACITY = 10;
    private static final Duration REFILL_PERIOD = Duration.ofMinutes(10);

    // TODO: Add scheduled eviction for stale IPs in production deployments with many users
    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Resolves or creates a rate limit bucket for the given IP address.
     *
     * @param ipAddress the client IP address
     * @return the bucket for this IP
     */
    public Bucket resolveBucket(String ipAddress) {
        return buckets.computeIfAbsent(ipAddress, ip -> createBucket());
    }

    /**
     * Resets the rate limit bucket for the given IP address.
     * Called on successful authentication to allow the user to proceed.
     *
     * @param ipAddress the client IP address
     */
    public void resetBucket(String ipAddress) {
        buckets.remove(ipAddress);
    }

    private Bucket createBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(CAPACITY)
                .refillGreedy(CAPACITY, REFILL_PERIOD)
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
