package com.vetspace.admin;

import com.vetspace.admin.dto.MindmapDtos.MindmapDto;
import com.vetspace.admin.dto.MindmapDtos.MindmapRequest;
import com.vetspace.admin.dto.ModuleDtos.PublishRequest;
import com.vetspace.web.PageResponse;
import com.vetspace.web.Paging;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/mindmaps")
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
public class MindmapAdminController {

    private final AdminCatalogService service;

    public MindmapAdminController(AdminCatalogService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MindmapDto create(@Valid @RequestBody MindmapRequest request) {
        return service.createMindmap(request);
    }

    @GetMapping
    public PageResponse<MindmapDto> list(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.listMindmaps(Paging.of(page, size, Sort.by("title"))));
    }

    @GetMapping("/{id}")
    public MindmapDto get(@PathVariable UUID id) {
        return service.getMindmap(id);
    }

    @PutMapping("/{id}")
    public MindmapDto update(@PathVariable UUID id, @Valid @RequestBody MindmapRequest request) {
        return service.updateMindmap(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.deleteMindmap(id);
    }

    @PatchMapping("/{id}/publish")
    public MindmapDto publish(@PathVariable UUID id, @Valid @RequestBody PublishRequest request) {
        return service.setMindmapPublished(id, request.published());
    }
}
