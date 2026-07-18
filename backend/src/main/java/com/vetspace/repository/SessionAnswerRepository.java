package com.vetspace.repository;

import com.vetspace.domain.session.SessionAnswer;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionAnswerRepository extends JpaRepository<SessionAnswer, UUID> {

    @Query("select sa from SessionAnswer sa where sa.sessionQuestion.id.sessionId = :sessionId")
    List<SessionAnswer> findBySessionId(@Param("sessionId") UUID sessionId);

    @Query("select sa from SessionAnswer sa where sa.sessionQuestion.id.sessionId = :sessionId and sa.sessionQuestion.id.questionId = :questionId")
    List<SessionAnswer> findBySessionIdAndQuestionId(@Param("sessionId") UUID sessionId, @Param("questionId") UUID questionId);

    @Modifying
    @Query("delete from SessionAnswer sa where sa.sessionQuestion.id.sessionId = :sessionId and sa.sessionQuestion.id.questionId = :questionId")
    void deleteBySessionIdAndQuestionId(@Param("sessionId") UUID sessionId, @Param("questionId") UUID questionId);

    @Modifying
    @Query("delete from SessionAnswer sa where sa.sessionQuestion.id.sessionId = :sessionId")
    void deleteBySessionId(@Param("sessionId") UUID sessionId);
}
