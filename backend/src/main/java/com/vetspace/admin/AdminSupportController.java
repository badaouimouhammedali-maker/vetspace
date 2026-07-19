package com.vetspace.admin;

import com.vetspace.admin.dto.AdminDtos.SupportMessageDto;
import com.vetspace.web.PageResponse;
import com.vetspace.web.Paging;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/support")
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
public class AdminSupportController {

    private final AdminSupportService service;

    public AdminSupportController(AdminSupportService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<SupportMessageDto> list(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.list(Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }
}
