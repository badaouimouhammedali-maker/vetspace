package com.vetspace.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class SchoolDtos {

    private SchoolDtos() {
    }

    public record SchoolRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 255) String slug
    ) {
    }

    public record SchoolDto(UUID id, String name, String slug) {
    }
}
