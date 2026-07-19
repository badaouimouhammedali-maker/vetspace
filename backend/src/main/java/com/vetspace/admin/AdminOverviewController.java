package com.vetspace.admin;

import com.vetspace.admin.dto.AdminDtos.OverviewDto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/overview")
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
public class AdminOverviewController {

    private final AdminOverviewService service;

    public AdminOverviewController(AdminOverviewService service) {
        this.service = service;
    }

    @GetMapping
    public OverviewDto overview() {
        return service.overview();
    }
}
