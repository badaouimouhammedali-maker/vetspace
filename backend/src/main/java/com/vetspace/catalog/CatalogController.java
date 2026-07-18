package com.vetspace.catalog;

import com.vetspace.admin.dto.CourseDtos.CourseDto;
import com.vetspace.admin.dto.ModuleDtos.ModuleDto;
import com.vetspace.admin.dto.SchoolDtos.SchoolDto;
import com.vetspace.domain.content.Difficulty;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogService catalogService;
    private final QuestionQueryService questionQueryService;

    public CatalogController(CatalogService catalogService, QuestionQueryService questionQueryService) {
        this.catalogService = catalogService;
        this.questionQueryService = questionQueryService;
    }

    /** Session-builder helper: how many questions match these filters (student sees published-only, own school). */
    @GetMapping("/questions/count")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Long> questionCount(@AuthenticationPrincipal UUID userId,
                                            @RequestParam(required = false) List<UUID> courseIds,
                                            @RequestParam(required = false) UUID moduleId,
                                            @RequestParam(required = false) UUID sourceExamId,
                                            @RequestParam(required = false) Difficulty difficulty,
                                            @RequestParam(required = false) List<UUID> labelIds) {
        return Map.of("count", questionQueryService.count(userId, courseIds, moduleId, sourceExamId, difficulty, labelIds));
    }

    /** Public: needed by the signup form. */
    @GetMapping("/schools")
    public List<SchoolDto> schools() {
        return catalogService.listSchools();
    }

    @GetMapping("/modules")
    @PreAuthorize("isAuthenticated()")
    public List<ModuleDto> modules(@AuthenticationPrincipal UUID userId, @RequestParam Integer studyYear) {
        return catalogService.listModules(userId, studyYear);
    }

    @GetMapping("/modules/{id}/courses")
    @PreAuthorize("isAuthenticated()")
    public List<CourseDto> courses(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return catalogService.listCourses(userId, id);
    }

    /** Session-builder helper: the caller's school's source exams. */
    @GetMapping("/source-exams")
    @PreAuthorize("isAuthenticated()")
    public List<com.vetspace.admin.dto.SourceExamDtos.SourceExamDto> sourceExams(
        @AuthenticationPrincipal UUID userId) {
        return catalogService.listSourceExams(userId);
    }

    @GetMapping("/mindmaps")
    @PreAuthorize("isAuthenticated()")
    public List<com.vetspace.admin.dto.MindmapDtos.MindmapDto> mindmaps(@AuthenticationPrincipal UUID userId,
                                                                          @RequestParam UUID courseId) {
        return catalogService.listMindmaps(userId, courseId);
    }

    @GetMapping("/mindmaps/{id}")
    @PreAuthorize("isAuthenticated()")
    public com.vetspace.admin.dto.MindmapDtos.MindmapDto mindmap(@AuthenticationPrincipal UUID userId,
                                                                   @PathVariable UUID id) {
        return catalogService.getMindmap(userId, id);
    }
}
