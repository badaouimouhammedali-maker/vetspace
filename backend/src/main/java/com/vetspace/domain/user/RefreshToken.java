package com.vetspace.domain.user;

import com.vetspace.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    /** Shared by every token in one rotation lineage; reuse of a revoked token revokes the whole family. */
    @Column(name = "family_id", nullable = false, columnDefinition = "uuid")
    private UUID familyId;

    /** When this token was minted. One row per login plus one per rotation. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** Why {@link #revokedAt} was set. Null on rows revoked before V11 — treated conservatively. */
    @Enumerated(EnumType.STRING)
    @Column(name = "revoked_reason", length = 20)
    private RevocationReason revokedReason;

    /**
     * Coarse device description derived server-side from the User-Agent — "Chrome sur
     * Windows". Enough for a student to recognise their own device in the admin's list;
     * deliberately not a fingerprint.
     */
    @Column(name = "device_label", length = 120)
    private String deviceLabel;

    /** Touched on every rotation. "Least recently used" for eviction is ordered by this. */
    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    /**
     * Origin IP of the request that minted this token, as text — see V11 for why not
     * {@code inet}. Only ever counted distinct, never subnet-matched.
     */
    @Column(name = "created_ip", length = 45)
    private String createdIp;
}
