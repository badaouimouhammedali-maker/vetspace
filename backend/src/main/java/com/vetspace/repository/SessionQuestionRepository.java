package com.vetspace.repository;

import com.vetspace.domain.session.SessionQuestion;
import com.vetspace.domain.session.SessionQuestionId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionQuestionRepository extends JpaRepository<SessionQuestion, SessionQuestionId> {
}
