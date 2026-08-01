package com.vetspace.admin;

import com.vetspace.admin.dto.ModuleDtos.PublishRequest;
import com.vetspace.admin.dto.QuestionDtos.ImportDryRunResult;
import com.vetspace.admin.dto.QuestionDtos.ImportResult;
import com.vetspace.admin.dto.QuestionDtos.QuestionAdminDto;
import com.vetspace.admin.dto.QuestionDtos.QuestionImportRow;
import com.vetspace.admin.dto.QuestionDtos.QuestionRequest;
import com.vetspace.domain.content.Difficulty;
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
@RequestMapping("/api/admin/questions")
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
public class QuestionAdminController {

    private final QuestionAdminService service;

    public QuestionAdminController(QuestionAdminService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionAdminDto create(@Valid @RequestBody QuestionRequest request) {
        return service.create(request);
    }

    @GetMapping
    public PageResponse<QuestionAdminDto> list(
        @RequestParam(required = false) UUID courseId,
        @RequestParam(required = false) UUID moduleId,
        @RequestParam(required = false) UUID sourceExamId,
        @RequestParam(required = false) Difficulty difficulty,
        @RequestParam(required = false) Boolean published,
        @RequestParam(required = false) String q,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.list(courseId, moduleId, sourceExamId, difficulty, published, q,
            Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @GetMapping("/{id}")
    public QuestionAdminDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public QuestionAdminDto update(@PathVariable UUID id, @Valid @RequestBody QuestionRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PatchMapping("/{id}/publish")
    public QuestionAdminDto publish(@PathVariable UUID id, @Valid @RequestBody PublishRequest request) {
        return service.setPublished(id, request.published());
    }

    /** Rows are validated together; any failure returns 400 with per-row errors and persists nothing. */
    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportResult importQuestions(@RequestBody List<QuestionImportRow> rows) {
        return service.importQuestions(rows);
    }

    /**
     * Same body, same validation, nothing written: reports what each row resolved to plus
     * every error at once.
     *
     * <p>A separate path rather than {@code /import?dryRun=true} so that "this call cannot
     * write" is visible in the URL — in an access log, in a proxy rule, in a screenshot of
     * the network tab — and so the two responses can be different types instead of one
     * union the client has to narrow.
     */
    @PostMapping("/import/dry-run")
    public ImportDryRunResult dryRunImport(@RequestBody List<QuestionImportRow> rows) {
        return service.dryRun(rows);
    }
}
