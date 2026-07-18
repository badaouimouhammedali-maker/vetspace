package com.vetspace.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class CourseDtos {

    private CourseDtos() {
    }

    public record CourseRequest(
        @NotNull UUID moduleId,
        @NotBlank @Size(max = 255) String name,
        boolean published,
        boolean freePreview
    ) {
    }

    public record CourseDto(UUID id, UUID moduleId, String name, Integer position, boolean published, boolean freePreview) {
    }
}
