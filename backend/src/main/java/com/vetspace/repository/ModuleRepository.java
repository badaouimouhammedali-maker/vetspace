package com.vetspace.repository;

import com.vetspace.domain.content.Module;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModuleRepository extends JpaRepository<Module, UUID> {
}
