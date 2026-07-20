package com.vetspace.domain.access;

import com.vetspace.domain.common.BaseEntity;
import com.vetspace.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One code-generation run. Exists so a batch whose plaintext was never downloaded can be
 * identified and revoked wholesale — the codes themselves are only ever stored hashed.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "code_batches")
public class CodeBatch extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pack_id", nullable = false)
    private Pack pack;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "code_count", nullable = false)
    private int codeCount;

    @Column(name = "max_uses", nullable = false)
    private int maxUses;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    /** Null means the one-shot CSV was never fetched — the plaintext is gone. */
    @Column(name = "downloaded_at")
    private Instant downloadedAt;
}
