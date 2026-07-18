package com.vetspace.repository;

import com.vetspace.domain.access.Pack;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PackRepository extends JpaRepository<Pack, UUID>, JpaSpecificationExecutor<Pack> {
}
