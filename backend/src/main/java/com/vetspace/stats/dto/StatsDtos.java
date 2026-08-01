package com.vetspace.stats.dto;

import com.vetspace.domain.session.SessionStatus;
import com.vetspace.domain.session.SessionType;
import com.vetspace.user.dto.ActiveSubscriptionDto;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class StatsDtos {

    private StatsDtos() {
    }

    /** Per-session metrics; "juste"/"fausse"/"consulte" mirror the reference UI's terms. */
    public record SessionStatsDto(
        UUID id,
        String title,
        SessionType sessionType,
        SessionStatus status,
        Instant startedAt,
        int totalSeconds,
        double avgSecondsPerQuestion,
        long totalQuestions,
        long juste,
        long fausse,
        long consulte,
        double precisionPercent
    ) {
    }

    public record CourseStatsDto(
        UUID courseId,
        String courseName,
        long totalQuestions,
        long juste,
        long fausse,
        long consulte,
        long totalSeconds,
        double avgSecondsPerQuestion,
        double precisionPercent
    ) {
    }

    public record DailyStatsDto(LocalDate date, long juste, long fausse, long consultees) {
    }

    /**
     * One course's lifetime coverage for one student, counted per distinct question
     * across every session they have run.
     *
     * @param totalQuestions   published questions in the course — the denominator of the
     *                         progress bar, and 0 for a course with no content yet
     * @param seenQuestions    distinct questions that have appeared in one of their
     *                         sessions, whatever they did with it afterwards. Matches the
     *                         builder's "only unseen" filter, so the two screens agree
     * @param answeredQuestions distinct questions actually answered (a consulted-only
     *                         question is seen but not answered) — the precision denominator
     * @param correctQuestions distinct questions answered correctly at least once
     * @param neverSeenQuestions {@code totalQuestions - seenQuestions}, precomputed so the
     *                         client cannot get the subtraction wrong
     * @param precisionPercent {@code correctQuestions / answeredQuestions}, 0 when nothing
     *                         has been answered. Per question, NOT per attempt: this asks
     *                         "how much of this course do I know" rather than "how well did
     *                         I do that day", which is what per-session stats already answer
     */
    public record CourseCoverageDto(
        UUID courseId,
        String courseName,
        long totalQuestions,
        long seenQuestions,
        long answeredQuestions,
        long correctQuestions,
        long neverSeenQuestions,
        double precisionPercent
    ) {
    }

    /** Coverage grouped under the module it belongs to, in catalogue order. */
    public record ModuleCoverageDto(
        UUID moduleId,
        String moduleName,
        long totalQuestions,
        long seenQuestions,
        List<CourseCoverageDto> courses
    ) {
    }

    public record BankTotalsDto(long questions, long sourceExams, long mindmaps) {
    }

    public record OverviewDto(
        BankTotalsDto bank,
        SessionStatsDto lastSession,
        List<ActiveSubscriptionDto> activeSubscriptions
    ) {
    }
}
