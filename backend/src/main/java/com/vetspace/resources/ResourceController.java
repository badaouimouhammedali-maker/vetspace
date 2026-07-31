package com.vetspace.resources;

import com.vetspace.resources.dto.ResourceDtos.ModuleResourcesDto;
import com.vetspace.resources.dto.ResourceDtos.ResourceDto;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Student-facing study library. Authenticated, and deliberately NOT subscription-gated:
 * course material is the free half of the product.
 */
@RestController
@RequestMapping("/api/resources")
@PreAuthorize("isAuthenticated()")
public class ResourceController {

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    /** Years that have published resources — drives the year selector. */
    @GetMapping("/years")
    public List<Integer> years() {
        return resourceService.yearsWithResources();
    }

    @GetMapping
    public List<ModuleResourcesDto> list(@RequestParam(required = false) Integer studyYear,
                                          @RequestParam(required = false) UUID moduleId) {
        return resourceService.listForStudents(studyYear, moduleId);
    }

    @GetMapping("/{id}")
    public ResourceDto get(@AuthenticationPrincipal UUID callerId, @PathVariable UUID id) {
        return resourceService.getForCaller(callerId, id);
    }
}
