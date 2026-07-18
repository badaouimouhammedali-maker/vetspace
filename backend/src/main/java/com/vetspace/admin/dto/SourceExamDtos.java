package com.vetspace.admin.dto;

import com.vetspace.domain.content.ExamType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class SourceExamDtos {

    private SourceExamDtos() {
    }

    public record SourceExamRequest(
        @NotNull UUID schoolId,
        @NotBlank @Size(max = 255) String label,
        @NotNull @Min(1900) @Max(2200) Integer year,
        @NotNull ExamType examType
    ) {
    }

    public record SourceExamDto(UUID id, UUID schoolId, String label, Integer year, ExamType examType) {
    }
}
