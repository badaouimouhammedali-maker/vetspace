package com.vetspace.repository;

import com.vetspace.domain.extras.Signal;
import com.vetspace.domain.extras.SignalStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignalRepository extends JpaRepository<Signal, UUID> {

    List<Signal> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Page<Signal> findByStatus(SignalStatus status, Pageable pageable);
}
