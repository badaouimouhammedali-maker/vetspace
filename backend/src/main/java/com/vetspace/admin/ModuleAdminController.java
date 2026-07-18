package com.vetspace.admin;

import com.vetspace.admin.dto.ModuleDtos.ModuleDto;
import com.vetspace.admin.dto.ModuleDtos.ModuleRequest;
import com.vetspace.admin.dto.ModuleDtos.PublishRequest;
import com.vetspace.admin.dto.ModuleDtos.ReorderRequest;
import com.vetspace.web.PageResponse;
import com.vetspace.web.Paging;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/admin/modules")
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
public class ModuleAdminController {

    private final AdminCatalogService service;

    public ModuleAdminController(AdminCatalogService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ModuleDto create(@Valid @RequestBody ModuleRequest request) {
        return service.createModule(request);
    }

    @GetMapping
    public PageResponse<ModuleDto> list(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.listModules(Paging.of(page, size, Sort.by("studyYear", "position"))));
    }

    @GetMapping("/{id}")
    public ModuleDto get(@PathVariable UUID id) {
        return service.getModule(id);
    }

    @PutMapping("/{id}")
    public ModuleDto update(@PathVariable UUID id, @Valid @RequestBody ModuleRequest request) {
        return service.updateModule(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.deleteModule(id);
    }

    @PatchMapping("/{id}/publish")
    public ModuleDto publish(@PathVariable UUID id, @Valid @RequestBody PublishRequest request) {
        return service.setModulePublished(id, request.published());
    }

    @PatchMapping("/reorder")
    public List<ModuleDto> reorder(@Valid @RequestBody ReorderRequest request) {
        return service.reorderModules(request.orderedIds());
    }
}
