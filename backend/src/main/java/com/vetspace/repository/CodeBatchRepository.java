package com.vetspace.repository;

import com.vetspace.domain.access.CodeBatch;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodeBatchRepository extends JpaRepository<CodeBatch, UUID> {

    List<CodeBatch> findAllByOrderByGeneratedAtDesc();
}
