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

    public record ImportRowError(int row, String field, String message) {
    }

    public record ImportResult(int imported, List<UUID> questionIds) {
    }
}
