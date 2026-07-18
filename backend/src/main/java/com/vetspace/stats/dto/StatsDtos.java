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

    public record BankTotalsDto(long questions, long sourceExams, long mindmaps) {
    }

    public record OverviewDto(
        BankTotalsDto bank,
        SessionStatsDto lastSession,
        List<ActiveSubscriptionDto> activeSubscriptions
    ) {
    }
}
