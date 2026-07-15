package com.vetspace.repository;

import com.vetspace.domain.access.ActivationCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivationCodeRepository extends JpaRepository<ActivationCode, UUID> {

    Optional<ActivationCode> findByCodeHash(String codeHash);
}
