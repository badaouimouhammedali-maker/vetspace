package com.vetspace.access;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 5 redemption attempts per hour, per user AND per IP. Lives at the service layer (not the
 * servlet filter) because the per-user half needs the authenticated principal, which isn't
 * resolved yet when the global RateLimitingFilter runs.
 */
@Component
public class RedeemRateLimiter {

    private static final Bandwidth LIMIT = Bandwidth.classic(5, Refill.intervally(5, Duration.ofHours(1)));

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /** Checks both buckets; the attempt counts against both regardless of whether the code turns out valid. */
    public void check(UUID userId, String clientIp) {
        if (!tryConsume("user:" + userId) | !tryConsume("ip:" + clientIp)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");
        }
    }

    private boolean tryConsume(String key) {
        return buckets.computeIfAbsent(key, k -> Bucket.builder().addLimit(LIMIT).build()).tryConsume(1);
    }

    public void resetForTests() {
        buckets.clear();
    }
}
