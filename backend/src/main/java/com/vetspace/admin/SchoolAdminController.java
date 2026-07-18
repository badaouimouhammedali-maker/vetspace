package com.vetspace.admin;

import com.vetspace.admin.dto.SchoolDtos.SchoolDto;
import com.vetspace.admin.dto.SchoolDtos.SchoolRequest;
import com.vetspace.web.PageResponse;
import com.vetspace.web.Paging;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/schools")
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
public class SchoolAdminController {

    private final AdminCatalogService service;

    public SchoolAdminController(AdminCatalogService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SchoolDto create(@Valid @RequestBody SchoolRequest request) {
        return service.createSchool(request);
    }

    @GetMapping
    public PageResponse<SchoolDto> list(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.listSchools(Paging.of(page, size, Sort.by("name"))));
    }

    @GetMapping("/{id}")
    public SchoolDto get(@PathVariable UUID id) {
        return service.getSchool(id);
    }

    @PutMapping("/{id}")
    public SchoolDto update(@PathVariable UUID id, @Valid @RequestBody SchoolRequest request) {
        return service.updateSchool(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.deleteSchool(id);
    }
}
