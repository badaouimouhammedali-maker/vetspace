package com.vetspace.repository;

import com.vetspace.domain.extras.QuestionLabel;
import com.vetspace.domain.extras.QuestionLabelId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionLabelRepository extends JpaRepository<QuestionLabel, QuestionLabelId> {

    @Query("select ql.id.questionId from QuestionLabel ql where ql.id.labelId = :labelId")
    List<UUID> questionIdsForLabel(@Param("labelId") UUID labelId);
}
