package com.vetspace.admin;

import com.vetspace.admin.dto.SourceExamDtos.SourceExamDto;
import com.vetspace.admin.dto.SourceExamDtos.SourceExamRequest;
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
@RequestMapping("/api/admin/source-exams")
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
public class SourceExamAdminController {

    private final AdminCatalogService service;

    public SourceExamAdminController(AdminCatalogService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SourceExamDto create(@Valid @RequestBody SourceExamRequest request) {
        return service.createSourceExam(request);
    }

    @GetMapping
    public PageResponse<SourceExamDto> list(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.listSourceExams(Paging.of(page, size, Sort.by(Sort.Direction.DESC, "year"))));
    }

    @GetMapping("/{id}")
    public SourceExamDto get(@PathVariable UUID id) {
        return service.getSourceExam(id);
    }

    @PutMapping("/{id}")
    public SourceExamDto update(@PathVariable UUID id, @Valid @RequestBody SourceExamRequest request) {
        return service.updateSourceExam(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.deleteSourceExam(id);
    }
}
