package com.vetspace.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class ModuleDtos {

    private ModuleDtos() {
    }

    public record ModuleRequest(
        @NotNull UUID schoolId,
        @NotNull @Min(1) @Max(10) Integer studyYear,
        @NotBlank @Size(max = 255) String name,
        boolean published
    ) {
    }

    public record ModuleDto(UUID id, UUID schoolId, Integer studyYear, String name, Integer position, boolean published) {
    }

    public record PublishRequest(@NotNull Boolean published) {
    }

    public record ReorderRequest(@NotEmpty List<UUID> orderedIds) {
    }
}
