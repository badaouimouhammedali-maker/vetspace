package com.vetspace.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class PackDtos {

    private PackDtos() {
    }

    public record PackRequest(
        @Min(1) @Max(10) Integer studyYear,
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 20) String academicYear,
        @NotNull @PositiveOrZero Integer priceDa,
        boolean active,
        @NotNull Instant expiresAt
    ) {
    }

    public record PackDto(UUID id, Integer studyYear, String name, String academicYear,
                           Integer priceDa, boolean active, Instant expiresAt) {
    }
}
