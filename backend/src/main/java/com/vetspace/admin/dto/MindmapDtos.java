package com.vetspace.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class MindmapDtos {

    private MindmapDtos() {
    }

    public record MindmapRequest(
        @NotNull UUID courseId,
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 500) String imageUrl,
        boolean published
    ) {
    }

    public record MindmapDto(UUID id, UUID courseId, String title, String imageUrl, boolean published,
                              Instant createdAt, Instant updatedAt) {
    }
}
