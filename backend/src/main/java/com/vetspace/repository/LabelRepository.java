package com.vetspace.repository;

import com.vetspace.domain.extras.Label;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabelRepository extends JpaRepository<Label, UUID> {

    List<Label> findByUserIdOrderByNameAsc(UUID userId);

    /** Ownership-scoped lookup: a foreign label id behaves exactly like a nonexistent one. */
    Optional<Label> findByIdAndUserId(UUID id, UUID userId);

    List<Label> findByUserId(UUID userId);
}
