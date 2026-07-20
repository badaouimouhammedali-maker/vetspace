package com.vetspace.access;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Holds freshly generated plaintext codes in memory just long enough for the ONE CSV download
 * the generation response points to. Codes are never persisted in plaintext and never logged;
 * a batch disappears on first download or after the TTL, whichever comes first.
 */
@Component
public class CodeBatchStore {

    static final long TTL_SECONDS = 15 * 60;

    private record Batch(List<String> codes, String packName, UUID batchId, Instant expiresAt) {
    }

    private final Map<String, Batch> batches = new ConcurrentHashMap<>();

    public String store(List<String> codes, String packName, UUID batchId) {
        String token = UUID.randomUUID().toString();
        batches.put(token, new Batch(List.copyOf(codes), packName, batchId,
            Instant.now().plusSeconds(TTL_SECONDS)));
        return token;
    }

    /** The batch this token belongs to, so a successful download can be recorded. */
    public UUID batchIdFor(String token) {
        Batch batch = batches.get(token);
        return batch == null ? null : batch.batchId();
    }

    /** Single use: the batch is removed atomically on retrieval. Returns null if unknown, already downloaded, or expired. */
    public List<String> consume(String token) {
        Batch batch = batches.remove(token);
        if (batch == null || batch.expiresAt().isBefore(Instant.now())) {
            return null;
        }
        return batch.codes();
    }
}
