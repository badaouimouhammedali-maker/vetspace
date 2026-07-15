package com.vetspace.repository;

import com.vetspace.domain.session.SessionAnswer;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionAnswerRepository extends JpaRepository<SessionAnswer, UUID> {
}
