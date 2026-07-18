package com.vetspace.admin;

import com.vetspace.admin.dto.CourseDtos.CourseDto;
import com.vetspace.admin.dto.CourseDtos.CourseRequest;
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
@RequestMapping("/api/admin/courses")
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
public class CourseAdminController {

    private final AdminCatalogService service;

    public CourseAdminController(AdminCatalogService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CourseDto create(@Valid @RequestBody CourseRequest request) {
        return service.createCourse(request);
    }

    @GetMapping
    public PageResponse<CourseDto> list(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.listCourses(Paging.of(page, size, Sort.by("position"))));
    }

    @GetMapping("/{id}")
    public CourseDto get(@PathVariable UUID id) {
        return service.getCourse(id);
    }

    @PutMapping("/{id}")
    public CourseDto update(@PathVariable UUID id, @Valid @RequestBody CourseRequest request) {
        return service.updateCourse(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.deleteCourse(id);
    }

    @PatchMapping("/{id}/publish")
    public CourseDto publish(@PathVariable UUID id, @Valid @RequestBody PublishRequest request) {
        return service.setCoursePublished(id, request.published());
    }

    @PatchMapping("/reorder")
    public List<CourseDto> reorder(@Valid @RequestBody ReorderRequest request) {
        return service.reorderCourses(request.orderedIds());
    }
}
