package com.vetspace.repository;

import com.vetspace.domain.extras.Signal;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignalRepository extends JpaRepository<Signal, UUID> {
}
