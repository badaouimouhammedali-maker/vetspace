package com.vetspace.repository;

import com.vetspace.domain.content.Proposition;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropositionRepository extends JpaRepository<Proposition, UUID> {

    List<Proposition> findByQuestionId(UUID questionId);

    List<Proposition> findByQuestionIdInOrderByPositionAsc(Collection<UUID> questionIds);

    void deleteByQuestionId(UUID questionId);
}
