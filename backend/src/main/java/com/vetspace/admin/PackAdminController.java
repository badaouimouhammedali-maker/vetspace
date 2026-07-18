package com.vetspace.admin;

import com.vetspace.admin.dto.PackDtos.PackDto;
import com.vetspace.admin.dto.PackDtos.PackRequest;
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

/** Packs are commercial objects: ADMIN only, unlike the rest of the catalog which TEACHER can also manage. */
@RestController
@RequestMapping("/api/admin/packs")
@PreAuthorize("hasRole('ADMIN')")
public class PackAdminController {

    private final PackAdminService service;

    public PackAdminController(PackAdminService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PackDto create(@Valid @RequestBody PackRequest request) {
        return service.create(request);
    }

    @GetMapping
    public PageResponse<PackDto> list(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.list(Paging.of(page, size, Sort.by("name"))));
    }

    @GetMapping("/{id}")
    public PackDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public PackDto update(@PathVariable UUID id, @Valid @RequestBody PackRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
