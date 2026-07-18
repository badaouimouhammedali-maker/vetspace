package com.vetspace.repository;

import com.vetspace.domain.session.SessionQuestion;
import com.vetspace.domain.session.SessionQuestionId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionQuestionRepository extends JpaRepository<SessionQuestion, SessionQuestionId> {

    @Query("select sq from SessionQuestion sq where sq.id.sessionId = :sessionId order by sq.position asc")
    List<SessionQuestion> findBySessionIdOrderByPosition(@Param("sessionId") UUID sessionId);

    /** Per-session progress rollup for list/stats endpoints (avoids N+1 over the page). */
    @Query("""
        select sq.id.sessionId as sessionId,
               sum(case when sq.state = com.vetspace.domain.session.QuestionState.ANSWERED then 1 else 0 end) as answered,
               sum(case when sq.isCorrect = true then 1 else 0 end) as correct,
               sum(case when sq.state = com.vetspace.domain.session.QuestionState.CONSULTED then 1 else 0 end) as consulted,
               count(sq) as total
        from SessionQuestion sq
        where sq.id.sessionId in :sessionIds
        group by sq.id.sessionId
        """)
    List<SessionProgress> progressFor(@Param("sessionIds") Collection<UUID> sessionIds);

    /** Same metrics for one session, grouped by the questions' course. */
    @Query("""
        select sq.question.course.id as courseId,
               sq.question.course.name as courseName,
               count(sq) as total,
               sum(case when sq.state = com.vetspace.domain.session.QuestionState.ANSWERED then 1 else 0 end) as answered,
               sum(case when sq.isCorrect = true then 1 else 0 end) as correct,
               sum(case when sq.state = com.vetspace.domain.session.QuestionState.CONSULTED then 1 else 0 end) as consulted,
               sum(sq.secondsSpent) as seconds
        from SessionQuestion sq
        where sq.id.sessionId = :sessionId
        group by sq.question.course.id, sq.question.course.name
        order by sq.question.course.name
        """)
    List<CourseStatsRow> statsByCourse(@Param("sessionId") UUID sessionId);

    /** Raw interactions of the last N days; day-bucketing happens in Java (timezone-controlled). */
    @Query("""
        select sq.answeredAt as answeredAt, sq.state as state, sq.isCorrect as isCorrect
        from SessionQuestion sq
        where sq.session.user.id = :userId and sq.answeredAt >= :from
        """)
    List<WeeklyRow> interactionsSince(@Param("userId") UUID userId, @Param("from") Instant from);

    /** Every question id the user has ever had in a session — the onlyUnseen filter's exclusion set. */
    @Query("select distinct sq.id.questionId from SessionQuestion sq where sq.session.user.id = :userId")
    List<UUID> questionIdsSeenByUser(@Param("userId") UUID userId);

    interface SessionProgress {
        UUID getSessionId();

        long getAnswered();

        long getCorrect();

        long getConsulted();

        long getTotal();
    }

    interface CourseStatsRow {
        UUID getCourseId();

        String getCourseName();

        long getTotal();

        long getAnswered();

        long getCorrect();

        long getConsulted();

        long getSeconds();
    }

    interface WeeklyRow {
        Instant getAnsweredAt();

        com.vetspace.domain.session.QuestionState getState();

        Boolean getIsCorrect();
    }
}
