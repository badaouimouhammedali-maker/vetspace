package com.vetspace.resources;

import com.vetspace.resources.dto.ResourceDtos.ModuleResourceSummaryDto;
import com.vetspace.resources.dto.ResourceDtos.PublishRequest;
import com.vetspace.resources.dto.ResourceDtos.ReorderRequest;
import com.vetspace.resources.dto.ResourceDtos.ResourceCreateRequest;
import com.vetspace.resources.dto.ResourceDtos.ResourceDto;
import com.vetspace.resources.dto.ResourceDtos.ResourceUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/resources")
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
public class ResourceAdminController {

    private final ResourceService resourceService;

    public ResourceAdminController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    /**
     * Multipart: the file plus a JSON {@code metadata} part. The file's type is decided
     * by its magic bytes inside the service — the filename and the browser's declared
     * content type are both ignored.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResourceDto upload(@AuthenticationPrincipal UUID uploaderId,
                              @RequestPart("file") MultipartFile file,
                              @Valid @RequestPart("metadata") ResourceCreateRequest metadata) {
        return resourceService.create(uploaderId, metadata, file);
    }

    @GetMapping
    public List<ResourceDto> list(@RequestParam UUID moduleId) {
        return resourceService.listForAdmin(moduleId);
    }

    /** Per-module counts and byte totals — R2 usage, visible where the uploading happens. */
    @GetMapping("/summary")
    public List<ModuleResourceSummaryDto> summary() {
        return resourceService.summaries();
    }

    @PutMapping("/{id}")
    public ResourceDto update(@PathVariable UUID id, @Valid @RequestBody ResourceUpdateRequest request) {
        return resourceService.update(id, request);
    }

    @PatchMapping("/{id}/publish")
    public ResourceDto publish(@PathVariable UUID id, @Valid @RequestBody PublishRequest request) {
        return resourceService.setPublished(id, request.published());
    }

    @PostMapping("/reorder")
    public List<ResourceDto> reorder(@Valid @RequestBody ReorderRequest request) {
        return resourceService.reorder(request.orderedIds());
    }

    /** Removes the row and the stored object. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        resourceService.delete(id);
    }
}
