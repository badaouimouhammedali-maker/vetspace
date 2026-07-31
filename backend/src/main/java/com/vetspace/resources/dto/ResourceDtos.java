package com.vetspace.resources.dto;

import com.vetspace.domain.content.FileType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ResourceDtos {

    private ResourceDtos() {
    }

    /** Metadata sent alongside the multipart file on upload. */
    public record ResourceCreateRequest(
        @NotNull UUID moduleId,
        @NotBlank @Size(max = 255) String title,
        @Size(max = 2000) String description,
        boolean published
    ) {
    }

    /** Edits that do not replace the file. Swapping the file means delete + re-upload. */
    public record ResourceUpdateRequest(
        @NotNull UUID moduleId,
        @NotBlank @Size(max = 255) String title,
        @Size(max = 2000) String description,
        boolean published
    ) {
    }

    public record ResourceDto(UUID id, UUID moduleId, String moduleName, Integer studyYear,
                               String title, String description, String fileUrl, FileType fileType,
                               long fileSizeBytes, Integer position, boolean published,
                               Instant createdAt, Instant updatedAt) {
    }

    /** Student view: resources grouped under the module they belong to. */
    public record ModuleResourcesDto(UUID moduleId, String moduleName, Integer studyYear,
                                      Integer modulePosition, List<ResourceDto> resources) {
    }

    /** Admin view: adds the storage total so R2 usage is visible where uploads happen. */
    public record ModuleResourceSummaryDto(UUID moduleId, String moduleName, Integer studyYear,
                                            int resourceCount, long totalBytes) {
    }

    public record PublishRequest(@NotNull Boolean published) {
    }

    public record ReorderRequest(@NotEmpty List<UUID> orderedIds) {
    }
}
