package com.vetspace.repository;

import com.vetspace.domain.content.SourceExam;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceExamRepository extends JpaRepository<SourceExam, UUID> {

    List<SourceExam> findAllByOrderByYearDesc();

    /**
     * Label-based lookup for the bulk import. A list, not an Optional: nothing constrains
     * `label` to be unique, so two exams can legitimately share one and the caller must
     * report the ambiguity instead of picking one.
     */
    List<SourceExam> findByLabelIgnoreCaseOrderByYearDesc(String label);
}
