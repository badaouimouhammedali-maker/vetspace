package com.vetspace.admin;

import com.vetspace.admin.dto.AdminDtos.AdminUserDto;
import com.vetspace.admin.dto.AdminDtos.UpdateUserStatusRequest;
import com.vetspace.web.PageResponse;
import com.vetspace.web.Paging;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
public class AdminUserController {

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AdminUserDto> search(@RequestParam(required = false) String query,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.search(query, Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    /** Enable/disable a student. ADMIN only; an admin cannot change their own status. */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserDto updateStatus(@AuthenticationPrincipal UUID adminId, @PathVariable UUID id,
                                     @Valid @RequestBody UpdateUserStatusRequest request) {
        return service.updateStatus(adminId, id, request.status());
    }
}
