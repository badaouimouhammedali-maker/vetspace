package com.vetspace.config;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Warns loudly when the platform looks like it is running more than one replica.
 *
 * <p>Every rate limiter in the app — login attempts by IP, code redemption, signals,
 * support, verification resends — keeps its counters in memory, as does the public-stats
 * cache and the one-time activation-code batch store. On a single instance that is fine
 * and deliberate. On two, each limit silently doubles, and a code batch generated on one
 * instance cannot be downloaded from the other, which loses the plaintext codes for good.
 *
 * <p>This does not refuse to start: the app still works, it just enforces weaker limits
 * than intended, and an unplanned outage would be worse than a degraded control. But the
 * operator has to be told, because nothing else would reveal it.
 */
@Component
public class SingleInstanceWarning {

    private static final Logger log = LoggerFactory.getLogger(SingleInstanceWarning.class);

    /** Replica-count variables used by the platforms this app is documented for. */
    private static final List<String> REPLICA_COUNT_VARS = List.of(
        "RAILWAY_REPLICA_COUNT", "WEB_CONCURRENCY", "REPLICAS", "NUM_REPLICAS");

    private final Environment environment;

    public SingleInstanceWarning(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void checkReplicaCount() {
        for (String var : REPLICA_COUNT_VARS) {
            Optional<Integer> count = readPositiveInt(var);
            if (count.isPresent() && count.get() > 1) {
                log.warn("""

                    *** {}={} suggests more than one instance ***
                      Rate limiting, the stats cache and the one-time activation-code batch
                      store are all in-memory and per-instance. With {} replicas every rate
                      limit is effectively multiplied by {}, and a generated code batch can
                      only be downloaded from the instance that created it — miss it and the
                      plaintext codes are unrecoverable.
                      Run exactly ONE instance until these move to a shared store (Redis or
                      the database). See docs/deploy.md.
                    """, var, count.get(), count.get(), count.get());
                return;
            }
        }
    }

    private Optional<Integer> readPositiveInt(String name) {
        String raw = environment.getProperty(name);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            // A non-numeric value is not something to fail or complain about.
            return Optional.empty();
        }
    }
}
