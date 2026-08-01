package com.vetspace.admin.dto;

import com.vetspace.domain.content.Difficulty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class QuestionDtos {

    private QuestionDtos() {
    }

    public record PropositionRequest(
        @NotBlank @Pattern(regexp = "[A-E]") String letter,
        @NotBlank String text,
        @NotNull Boolean isTrue,
        String explanationHtml,
        List<String> explanationImages
    ) {
    }

    public record QuestionRequest(
        @NotNull UUID courseId,
        @NotBlank String statement,
        List<String> statementImages,
        UUID sourceExamId,
        Difficulty difficulty,
        boolean published,
        @NotNull @Valid @Size(min = 2, max = 5) List<PropositionRequest> propositions
    ) {
    }

    /** Full admin view — includes truth values and explanations; never served to students. */
    public record PropositionAdminDto(UUID id, String letter, String text, boolean isTrue,
                                       String explanationHtml, List<String> explanationImages, Integer position) {
    }

    public record QuestionAdminDto(UUID id, UUID courseId, String statement, List<String> statementImages,
                                    UUID sourceExamId, Difficulty difficulty, boolean published,
                                    Instant createdAt, Instant updatedAt, List<PropositionAdminDto> propositions) {
    }

    /**
     * One row of a bulk import.
     *
     * <p>Separate from {@link QuestionRequest} rather than loosening it: the single-question
     * CRUD endpoints must keep `@NotNull courseId`, and an import row deliberately cannot —
     * its target may be named instead. Rows are resolved into a {@code QuestionRequest} and
     * then go through exactly the same validation and assembly as a hand-authored question,
     * so the two paths cannot drift.
     *
     * <p>Identity is given one way or the other, never both:
     * <ul>
     *   <li>{@code courseId}, or {@code courseName} (+ optional {@code moduleName} /
     *       {@code studyYear} to disambiguate)</li>
     *   <li>{@code sourceExamId}, or {@code sourceExamLabel}</li>
     * </ul>
     * Supplying both forms for the same field is an error, not a precedence rule: silently
     * preferring the id would let a wrong-but-plausible name sit in a file for months while
     * questions land somewhere else entirely.
     */
    public record QuestionImportRow(
        UUID courseId,
        String courseName,
        String moduleName,
        Integer studyYear,
        @NotBlank String statement,
        List<String> statementImages,
        UUID sourceExamId,
        String sourceExamLabel,
        Difficulty difficulty,
        boolean published,
        @Valid @Size(min = 2, max = 5) List<PropositionRequest> propositions
    ) {
    }

    public record ImportRowError(int row, String field, String message) {
    }

    public record ImportResult(int imported, List<UUID> questionIds) {
    }

    /**
     * What one row resolved to, for the dry run. The point is that an author can read back
     * the *names* their file actually pointed at — "row 3 → Parvovirose / Maladies
     * infectieuses / year 3" — which is the only way to catch a file that is perfectly
     * valid and aimed at the wrong course.
     */
    public record ImportRowPreview(
        int row,
        String statement,
        UUID courseId,
        String courseName,
        String moduleName,
        Integer studyYear,
        UUID sourceExamId,
        String sourceExamLabel,
        Difficulty difficulty,
        boolean published,
        int propositionCount
    ) {
    }

    /** Dry-run outcome: what would have been written, plus every error, having written nothing. */
    public record ImportDryRunResult(
        int rowsSubmitted,
        int wouldImport,
        List<ImportRowPreview> resolved,
        List<ImportRowError> errors
    ) {
    }
}
