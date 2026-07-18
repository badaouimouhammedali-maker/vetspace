package com.vetspace.extras;

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

/** Per-user daily quotas for abuse-prone writes (signals 10/day, support 5/day). In-memory, like the other limiters. */
@Component
public class DailyUserRateLimiter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public void check(String scope, UUID userId, int perDay) {
        Bucket bucket = buckets.computeIfAbsent(scope + ":" + userId,
            k -> Bucket.builder()
                .addLimit(Bandwidth.classic(perDay, Refill.intervally(perDay, Duration.ofDays(1))))
                .build());
        if (!bucket.tryConsume(1)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");
        }
    }

    public void resetForTests() {
        buckets.clear();
    }
}
