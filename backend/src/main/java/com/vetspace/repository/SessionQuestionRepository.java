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

    /**
     * Per-course coverage for one student: how much of each course they have actually
     * worked through, across every session they have ever run.
     *
     * <p>Everything is counted per DISTINCT question, not per session row. A question
     * answered in four sessions is one question seen, and it counts as correct if it was
     * right in <em>any</em> of them — "have I got this one yet?", which is the question a
     * revision screen is answering. Per-attempt accuracy already exists per session at
     * {@code statsByCourse}; the two numbers are different on purpose.
     *
     * <p>Only currently-visible questions are counted, matching
     * {@link com.vetspace.admin.QuestionSpecifications#visibleToStudent()}. Without that,
     * a question the student saw before it was unpublished would still count as seen while
     * no longer counting towards the total, and the progress bar would read over 100%.
     *
     * <p>Courses the student has never touched are simply absent from the result; the
     * service pairs this with the catalogue totals and fills them in as zeroes.
     */
    @Query("""
        select c.id as courseId,
               count(distinct q.id) as seenQuestions,
               count(distinct case when sq.state = com.vetspace.domain.session.QuestionState.ANSWERED
                                   then q.id else null end) as answeredQuestions,
               count(distinct case when sq.isCorrect = true then q.id else null end) as correctQuestions
        from SessionQuestion sq
          join sq.question q
          join q.course c
          join c.module m
        where sq.session.user.id = :userId
          and m.studyYear = :studyYear
          and m.published = true
          and c.published = true
          and q.published = true
        group by c.id
        """)
    List<CourseCoverageRow> coverageByCourse(@Param("userId") UUID userId,
                                              @Param("studyYear") Integer studyYear);

    interface CourseCoverageRow {
        UUID getCourseId();

        long getSeenQuestions();

        long getAnsweredQuestions();

        long getCorrectQuestions();
    }

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
