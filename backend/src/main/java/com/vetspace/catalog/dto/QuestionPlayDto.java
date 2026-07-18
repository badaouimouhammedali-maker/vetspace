package com.vetspace.catalog.dto;

import com.vetspace.domain.content.Difficulty;
import java.util.List;
import java.util.UUID;

/**
 * The ONLY question shape ever served to a student during an active session.
 * By design it has no field for proposition truth values or explanations —
 * correction data flows exclusively through the dedicated correction endpoint.
 */
public record QuestionPlayDto(
    UUID id,
    UUID courseId,
    String statement,
    List<String> statementImages,
    Difficulty difficulty,
    List<PropositionPlayDto> propositions
) {

    public record PropositionPlayDto(UUID id, String letter, String text, Integer position) {
    }
}
