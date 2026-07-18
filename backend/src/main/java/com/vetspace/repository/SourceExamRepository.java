package com.vetspace.repository;

import com.vetspace.domain.content.SourceExam;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceExamRepository extends JpaRepository<SourceExam, UUID> {

    long countBySchoolId(UUID schoolId);

    List<SourceExam> findBySchoolIdOrderByYearDesc(UUID schoolId);
}
