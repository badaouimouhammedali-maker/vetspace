package com.vetspace.repository;

import com.vetspace.domain.session.Session;
import com.vetspace.domain.session.SessionType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<Session, UUID> {

    Page<Session> findByUserId(UUID userId, Pageable pageable);

    List<Session> findByUserIdOrderByStartedAtDesc(UUID userId);

    List<Session> findByUserIdAndSessionTypeOrderByStartedAtDesc(UUID userId, SessionType sessionType);

    Optional<Session> findFirstByUserIdOrderByStartedAtDesc(UUID userId);

    /** Ownership-scoped lookup: a foreign session id behaves exactly like a nonexistent one. */
    Optional<Session> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);
}
