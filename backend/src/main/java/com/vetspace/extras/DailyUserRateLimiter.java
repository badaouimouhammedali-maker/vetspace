package com.vetspace.extras;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Per-user daily quotas for abuse-prone writes (signals 10/day, support 5/day). In-memory, like the other limiters. */
@Component
public class DailyUserRateLimiter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final boolean enabled;

    public DailyUserRateLimiter(@Value("${app.rate-limits-enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    public void check(String scope, UUID userId, int perDay) {
        check(scope, userId, perDay, Duration.ofDays(1));
    }

    /** Same quota mechanism over an arbitrary period — e.g. verification resends, 3 per hour. */
    public void check(String scope, UUID userId, int limit, Duration period) {
        if (!enabled) {
            return;
        }
        Bucket bucket = buckets.computeIfAbsent(scope + ":" + userId,
            k -> Bucket.builder()
                .addLimit(Bandwidth.classic(limit, Refill.intervally(limit, period)))
                .build());
        if (!bucket.tryConsume(1)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");
        }
    }

    public void resetForTests() {
        buckets.clear();
    }
}
