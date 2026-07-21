package com.vetspace.session.dto;

import com.vetspace.catalog.dto.QuestionPlayDto;
import com.vetspace.domain.content.Difficulty;
import com.vetspace.domain.session.QuestionState;
import com.vetspace.domain.session.SessionStatus;
import com.vetspace.domain.session.SessionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SessionDtos {

    private SessionDtos() {
    }

    /** The filter snapshot stored in sessions.filters (jsonb) and replayed by repeat SAME_FILTERS. */
    public record SessionFilters(
        List<UUID> moduleIds,
        List<UUID> courseIds,
        List<UUID> sourceExamIds,
        Difficulty difficulty,
        Boolean onlyUnseen,
        List<UUID> labelIds
    ) {
    }

    public record CreateSessionRequest(
        String title,
        @NotNull SessionType sessionType,
        SessionFilters filters,
        @NotNull @Min(1) Integer questionCount
    ) {
    }

    public record SessionSummaryDto(
        UUID id,
        String title,
        SessionType sessionType,
        SessionStatus status,
        int questionCount,
        long answeredCount,
        long correctCount,
        double percentCorrectSoFar,
        int totalSeconds,
        boolean favorite,
        Integer rating,
        BigDecimal score,
        Instant startedAt,
        Instant submittedAt
    ) {
    }

    /** One playable question inside a session: play DTO only — no truth values, no explanations. */
    public record SessionQuestionPlayDto(
        QuestionPlayDto question,
        QuestionState state,
        Boolean isCorrect,
        int secondsSpent,
        List<UUID> selectedPropositionIds,
        int position
    ) {
    }

    /**
     * @param score final score, set on submit and {@code null} while the session is
     *              ACTIVE. It is here because the player renders its end screen from
     *              this payload whenever the session is SUBMITTED — on a refresh, or
     *              when the student opens a finished session from the list, there is no
     *              submit response to read the score from, and the screen showed "—"
     *              over work that had been scored and stored. Carrying no truth values
     *              or explanations, it does not widen what an ACTIVE session exposes.
     */
    public record SessionPlayDto(
        UUID id,
        String title,
        SessionType sessionType,
        SessionStatus status,
        int totalSeconds,
        BigDecimal score,
        List<SessionQuestionPlayDto> questions
    ) {
    }

    public record AnswerRequest(
        @NotNull List<UUID> selectedPropositionIds,
        @Min(0) int secondsSpent
    ) {
    }

    /** Correction for ONE question — the only student-facing shape that carries truth values and explanations. */
    public record PropositionCorrectionDto(UUID id, String letter, String text, boolean isTrue,
                                            String explanationHtml, List<String> explanationImages, Integer position) {
    }

    public record CorrectionDto(
        UUID questionId,
        QuestionState state,
        Boolean isCorrect,
        List<UUID> selectedPropositionIds,
        List<PropositionCorrectionDto> propositions
    ) {
    }

    /** Partial update: only non-null fields are applied. */
    public record UpdateSessionRequest(String title, Boolean favorite, @Min(1) @Max(5) Integer rating) {
    }

    public enum RepeatMode {
        ALL, WRONG_ONLY, UNANSWERED_ONLY, SAME_FILTERS
    }

    public record RepeatRequest(@NotNull RepeatMode mode) {
    }
}
