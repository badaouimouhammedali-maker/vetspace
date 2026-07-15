package com.vetspace.repository;

import com.vetspace.domain.extras.QuestionLabel;
import com.vetspace.domain.extras.QuestionLabelId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionLabelRepository extends JpaRepository<QuestionLabel, QuestionLabelId> {
}
