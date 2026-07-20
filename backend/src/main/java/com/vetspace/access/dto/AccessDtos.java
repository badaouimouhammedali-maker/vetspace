package com.vetspace.access.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AccessDtos {

    private AccessDtos() {
    }

    public record GenerateCodesRequest(
        @NotNull UUID packId,
        @NotNull @Min(1) @Max(500) Integer count,
        @Min(1) Integer maxUses
    ) {
    }

    /** The ONLY response ever carrying plaintext codes; csvToken buys one CSV download of the same batch. */
    public record GenerateCodesResponse(UUID packId, int count, List<String> codes, String csvToken) {
    }

    public enum CodeStatus {
        ACTIVE, EXHAUSTED, REVOKED, EXPIRED
    }

    public record CodeDto(UUID id, UUID packId, String packName, int maxUses, int usedCount,
                           boolean revoked, CodeStatus status, Instant createdAt) {
    }

    /**
     * A generation run and what became of it. {@code downloaded} false means the plaintext
     * CSV was never fetched, so those codes exist only as hashes and cannot be sold —
     * revoke the batch and generate a new one.
     */
    public record CodeBatchDto(UUID id, UUID packId, String packName, int codeCount, int maxUses,
                                Instant generatedAt, boolean downloaded, Instant downloadedAt,
                                long activeCount, long usedCount, long revokedCount) {
    }

    public record RevokeBatchResponse(UUID batchId, int revokedCount) {
    }

    public record RedeemRequest(@NotBlank String code) {
    }

    public record RedeemResponse(UUID subscriptionId, String packName, Instant startsAt, Instant endsAt) {
    }

    public record SubscriptionAuditDto(UUID id, String userEmail, String username, UUID packId, String packName,
                                        Instant startsAt, Instant endsAt, UUID activationCodeId) {
    }

    public record PublicPackDto(UUID id, UUID schoolId, Integer studyYear, String name, String academicYear,
                                 Integer priceDa, Instant expiresAt) {
    }
}
