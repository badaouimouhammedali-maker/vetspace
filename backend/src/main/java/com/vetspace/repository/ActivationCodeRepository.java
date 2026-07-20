package com.vetspace.repository;

import com.vetspace.domain.access.ActivationCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivationCodeRepository extends JpaRepository<ActivationCode, UUID> {

    Optional<ActivationCode> findByCodeHash(String codeHash);

    Page<ActivationCode> findByPackId(UUID packId, Pageable pageable);

    List<ActivationCode> findByBatchId(UUID batchId);

    /**
     * The redemption race guard: the WHERE clause re-checks every validity condition inside the
     * UPDATE itself, so under concurrent redemption of a nearly-exhausted code exactly
     * (max_uses - used_count) callers get a row back and everyone else gets 0.
     */
    @Modifying
    @Query("""
        update ActivationCode c
        set c.usedCount = c.usedCount + 1
        where c.id = :id and c.revoked = false and c.usedCount < c.maxUses
        """)
    int tryConsume(@Param("id") UUID id);
}
